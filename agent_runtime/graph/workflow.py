"""真正的 LangGraph 状态图工作流。

用 LangGraph 的 StateGraph 把 classify_intent → extract_slots → generate_question
→ (条件边) → retrieve_sop → generate_plan 串成可观测的状态图编排：

- 定义统一 ``AgentState``，固化节点间传递的状态字段。
- 每个节点都是真实逻辑，不再是空操作。
- 使用条件边控制流程：缺槽位 / 未知意图时提前终止，槽位完整时进入 SOP 检索和 Plan 生成。
- 每个节点记录输入摘要、输出摘要、耗时、错误信息，便于回放和评估。
- 未安装 langgraph 时回退到顺序执行器 ``run_workflow``，保证离线环境可验收。
"""

from __future__ import annotations

import operator
import time
from datetime import datetime, timezone
from typing import Annotated, Any, TypedDict

from agent_runtime.nodes import classify_intent, extract_slots, generate_plan, generate_question, retrieve_sop


# ---------------------------------------------------------------------------
# AgentState：LangGraph 状态图统一状态对象
# ---------------------------------------------------------------------------


class AgentState(TypedDict, total=False):
    """贯穿所有节点的统一状态。

    字段分为三组：
    1. 输入上下文 —— 由 Java AgentContextResponse 归一化后注入。
    2. 节点工作键 —— 供下游节点读取的中间结果（如 ``intent`` 字符串）。
    3. 节点输出结果 —— 最终输出契约使用的完整节点结果字典。
    4. node_trace —— 使用 ``operator.add`` reducer 累加的节点级 trace，用于回放和评估。
    """

    # --- 输入上下文 ---
    ticket_facts: dict[str, Any]
    current_state: dict[str, Any]
    known_slots: dict[str, Any]
    missing_slots: list[str]
    recent_messages: list[dict[str, Any]]
    conversation_summary: str
    matched_sops: list[str]
    tool_evidence: list[Any]
    approval_context: dict[str, Any]
    risk_policy: dict[str, Any]
    current_node: str

    # --- 节点工作键（下游节点读取） ---
    intent: str
    missingSlots: list[str]
    selectedSopId: str

    # --- 节点输出结果（最终输出契约） ---
    intent_result: dict[str, Any]
    slots_result: dict[str, Any]
    question_result: dict[str, Any]
    retrieval_result: dict[str, Any]
    plan_result: dict[str, Any]

    # --- 可观测性 ---
    node_trace: Annotated[list[dict[str, Any]], operator.add]


# ---------------------------------------------------------------------------
# 节点级 trace 工具
# ---------------------------------------------------------------------------


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _trace_entry(
    node: str,
    started_at: str,
    elapsed_ms: float,
    *,
    error: str | None = None,
) -> dict[str, Any]:
    return {
        "node": node,
        "startedAt": started_at,
        "elapsedMs": round(elapsed_ms, 3),
        "error": error,
    }


# ---------------------------------------------------------------------------
# LangGraph 节点工厂
# ---------------------------------------------------------------------------


