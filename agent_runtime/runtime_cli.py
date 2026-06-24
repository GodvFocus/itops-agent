"""给 Java Backend 提供一个稳定的 Python Agent Runtime 命令行入口。"""

from __future__ import annotations

import json
import sys
from typing import Any

from agent_runtime.graph.workflow import build_workflow, run_workflow
from agent_runtime.llm_client.mock import MockLLMClient


def _normalize_context(payload: dict[str, Any]) -> dict[str, Any]:
    """把 Java 侧的 AgentContextResponse 映射成 Python Runtime 内部使用的上下文字段。"""

    return {
        "ticket_facts": payload.get("ticket_facts", payload.get("ticketFacts", {})),
        "current_state": payload.get("current_state", payload.get("currentState", {})),
        "known_slots": payload.get("known_slots", payload.get("knownSlots", {})),
        "missing_slots": payload.get("missing_slots", payload.get("missingSlots", [])),
        "recent_messages": payload.get("recent_messages", payload.get("recentMessages", [])),
        "conversation_summary": payload.get("conversation_summary", payload.get("conversationSummary", "")),
        "matched_sops": payload.get("matched_sops", payload.get("matchedSops", [])),
        "tool_evidence": payload.get("tool_evidence", payload.get("toolEvidence", [])),
        "approval_context": payload.get("approval_context", payload.get("approvalContext", {})),
        "risk_policy": payload.get("risk_policy", payload.get("riskPolicy", {})),
        "current_node": payload.get("current_node", payload.get("currentNode", "INIT")),
    }


def run_analysis(payload: dict[str, Any]) -> dict[str, Any]:
    """执行一次 Agent Runtime 分析，并返回统一 JSON 结果。"""

    workflow = build_workflow()
    client = MockLLMClient()
    context = _normalize_context(payload)

    if workflow["mode"] == "langgraph":
        # 当前项目仍以顺序执行结果为准；即便 LangGraph 可用，也保持输出契约一致。
        result = run_workflow(client, context)
    else:
        result = workflow["runner"](client, context)

    return {
        "workflowMode": workflow["mode"],
        **result,
    }


def main() -> int:
    command = sys.argv[1] if len(sys.argv) > 1 else "analyze"
    if command != "analyze":
        raise ValueError(f"Unsupported command: {command}")

    payload = json.load(sys.stdin)
    result = run_analysis(payload)
    json.dump(result, sys.stdout, ensure_ascii=False)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
