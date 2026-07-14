"""真实 LLM 客户端测试。

通过 mock OpenAI SDK 的 chat.completions.create 调用来验证：
- OpenAICompatibleLLMClient 能正确解析模型输出并填充节点契约。
- 工厂函数 create_llm_client 根据配置返回正确的客户端类型。
- 供应商默认配置正确填充。
- LLM 调用失败时降级到 MockLLMClient。
- 缺槽位追问的逻辑判断由代码决定，LLM 只负责生成文案。
"""

from __future__ import annotations

import json
from unittest.mock import MagicMock, patch

import pytest

from agent_runtime.config import CHAT_PROVIDER_DEFAULTS, ModelConfig, RuntimeSettings
from agent_runtime.llm_client import create_llm_client
from agent_runtime.llm_client.base import LLMRequest
from agent_runtime.llm_client.mock import MockLLMClient
from agent_runtime.llm_client.openai_compatible import OpenAICompatibleLLMClient
from agent_runtime.llm_client.slot_utils import REQUIRED_SLOTS_BY_INTENT, SLOT_LABELS, resolve_missing


# ---------------------------------------------------------------------------
# 辅助函数
# ---------------------------------------------------------------------------


def _make_chat_response(content: str):
    """构造一个模拟的 OpenAI ChatCompletion 响应对象。"""
    response = MagicMock()
    response.choices = [MagicMock()]
    response.choices[0].message.content = content
    return response


def _make_context(title="OA 登录失败", description="我的 OA 账号被锁定了，工号是 E10086"):
    return {
        "ticket_facts": {"ticketId": "T1", "title": title, "description": description, "status": "NEW"},
        "current_state": {"status": "NEW", "version": 0},
        "known_slots": {},
        "missing_slots": [],
        "recent_messages": [{"content": description}],
        "conversation_summary": "",
        "matched_sops": [],
        "tool_evidence": [],
        "approval_context": {},
        "risk_policy": {},
        "current_node": "INIT",
    }


def _make_client():
    """创建一个 OpenAICompatibleLLMClient，其底层 OpenAI SDK 被 mock。"""
    with patch("agent_runtime.llm_client.openai_compatible.OpenAICompatibleLLMClient._create_client") as mock_create:
        mock_create.return_value = MagicMock()
        client = OpenAICompatibleLLMClient(
            api_key="test-key",
            base_url="https://api.deepseek.com",
            model="deepseek-chat",
            provider="deepseek",
        )
    return client


# ---------------------------------------------------------------------------
# slot_utils 共享逻辑
# ---------------------------------------------------------------------------


def test_resolve_missing_should_match_sop_required_slots():
    assert resolve_missing("ACCOUNT_LOGIN_ISSUE", {"employeeId": "E1"}) == ["targetSystem"]
    assert resolve_missing("VPN_CONNECTION_ISSUE", {"employeeId": "E1", "deviceType": "IOS"}) == ["errorMessage"]
    assert resolve_missing("PERMISSION_REQUEST", {}) == [
        "employeeId", "targetSystem", "permissionLevel", "reason", "duration",
    ]
    assert resolve_missing("UNKNOWN", {}) == []


def test_required_slots_should_cover_all_supported_intents():
    assert set(REQUIRED_SLOTS_BY_INTENT.keys()) == {
        "ACCOUNT_LOGIN_ISSUE", "VPN_CONNECTION_ISSUE", "PERMISSION_REQUEST", "UNKNOWN",
    }


def test_slot_labels_should_cover_all_required_slots():
    all_slots = set()
    for slots in REQUIRED_SLOTS_BY_INTENT.values():
        all_slots.update(slots)
    for slot in all_slots:
        assert slot in SLOT_LABELS, f"槽位 {slot} 缺少中文标签"


# ---------------------------------------------------------------------------
# OpenAICompatibleLLMClient: classify_intent
# ---------------------------------------------------------------------------


def test_classify_intent_should_parse_valid_json():
    client = _make_client()
    client._client.chat.completions.create.return_value = _make_chat_response(
        json.dumps({"intent": "ACCOUNT_LOGIN_ISSUE", "confidence": 0.95, "reasoning": "命中登录异常"})
    )
    result = client.invoke(LLMRequest(node_name="classify_intent", context=_make_context()))
    assert result["intent"] == "ACCOUNT_LOGIN_ISSUE"
    assert 0 < result["confidence"] <= 1
    assert "登录" in result["reasoning"]