def _build_nodes(client: Any) -> dict[str, Any]:
    """创建绑定到指定 LLM client 的 LangGraph 节点函数。

    每个节点函数接收 ``AgentState``，返回需要合并回状态的部分字典。
    节点内部捕获异常并返回安全降级值，保证图不会因单节点失败而整体崩溃。
    """

    def classify_intent_node(state: AgentState) -> dict[str, Any]:
        started_at = _now_iso()
        t0 = time.perf_counter()
        try:
            result = classify_intent.run(client, state)
            elapsed = (time.perf_counter() - t0) * 1000
            return {
                "intent": result["intent"],
                "intent_result": result,
                "current_node": "classify_intent",
                "node_trace": [_trace_entry("classify_intent", started_at, elapsed)],
            }
        except Exception as exc:
            elapsed = (time.perf_counter() - t0) * 1000
            fallback = {"intent": "UNKNOWN", "confidence": 0.0, "reasoning": f"节点异常: {exc}"}
            return {
                "intent": "UNKNOWN",
                "intent_result": fallback,
                "current_node": "classify_intent",
                "node_trace": [_trace_entry("classify_intent", started_at, elapsed, error=str(exc))],
            }

    def extract_slots_node(state: AgentState) -> dict[str, Any]:
        started_at = _now_iso()
        t0 = time.perf_counter()
        try:
            result = extract_slots.run(client, state)
            elapsed = (time.perf_counter() - t0) * 1000
            return {
                "missingSlots": result["missingSlots"],
                "known_slots": result["slots"],
                "slots_result": result,
                "current_node": "extract_slots",
                "node_trace": [_trace_entry("extract_slots", started_at, elapsed)],
            }
        except Exception as exc:
            elapsed = (time.perf_counter() - t0) * 1000
            fallback = {
                "slots": state.get("known_slots", {}),
                "missingSlots": state.get("missingSlots", []),
                "reasoning": f"节点异常: {exc}",
            }
            return {
                "missingSlots": fallback["missingSlots"],
                "slots_result": fallback,
                "current_node": "extract_slots",
                "node_trace": [_trace_entry("extract_slots", started_at, elapsed, error=str(exc))],
            }

    def generate_question_node(state: AgentState) -> dict[str, Any]:
        started_at = _now_iso()
        t0 = time.perf_counter()
        try:
            result = generate_question.run(client, state)
            elapsed = (time.perf_counter() - t0) * 1000
            return {
                "question_result": result,
                "current_node": "generate_question",
                "node_trace": [_trace_entry("generate_question", started_at, elapsed)],
            }
        except Exception as exc:
            elapsed = (time.perf_counter() - t0) * 1000
            fallback = {
                "shouldAskUser": True,
                "question": "处理过程中出现异常，建议转人工处理。",
                "nextStep": "ESCALATE_TO_HUMAN",
            }
            return {
                "question_result": fallback,
                "current_node": "generate_question",
                "node_trace": [_trace_entry("generate_question", started_at, elapsed, error=str(exc))],
            }

    def retrieve_sop_node(state: AgentState) -> dict[str, Any]:
        started_at = _now_iso()
        t0 = time.perf_counter()
        try:
            result = retrieve_sop.run(state)
            elapsed = (time.perf_counter() - t0) * 1000
            return {
                "selectedSopId": result["selectedSopId"],
                "matched_sops": [match["sop_id"] for match in result["matchedSops"]],
                "retrieval_result": result,
                "current_node": "retrieve_sop",
                "node_trace": [_trace_entry("retrieve_sop", started_at, elapsed)],
            }
        except Exception as exc:
            elapsed = (time.perf_counter() - t0) * 1000
            return {
                "selectedSopId": "",
                "current_node": "retrieve_sop",
                "node_trace": [_trace_entry("retrieve_sop", started_at, elapsed, error=str(exc))],
            }

    def generate_plan_node(state: AgentState) -> dict[str, Any]:
        started_at = _now_iso()
        t0 = time.perf_counter()
        try:
            result = generate_plan.run(state)
            elapsed = (time.perf_counter() - t0) * 1000
            return {
                "plan_result": result,
                "current_node": "generate_plan",
                "node_trace": [_trace_entry("generate_plan", started_at, elapsed)],
            }
        except Exception as exc:
            elapsed = (time.perf_counter() - t0) * 1000
            return {
                "current_node": "generate_plan",
                "node_trace": [_trace_entry("generate_plan", started_at, elapsed, error=str(exc))],
            }

    return {
        "classify_intent": classify_intent_node,
        "extract_slots": extract_slots_node,
        "generate_question": generate_question_node,
        "retrieve_sop": retrieve_sop_node,
        "generate_plan": generate_plan_node,
    }


# ---------------------------------------------------------------------------
# 条件边路由函数
# ---------------------------------------------------------------------------


def route_after_question(state: AgentState) -> str:
    """generate_question 之后的条件路由。

    - 未知意图 → 直接结束（转人工）。
    - 仍需追问（缺槽位） → 直接结束（等待用户补充）。
    - 槽位完整 → 进入 SOP 检索。
    """
    if state.get("intent") == "UNKNOWN":
        return "end"
    question = state.get("question_result", {})
    if question.get("shouldAskUser"):
        return "end"
    return "retrieve_sop"


def route_after_retrieval(state: AgentState) -> str:
    """retrieve_sop 之后的条件路由。

    - 未选中 SOP（检索异常或无命中） → 直接结束，不生成 Plan。
    - 选中 SOP → 进入 Plan 生成。
    """
    if not state.get("selectedSopId"):
        return "end"
    return "generate_plan"


