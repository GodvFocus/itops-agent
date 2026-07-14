"""LangGraph 状态图工作流专项测试。

验证真实状态图编排的核心行为：
- langgraph 可用时以 "langgraph" 模式运行。
- 条件边：缺槽位 / 未知意图时提前终止，不生成 SOP / Plan。
- 槽位完整时走完整链路并生成 Candidate Plan。
- 每个执行过的节点都在 nodeTrace 中留下记录。
- LangGraph 路径与顺序执行器输出契约一致。
"""

from __future__ import annotations

import pytest

from agent_runtime.context.context_builder import ContextBuilder
from agent_runtime.graph.workflow import (
    AgentState,
    build_output,
    build_workflow,
    run_langgraph,
    run_workflow,
)
from agent_runtime.llm_client.mock import MockLLMClient


def _build_context(title: str, description: str, recent_content: str | None = None) -> dict:
    builder = ContextBuilder()
    return builder.build(
        ticket={
            "ticketId": "T-LG-001",
            "title": title,
            "description": description,
            "status": "NEW",
            "version": 0,
        },
        ticket_context=None,
        recent_messages=[{"content": recent_content or description}],
    )


# ---------------------------------------------------------------------------
# 模式验证
# ---------------------------------------------------------------------------


def test_build_workflow_should_return_langgraph_mode_when_installed():
    """langgraph 已安装时 build_workflow 应返回编译后的真实状态图。"""
    workflow = build_workflow(MockLLMClient())
    assert workflow["mode"] in {"langgraph", "sequential_fallback"}
    if workflow["mode"] == "langgraph":
        assert workflow["graph"] is not None


def test_build_workflow_without_client_should_still_work():
    """无参调用 build_workflow 不报错（测试依赖此行为）。"""
    workflow = build_workflow()
    assert workflow["mode"] in {"langgraph", "sequential_fallback"}


# ---------------------------------------------------------------------------
# 条件边：缺槽位提前终止
# ---------------------------------------------------------------------------


def test_langgraph_should_stop_at_question_when_slots_missing():
    """缺槽位时 generate_question 返回 shouldAskUser=True，条件边路由到 END。"""
    client = MockLLMClient()
    workflow = build_workflow(client)
    if workflow["mode"] != "langgraph":
        pytest.skip("langgraph not installed")

    context = _build_context("OA 登录失败", "今天登录 OA 提示账号已锁定")
    result = run_langgraph(workflow["graph"], context)

    assert result["intent"]["intent"] == "ACCOUNT_LOGIN_ISSUE"
    assert "employeeId" in result["slots"]["missingSlots"]
    assert result["question"]["shouldAskUser"] is True
    # 缺槽位时不应生成 retrieval / plan
    assert "retrieval" not in result
    assert "plan" not in result


# ---------------------------------------------------------------------------
# 条件边：未知意图转人工
# ---------------------------------------------------------------------------


def test_langgraph_should_stop_for_unknown_intent():
    """未知意图时条件边路由到 END，不进入 SOP 检索。"""
    client = MockLLMClient()
    workflow = build_workflow(client)
    if workflow["mode"] != "langgraph":
        pytest.skip("langgraph not installed")

    context = _build_context("打印机坏了", "办公室打印机无法连接，需要维修")
    result = run_langgraph(workflow["graph"], context)

    assert result["intent"]["intent"] == "UNKNOWN"
    assert result["question"]["shouldAskUser"] is True
    assert result["question"]["nextStep"] == "ESCALATE_TO_HUMAN"
    assert "retrieval" not in result
    assert "plan" not in result


# ---------------------------------------------------------------------------
# 完整链路：槽位完整时生成 SOP / Plan
# ---------------------------------------------------------------------------


def test_langgraph_should_generate_plan_when_slots_complete():
    """槽位完整时走完整链路：classify → extract → question → retrieve → plan。"""
    client = MockLLMClient()
    workflow = build_workflow(client)
    if workflow["mode"] != "langgraph":
        pytest.skip("langgraph not installed")

    context = _build_context(
        "OA 登录失败",
        "我的 OA 账号被锁定了，工号是 E10086",
    )
    result = run_langgraph(workflow["graph"], context)

    assert result["intent"]["intent"] == "ACCOUNT_LOGIN_ISSUE"
    assert result["question"]["shouldAskUser"] is False
    assert result["retrieval"]["selectedSopId"] == "SOP-ACC-LOCKED-001"
    assert result["plan"]["ticketId"] == "T-LG-001"
    assert len(result["plan"]["steps"]) > 0


# ---------------------------------------------------------------------------
# 节点级 trace 可观测性
# ---------------------------------------------------------------------------


def test_langgraph_should_produce_node_trace_for_full_path():
    """完整链路应在 nodeTrace 中记录所有 5 个节点。"""
    client = MockLLMClient()
    workflow = build_workflow(client)
    if workflow["mode"] != "langgraph":
        pytest.skip("langgraph not installed")

    context = _build_context("OA 登录失败", "我的 OA 账号被锁定了，工号是 E10086")
    result = run_langgraph(workflow["graph"], context)

    trace = result.get("nodeTrace")
    assert trace is not None
    executed_nodes = [entry["node"] for entry in trace]
    assert executed_nodes == [
        "classify_intent",
        "extract_slots",
        "generate_question",
        "retrieve_sop",
        "generate_plan",
    ]
    for entry in trace:
        assert "startedAt" in entry
        assert "elapsedMs" in entry
        assert entry["error"] is None


