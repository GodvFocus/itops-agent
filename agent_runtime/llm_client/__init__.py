"""LLM client 工厂入口。

根据配置中的 ``ITOPS_CHAT_PROVIDER`` 自动选择对应的 LLM 客户端：
- ``mock``：规则驱动的 MockLLMClient（默认，离线可运行）
- ``deepseek`` / ``qwen`` / ``glm``：OpenAI 兼容协议的真实 LLM 客户端

其他遵循 OpenAI 兼容协议的供应商也可以直接通过 ``ITOPS_CHAT_PROVIDER`` 配置接入。
"""

from __future__ import annotations

import logging

from agent_runtime.llm_client.base import BaseLLMClient, LLMRequest
from agent_runtime.llm_client.mock import MockLLMClient

logger = logging.getLogger(__name__)

# 支持通过 OpenAI 兼容协议接入的供应商
_OPENAI_COMPATIBLE_PROVIDERS = {"deepseek", "qwen", "glm", "openai"}


def create_llm_client(settings=None) -> BaseLLMClient:
    """根据运行时配置创建 LLM 客户端实例。

    参数:
        settings: RuntimeSettings 实例。为 None 时自动加载默认配置。

    返回:
        BaseLLMClient 实例（MockLLMClient 或 OpenAICompatibleLLMClient）。
    """
    if settings is None:
        from agent_runtime.config import get_runtime_settings

        settings = get_runtime_settings()

    provider = settings.chat.provider.strip().lower()

    if provider == "mock":
        return MockLLMClient()

    if provider in _OPENAI_COMPATIBLE_PROVIDERS:
        if not settings.chat.api_key:
            logger.warning(
                "ITOPS_CHAT_PROVIDER=%s 但未配置 ITOPS_CHAT_API_KEY，降级到 MockLLMClient",
                provider,
            )
            return MockLLMClient()

        try:
            from agent_runtime.llm_client.openai_compatible import OpenAICompatibleLLMClient

            return OpenAICompatibleLLMClient(
                api_key=settings.chat.api_key,
                base_url=settings.chat.endpoint,
                model=settings.chat.model,
                provider=provider,
            )
        except Exception as exc:
            logger.warning("创建 %s LLM 客户端失败: %s，降级到 MockLLMClient", provider, exc)
            return MockLLMClient()

    # 未知的 provider 也降级到 mock，保证链路不中断
    logger.warning("未知 LLM provider: %s，降级到 MockLLMClient", provider)
    return MockLLMClient()


__all__ = ["BaseLLMClient", "LLMRequest", "MockLLMClient", "create_llm_client"]