def test_classify_intent_should_reject_invalid_intent_and_retry():
    client = _make_client()
    # 第一次返回非法意图，第二次返回合法意图
    client._client.chat.completions.create.side_effect = [
        _make_chat_response(json.dumps({"intent": "INVALID", "confidence": 0.9, "reasoning": "bad"})),
        _make_chat_response(json.dumps({"intent": "VPN_CONNECTION_ISSUE", "confidence": 0.88, "reasoning": "VPN"})),
    ]
    result = client.invoke(LLMRequest(node_name="classify_intent", context=_make_context(
        title="VPN 连接失败", description="VPN 认证失败"
    )))
    assert result["intent"] == "VPN_CONNECTION_ISSUE"
    assert client._client.chat.completions.create.call_count == 2


def test_classify_intent_should_fallback_to_mock_when_all_retries_fail():
    client = _make_client()
    client._client.chat.completions.create.side_effect = RuntimeError("API error")
    result = client.invoke(LLMRequest(node_name="classify_intent", context=_make_context(
        title="VPN 连接失败", description="VPN 认证失败"
    )))
    # Mock 降级应返回 VPN_CONNECTION_ISSUE
    assert result["intent"] == "VPN_CONNECTION_ISSUE"


# ---------------------------------------------------------------------------
# OpenAICompatibleLLMClient: extract_slots
# ---------------------------------------------------------------------------


def test_extract_slots_should_compute_missing_slots_in_code():
    client = _make_client()
    client._client.chat.completions.create.return_value = _make_chat_response(
        json.dumps({"slots": {"employeeId": "E10086", "targetSystem": "OA"}, "reasoning": "抽取了员工编号和系统"})
    )
    context = _make_context()
    context["intent"] = "ACCOUNT_LOGIN_ISSUE"
    result = client.invoke(LLMRequest(node_name="extract_slots", context=context))
    assert result["slots"]["employeeId"] == "E10086"
    assert result["slots"]["targetSystem"] == "OA"
    # 槽位完整时 missingSlots 应为空
    assert result["missingSlots"] == []


def test_extract_slots_should_detect_missing_slots():
    client = _make_client()
    client._client.chat.completions.create.return_value = _make_chat_response(
        json.dumps({"slots": {"targetSystem": "OA"}, "reasoning": "只抽到系统"})
    )
    context = _make_context()
    context["intent"] = "ACCOUNT_LOGIN_ISSUE"
    result = client.invoke(LLMRequest(node_name="extract_slots", context=context))
    assert result["slots"]["targetSystem"] == "OA"
    assert "employeeId" in result["missingSlots"]


def test_extract_slots_should_fallback_to_mock_on_invalid_json():
    client = _make_client()
    client._client.chat.completions.create.return_value = _make_chat_response("not json at all")
    context = _make_context()
    context["intent"] = "ACCOUNT_LOGIN_ISSUE"
    result = client.invoke(LLMRequest(node_name="extract_slots", context=context))
    # Mock 降级应成功抽取
    assert "slots" in result
    assert "missingSlots" in result


# ---------------------------------------------------------------------------
# OpenAICompatibleLLMClient: generate_question
# ---------------------------------------------------------------------------


def test_generate_question_should_return_escalate_for_unknown_intent():
    """未知意图不需要调用 LLM，直接返回转人工。"""
    client = _make_client()
    context = _make_context()
    context["intent"] = "UNKNOWN"
    context["missingSlots"] = []
    result = client.invoke(LLMRequest(node_name="generate_question", context=context))
    assert result["shouldAskUser"] is True
    assert result["nextStep"] == "ESCALATE_TO_HUMAN"
    # 不应调用 LLM
    client._client.chat.completions.create.assert_not_called()


def test_generate_question_should_return_ready_when_no_missing_slots():
    """槽位完整时不需要调用 LLM。"""
    client = _make_client()
    context = _make_context()
    context["intent"] = "ACCOUNT_LOGIN_ISSUE"
    context["missingSlots"] = []
    result = client.invoke(LLMRequest(node_name="generate_question", context=context))
    assert result["shouldAskUser"] is False
    assert result["nextStep"] == "UNDERSTANDING_READY"
    client._client.chat.completions.create.assert_not_called()


def test_generate_question_should_call_llm_for_missing_slots():
    """有缺失槽位时调用 LLM 生成自然语言追问。"""
    client = _make_client()
    client._client.chat.completions.create.return_value = _make_chat_response(
        "请提供您的员工编号，以便我们进一步核实账号状态。"
    )
    context = _make_context()
    context["intent"] = "ACCOUNT_LOGIN_ISSUE"
    context["missingSlots"] = ["employeeId"]
    result = client.invoke(LLMRequest(node_name="generate_question", context=context))
    assert result["shouldAskUser"] is True
    assert result["nextStep"] == "ASK_USER_FOR_MISSING_SLOTS"
    assert "员工编号" in result["question"]
    client._client.chat.completions.create.assert_called_once()


