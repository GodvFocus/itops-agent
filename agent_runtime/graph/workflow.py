"""优先构建 LangGraph，未安装时回退到顺序执行器。"""

from __future__ import annotations

from agent_runtime.nodes import classify_intent, extract_slots, generate_plan, generate_question, retrieve_sop


def run_workflow(client, context: dict) -> dict:
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
    if not slot_result["missingSlots"]:
        retrieval_result = retrieve_sop.run(state)
        state["selectedSopId"] = retrieval_result["selectedSopId"]
        state["matched_sops"] = [match["sop_id"] for match in retrieval_result["matchedSops"]]
        result["retrieval"] = retrieval_result
        result["plan"] = generate_plan.run(state)
    return result


def build_workflow():
    try:
        from langgraph.graph import END, StateGraph  # type: ignore
    except ImportError:
        return {"mode": "sequential_fallback", "runner": run_workflow}

    graph = StateGraph(dict)
    graph.add_node("classify_intent", lambda state: state)
    graph.add_node("extract_slots", lambda state: state)
    graph.add_node("generate_question", lambda state: state)
    graph.add_node("retrieve_sop", lambda state: state)
    graph.add_node("generate_plan", lambda state: state)
    graph.add_edge("classify_intent", "extract_slots")
    graph.add_edge("extract_slots", "generate_question")
    graph.add_edge("generate_question", "retrieve_sop")
    graph.add_edge("retrieve_sop", "generate_plan")
    graph.add_edge("generate_plan", END)
    graph.set_entry_point("classify_intent")
    return {"mode": "langgraph", "graph": graph.compile()}