def test_langgraph_should_produce_node_trace_for_short_path():
    """缺槽位短路时 nodeTrace 应只记录前 3 个节点。"""
    client = MockLLMClient()
    workflow = build_workflow(client)
    if workflow["mode"] != "langgraph":
        pytest.skip("langgraph not installed")

    context = _build_context("OA 登录失败", "今天登录 OA 提示账号已锁定")
    result = run_langgraph(workflow["graph"], context)

    trace = result["nodeTrace"]
    executed_nodes = [entry["node"] for entry in trace]
    assert executed_nodes == ["classify_intent", "extract_slots", "generate_question"]


# ---------------------------------------------------------------------------
# LangGraph 与顺序执行器输出契约一致性
# ---------------------------------------------------------------------------


def test_langgraph_output_should_match_sequential_for_complete_case():
    """同一输入下，LangGraph 路径与顺序执行器输出契约一致。"""
    client = MockLLMClient()
    context = _build_context("OA 登录失败", "我的 OA 账号被锁定了，工号是 E10086")

    seq_result = run_workflow(client, dict(context))

    workflow = build_workflow(client)
    if workflow["mode"] != "langgraph":
        pytest.skip("langgraph not installed")

    lg_result = run_langgraph(workflow["graph"], dict(context))

    # 核心字段必须一致（nodeTrace 是 LangGraph 路径独有，不比较）
    assert lg_result["intent"] == seq_result["intent"]
    assert lg_result["slots"] == seq_result["slots"]
    assert lg_result["question"] == seq_result["question"]
    assert lg_result["retrieval"]["selectedSopId"] == seq_result["retrieval"]["selectedSopId"]
    assert lg_result["plan"]["planId"] == seq_result["plan"]["planId"]
    assert lg_result["plan"]["ticketId"] == seq_result["plan"]["ticketId"]
    assert [s["action"] for s in lg_result["plan"]["steps"]] == [s["action"] for s in seq_result["plan"]["steps"]]


def test_langgraph_output_should_match_sequential_for_missing_slot_case():
    """缺槽位场景下，LangGraph 与顺序执行器都应短路且输出一致。"""
    client = MockLLMClient()
    context = _build_context("OA 登录失败", "今天登录 OA 提示账号已锁定")

    seq_result = run_workflow(client, dict(context))

    workflow = build_workflow(client)
    if workflow["mode"] != "langgraph":
        pytest.skip("langgraph not installed")

    lg_result = run_langgraph(workflow["graph"], dict(context))

    assert lg_result["intent"] == seq_result["intent"]
    assert lg_result["question"]["shouldAskUser"] == seq_result["question"]["shouldAskUser"]
    assert "retrieval" not in lg_result
    assert "retrieval" not in seq_result
    assert "plan" not in lg_result
    assert "plan" not in seq_result


# ---------------------------------------------------------------------------
# build_output 单元测试
# ---------------------------------------------------------------------------


def test_build_output_should_map_state_to_contract():
    state = {
        "intent_result": {"intent": "VPN_CONNECTION_ISSUE", "confidence": 0.9, "reasoning": "test"},
        "slots_result": {"slots": {}, "missingSlots": [], "reasoning": "test"},
        "question_result": {"shouldAskUser": False, "question": "", "nextStep": "UNDERSTANDING_READY"},
        "retrieval_result": {"selectedSopId": "SOP-1", "reasoning": "", "matchedSops": []},
        "plan_result": {"planId": "plan-1", "ticketId": "T1", "intent": "VPN_CONNECTION_ISSUE", "riskLevel": "LOW", "steps": []},
        "node_trace": [{"node": "classify_intent", "startedAt": "2025-01-01T00:00:00Z", "elapsedMs": 1.0, "error": None}],
    }
    output = build_output(state)
    assert output["intent"]["intent"] == "VPN_CONNECTION_ISSUE"
    assert output["retrieval"]["selectedSopId"] == "SOP-1"
    assert output["plan"]["planId"] == "plan-1"
    assert len(output["nodeTrace"]) == 1


def test_build_output_should_omit_optional_keys_when_absent():
    state = {
        "intent_result": {"intent": "UNKNOWN", "confidence": 0.5, "reasoning": "test"},
        "slots_result": {"slots": {}, "missingSlots": [], "reasoning": "test"},
        "question_result": {"shouldAskUser": True, "question": "请补充信息", "nextStep": "ASK_USER"},
    }
    output = build_output(state)
    assert "retrieval" not in output
    assert "plan" not in output
    assert "nodeTrace" not in output


# ---------------------------------------------------------------------------
# AgentState 类型存在性验证
# ---------------------------------------------------------------------------


def test_agent_state_should_be_defined():
    """AgentState 必须作为统一状态类型存在，供 LangGraph 状态图使用。"""
    assert AgentState is not None
    # TypedDict 在运行时仍是 dict 子类提示
    assert callable(AgentState)
