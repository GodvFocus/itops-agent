"""按当前节点组装最小上下文，不把全量历史直接塞给模型。"""

from __future__ import annotations


class ContextBuilder:
    def build(self, ticket: dict, ticket_context: dict | None, recent_messages: list[dict]) -> dict:
        ticket_context = ticket_context or {}
        return {
            "ticket_facts": {
                "ticketId": ticket["ticketId"],
                "title": ticket["title"],
                "description": ticket["description"],
                "status": ticket["status"],
            },
            "current_state": {
                "status": ticket["status"],
                "version": ticket["version"],
            },
            "known_slots": ticket_context.get("slots", {}),
            "missing_slots": ticket_context.get("missingSlots", []),
            "recent_messages": recent_messages[-6:],
            "conversation_summary": ticket_context.get("conversationSummary", ""),
            "matched_sops": ticket_context.get("matchedSops", []),
            "tool_evidence": [],
            "approval_context": {},
            "risk_policy": {},
            "current_node": ticket_context.get("lastAgentStep", "INIT"),
        }
