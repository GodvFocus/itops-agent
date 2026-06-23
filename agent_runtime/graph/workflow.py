"""优先构建 LangGraph，未安装时回退到顺序执行器。"""

from __future__ import annotations

from agent_runtime.nodes import classify_intent, extract_slots, generate_question


def run_workflow(client, context: dict) -> dict:
    state = dict(context)
    intent_result = classify_intent.run(client, state)
    state["intent"] = intent_result["intent"]
    slot_result = extract_slots.run(client, state)
    state["missingSlots"] = slot_result["missingSlots"]
    question_result = generate_question.run(client, state)
    return {
        "intent": intent_result,
        "slots": slot_result,
        "question": question_result,
    }


def build_workflow():
    try:
        from langgraph.graph import END, StateGraph  # type: ignore
    except ImportError:
        return {"mode": "sequential_fallback", "runner": run_workflow}

    graph = StateGraph(dict)
    graph.add_node("classify_intent", lambda state: state)
    graph.add_node("extract_slots", lambda state: state)
    graph.add_node("generate_question", lambda state: state)
    graph.add_edge("classify_intent", "extract_slots")
    graph.add_edge("extract_slots", "generate_question")
    graph.add_edge("generate_question", END)
    graph.set_entry_point("classify_intent")
    return {"mode": "langgraph", "graph": graph.compile()}
