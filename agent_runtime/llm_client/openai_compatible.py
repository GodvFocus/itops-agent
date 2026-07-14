"""OpenAI 兼容协议的真实 LLM 客户端。

DeepSeek、Qwen（通义千问）、GLM（智谱）均提供 OpenAI 兼容的 Chat Completions API，
因此用单一客户端实现即可覆盖三个供应商，只需切换 base_url、api_key 和 model。

设计要点：
- 每个节点有独立的系统提示词，约束输出为 JSON。
- 对模型输出做 Pydantic schema 校验，失败时重试。
- 连续重试仍失败时降级到 MockLLMClient，保证链路不中断。
- 缺槽位追问的逻辑判断（shouldAskUser / nextStep）由代码决定，LLM 只负责生成自然语言文案。
"""

from __future__ import annotations

import json
import logging
from typing import Any

from agent_runtime.llm_client.base import LLMRequest
from agent_runtime.llm_client.prompts import (
    CLASSIFY_INTENT_SYSTEM,
    EXTRACT_SLOTS_SYSTEM,
    build_question_user_prompt,
)
from agent_runtime.llm_client.slot_utils import SLOT_LABELS, build_text, resolve_missing

logger = logging.getLogger(__name__)

_MAX_RETRIES = 2


class OpenAICompatibleLLMClient:
    """通过 OpenAI 兼容协议调用真实大模型的 LLM 客户端。

    支持 DeepSeek、Qwen、GLM 等供应商，只需配置不同的 base_url / api_key / model。
    """

    def __init__(
        self,
        api_key: str,
        base_url: str,
        model: str,
        provider: str = "openai-compatible",
    ):
        if not api_key:
            raise ValueError("OpenAICompatibleLLMClient 需要 api_key，请在 .env 中配置 ITOPS_CHAT_API_KEY")
        if not base_url:
            raise ValueError("OpenAICompatibleLLMClient 需要 base_url，请在 .env 中配置 ITOPS_CHAT_ENDPOINT")

        self.api_key = api_key
        self.base_url = base_url
        self.model = model
        self.provider = provider
        self._client = self._create_client()
        # 延迟初始化 mock 降级客户端，避免未使用时引入不必要的依赖
        self._mock_fallback: Any = None

    def _create_client(self):
        """创建 OpenAI SDK 客户端实例。"""
        from openai import OpenAI

        return OpenAI(api_key=self.api_key, base_url=self.base_url)

    @property
    def mock_fallback(self) -> Any:
        """获取 mock 降级客户端（懒加载）。"""
        if self._mock_fallback is None:
            from agent_runtime.llm_client.mock import MockLLMClient

            self._mock_fallback = MockLLMClient()
        return self._mock_fallback

    # ------------------------------------------------------------------
    # 对外接口
    # ------------------------------------------------------------------

    def invoke(self, request: LLMRequest) -> dict[str, Any]:
        """根据节点名称分发到对应的处理逻辑。"""
        handler = {
            "classify_intent": self._classify_intent,
            "extract_slots": self._extract_slots,
            "generate_question": self._generate_question,
        }.get(request.node_name)

        if handler is None:
            raise ValueError(f"Unsupported node: {request.node_name}")

        return handler(request.context)

    # ------------------------------------------------------------------
    # classify_intent
    # ------------------------------------------------------------------

    def _classify_intent(self, context: dict[str, Any]) -> dict[str, Any]:
        user_prompt = f"工单内容：\n{build_text(context)}"
        for attempt in range(_MAX_RETRIES + 1):
            try:
                content = self._chat_json(CLASSIFY_INTENT_SYSTEM, user_prompt)
                payload = json.loads(content)
                intent = payload.get("intent", "UNKNOWN")
                if intent not in ("ACCOUNT_LOGIN_ISSUE", "VPN_CONNECTION_ISSUE", "PERMISSION_REQUEST", "UNKNOWN"):
                    raise ValueError(f"模型返回了非法意图: {intent}")
                return {
                    "intent": intent,
                    "confidence": float(payload.get("confidence", 0.5)),
                    "reasoning": str(payload.get("reasoning", "")),
                }
            except Exception as exc:
                logger.warning("classify_intent 第 %d 次尝试失败: %s", attempt + 1, exc)

        logger.error("classify_intent 重试耗尽，降级到 Mock")
        return self.mock_fallback.invoke(LLMRequest(node_name="classify_intent", context=context))

    # ------------------------------------------------------------------
    # extract_slots
    # ------------------------------------------------------------------

    def _extract_slots(self, context: dict[str, Any]) -> dict[str, Any]:
        intent = context.get("intent", "UNKNOWN")
        user_prompt = f"工单内容：\n{build_text(context)}"
        for attempt in range(_MAX_RETRIES + 1):
            try:
                content = self._chat_json(EXTRACT_SLOTS_SYSTEM, user_prompt)
                payload = json.loads(content)
                slots = payload.get("slots", {})
                if not isinstance(slots, dict):
                    raise ValueError("模型返回的 slots 不是对象")
                # 缺失槽位由代码计算，确保与 SOP required_slots 一致
                missing = resolve_missing(intent, slots)
                return {
                    "slots": slots,
                    "missingSlots": missing,
                    "reasoning": str(payload.get("reasoning", "由 LLM 抽取槽位")),
                }
            except Exception as exc:
                logger.warning("extract_slots 第 %d 次尝试失败: %s", attempt + 1, exc)

        logger.error("extract_slots 重试耗尽，降级到 Mock")
        return self.mock_fallback.invoke(LLMRequest(node_name="extract_slots", context=context))

    # ------------------------------------------------------------------
    # generate_question
    # ------------------------------------------------------------------

    def _generate_question(self, context: dict[str, Any]) -> dict[str, Any]:
        intent = context.get("intent", "UNKNOWN")
        missing_slots = context.get("missingSlots", [])

        # 未知意图：确定性逻辑，不需要调用 LLM
        if intent == "UNKNOWN":
            return {
                "shouldAskUser": True,
                "question": "这个问题当前不在 MVP 支持范围内，建议转人工处理。",
                "nextStep": "ESCALATE_TO_HUMAN",
            }

        # 槽位完整：不需要追问
        if not missing_slots:
            return {
                "shouldAskUser": False,
                "question": "",
                "nextStep": "UNDERSTANDING_READY",
            }

        # 有缺失槽位：调用 LLM 生成自然语言追问
        for attempt in range(_MAX_RETRIES + 1):
            try:
                user_prompt = build_question_user_prompt(context)
                question_text = self._chat_text(
                    "你是企业 IT 服务台客服助手，请根据用户缺失的信息生成简短友好的追问。",
                    user_prompt,
                )
                question_text = question_text.strip().strip('"').strip("'")
                if question_text:
                    return {
                        "shouldAskUser": True,
                        "question": question_text,
                        "nextStep": "ASK_USER_FOR_MISSING_SLOTS",
                    }
            except Exception as exc:
                logger.warning("generate_question 第 %d 次尝试失败: %s", attempt + 1, exc)

        # LLM 失败时使用规则兜底文案
        logger.warning("generate_question 降级到规则文案")
        labels = "、".join(SLOT_LABELS.get(slot, slot) for slot in missing_slots)
        return {
            "shouldAskUser": True,
            "question": f"为了继续处理，请补充：{labels}。",
            "nextStep": "ASK_USER_FOR_MISSING_SLOTS",
        }

    # ------------------------------------------------------------------
    # 底层调用
    # ------------------------------------------------------------------

    def _chat_json(self, system_prompt: str, user_prompt: str) -> str:
        """调用 Chat Completions API，要求返回 JSON 格式。"""
        response = self._client.chat.completions.create(
            model=self.model,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            response_format={"type": "json_object"},
            temperature=0.1,
        )
        return response.choices[0].message.content or ""

    def _chat_text(self, system_prompt: str, user_prompt: str) -> str:
        """调用 Chat Completions API，返回纯文本。"""
        response = self._client.chat.completions.create(
            model=self.model,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            temperature=0.3,
        )
        return response.choices[0].message.content or ""