def test_generate_question_should_fallback_to_template_on_llm_failure():
    """LLM 调用失败时使用模板文案兜底。"""
    client = _make_client()
    client._client.chat.completions.create.side_effect = RuntimeError("API error")
    context = _make_context()
    context["intent"] = "ACCOUNT_LOGIN_ISSUE"
    context["missingSlots"] = ["employeeId", "targetSystem"]
    result = client.invoke(LLMRequest(node_name="generate_question", context=context))
    assert result["shouldAskUser"] is True
    assert result["nextStep"] == "ASK_USER_FOR_MISSING_SLOTS"
    assert "员工编号" in result["question"]
    assert "目标系统" in result["question"]


# ---------------------------------------------------------------------------
# 工厂函数 create_llm_client
# ---------------------------------------------------------------------------


def test_factory_should_return_mock_for_mock_provider():
    settings = RuntimeSettings(
        qdrant_enabled=False,
        qdrant_url="",
        qdrant_collection_name="sop_catalog",
        embedding=ModelConfig(provider="mock", model="hash-embedding", endpoint="", api_key=""),
        chat=ModelConfig(provider="mock", model="mock-chat", endpoint="", api_key=""),
    )
    client = create_llm_client(settings)
    assert isinstance(client, MockLLMClient)


def test_factory_should_return_openai_compatible_for_deepseek():
    settings = RuntimeSettings(
        qdrant_enabled=False,
        qdrant_url="",
        qdrant_collection_name="sop_catalog",
        embedding=ModelConfig(provider="mock", model="hash-embedding", endpoint="", api_key=""),
        chat=ModelConfig(
            provider="deepseek",
            model="deepseek-chat",
            endpoint="https://api.deepseek.com",
            api_key="sk-test",
        ),
    )
    client = create_llm_client(settings)
    assert isinstance(client, OpenAICompatibleLLMClient)
    assert client.provider == "deepseek"
    assert client.model == "deepseek-chat"


def test_factory_should_fallback_to_mock_when_api_key_missing():
    """配置了真实 provider 但没有 api_key 时应降级到 mock。"""
    settings = RuntimeSettings(
        qdrant_enabled=False,
        qdrant_url="",
        qdrant_collection_name="sop_catalog",
        embedding=ModelConfig(provider="mock", model="hash-embedding", endpoint="", api_key=""),
        chat=ModelConfig(
            provider="deepseek",
            model="deepseek-chat",
            endpoint="https://api.deepseek.com",
            api_key="",
        ),
    )
    client = create_llm_client(settings)
    assert isinstance(client, MockLLMClient)


def test_factory_should_fallback_to_mock_for_unknown_provider():
    settings = RuntimeSettings(
        qdrant_enabled=False,
        qdrant_url="",
        qdrant_collection_name="sop_catalog",
        embedding=ModelConfig(provider="mock", model="hash-embedding", endpoint="", api_key=""),
        chat=ModelConfig(provider="unknown-llm", model="x", endpoint="http://x", api_key="k"),
    )
    client = create_llm_client(settings)
    assert isinstance(client, MockLLMClient)


# ---------------------------------------------------------------------------
# 供应商默认配置
# ---------------------------------------------------------------------------


@pytest.mark.parametrize("provider", ["deepseek", "qwen", "glm", "openai"])
def test_provider_defaults_should_have_endpoint_and_model(provider):
    defaults = CHAT_PROVIDER_DEFAULTS[provider]
    assert defaults["endpoint"]
    assert defaults["model"]


def test_config_should_fill_provider_defaults():
    """只配 provider 和 api_key 时，endpoint 和 model 应自动填充。"""
    from agent_runtime.config import load_runtime_settings

    settings = load_runtime_settings(
        env={"ITOPS_CHAT_PROVIDER": "deepseek", "ITOPS_CHAT_API_KEY": "sk-test"},
        dotenv_paths=(),
    )
    assert settings.chat.provider == "deepseek"
    assert settings.chat.endpoint == "https://api.deepseek.com"
    assert settings.chat.model == "deepseek-chat"
    assert settings.chat.api_key == "sk-test"


def test_config_should_allow_overriding_provider_defaults():
    """显式配置的 endpoint 和 model 应覆盖供应商默认值。"""
    from agent_runtime.config import load_runtime_settings

    settings = load_runtime_settings(
        env={
            "ITOPS_CHAT_PROVIDER": "deepseek",
            "ITOPS_CHAT_API_KEY": "sk-test",
            "ITOPS_CHAT_MODEL": "deepseek-reasoner",
            "ITOPS_CHAT_ENDPOINT": "https://custom.example.com",
        },
        dotenv_paths=(),
    )
    assert settings.chat.model == "deepseek-reasoner"
    assert settings.chat.endpoint == "https://custom.example.com"