# ---------------------------------------------------------------------------
# 最终输出构建
# ---------------------------------------------------------------------------


def build_output(state: dict[str, Any]) -> dict[str, Any]:
    """把 LangGraph 最终状态映射成与顺序执行器一致的输出契约。"""
    result: dict[str, Any] = {
        "intent": state.get("intent_result", {}),
        "slots": state.get("slots_result", {}),
        "question": state.get("question_result", {}),
    }
    if state.get("retrieval_result"):
        result["retrieval"] = state["retrieval_result"]
    if state.get("plan_result"):
        result["plan"] = state["plan_result"]
    if state.get("node_trace"):
        result["nodeTrace"] = state["node_trace"]
    return result


# ---------------------------------------------------------------------------
# 顺序执行器（langgraph 未安装时的回退路径，同时保持测试兼容）
# ---------------------------------------------------------------------------


def run_workflow(client, context: dict) -> dict:
    """顺序执行器：与历史行为完全一致，作为 langgraph 不可用时的回退。"""
    state = dict(context)
    intent_result = classify_intent.run(client, state)
    state["intent"] = intent_result["intent"]
    slot_result = extract_slots.run(client, state)
    state["missingSlots"] = slot_result["missingSlots"]
    state["known_slots"] = slot_result["slots"]
    question_result = generate_question.run(client, state)
    result = {
        "intent": intent_result,
        "slots": slot_result,
        "question": question_result,
    }
    # UNKNOWN 或仍需追问时，不应继续生成 SOP / Plan，避免越界推进自动处理链路。
    if slot_result["missingSlots"] or question_result["shouldAskUser"] or intent_result["intent"] == "UNKNOWN":
        return result

    retrieval_result = retrieve_sop.run(state)
    state["selectedSopId"] = retrieval_result["selectedSopId"]
    state["matched_sops"] = [match["sop_id"] for match in retrieval_result["matchedSops"]]
    result["retrieval"] = retrieval_result
    result["plan"] = generate_plan.run(state)
    return result


# ---------------------------------------------------------------------------
# 工作流构建入口
# ---------------------------------------------------------------------------


def build_workflow(client: Any = None) -> dict[str, Any]:
    """构建 LangGraph 状态图工作流。

    - 安装了 langgraph 时返回编译后的真实状态图。
    - 未安装时回退到顺序执行器。

    参数:
        client: LLM Client 实例。为 None 时使用 MockLLMClient，保证 ``build_workflow()``
                无参调用也能正常工作（测试依赖此行为）。
    """
    try:
        from langgraph.graph import END, StateGraph
    except ImportError:
        return {"mode": "sequential_fallback", "runner": run_workflow}

    if client is None:
        from agent_runtime.llm_client.mock import MockLLMClient

        client = MockLLMClient()

    nodes = _build_nodes(client)
    graph: StateGraph = StateGraph(AgentState)

    graph.add_node("classify_intent", nodes["classify_intent"])
    graph.add_node("extract_slots", nodes["extract_slots"])
    graph.add_node("generate_question", nodes["generate_question"])
    graph.add_node("retrieve_sop", nodes["retrieve_sop"])
    graph.add_node("generate_plan", nodes["generate_plan"])

    graph.set_entry_point("classify_intent")

    # 线性边：意图分类 → 槽位抽取 → 缺槽追问
    graph.add_edge("classify_intent", "extract_slots")
    graph.add_edge("extract_slots", "generate_question")

    # 条件边：缺槽位 / 未知意图 → END；槽位完整 → SOP 检索
    graph.add_conditional_edges(
        "generate_question",
        route_after_question,
        {"retrieve_sop": "retrieve_sop", "end": END},
    )

    # 条件边：检索无命中 → END；命中 SOP → Plan 生成
    graph.add_conditional_edges(
        "retrieve_sop",
        route_after_retrieval,
        {"generate_plan": "generate_plan", "end": END},
    )

    graph.add_edge("generate_plan", END)

    return {"mode": "langgraph", "graph": graph.compile()}


def run_langgraph(compiled_graph: Any, context: dict[str, Any]) -> dict[str, Any]:
    """执行编译后的 LangGraph 状态图，返回与顺序执行器一致的输出契约。"""
    final_state = compiled_graph.invoke(dict(context))
    return build_output(final_state)
