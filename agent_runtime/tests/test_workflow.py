from agent_runtime.context.context_builder import ContextBuilder
from agent_runtime.graph.workflow import build_workflow, run_workflow
from agent_runtime.llm_client.mock import MockLLMClient
from agent_runtime.models import IntentResult


def test_workflow_should_classify_and_ask_for_missing_employee_id():
    builder = ContextBuilder()
    context = builder.build(
        ticket={
            "ticketId": "T1",
            "title": "OA 登录失败",
            "description": "今天登录 OA 提示账号已锁定",
            "status": "NEW",
            "version": 0,
        },
        ticket_context=None,
        recent_messages=[{"content": "今天登录 OA 提示账号已锁定"}],
    )
    client = MockLLMClient()
    result = run_workflow(client, context)
    assert result["intent"]["intent"] == "ACCOUNT_LOGIN_ISSUE"
    assert "employeeId" in result["slots"]["missingSlots"]
    assert result["question"]["shouldAskUser"] is True


def test_pydantic_model_should_reject_unknown_fields():
    payload = {
        "intent": "VPN_CONNECTION_ISSUE",
        "confidence": 0.95,
        "reasoning": "命中 VPN 关键词",
        "unexpected": "bad-field",
    }
    try:
        IntentResult.model_validate(payload)
        raised = False
    except Exception:
        raised = True
    assert raised is True


def test_build_workflow_should_fallback_when_langgraph_is_unavailable():
    workflow = build_workflow()
    assert workflow["mode"] in {"sequential_fallback", "langgraph"}
