package com.itops.itopsagent.service.agent;

import com.itops.itopsagent.entity.enums.TicketIntent;
import java.util.List;
import java.util.Map;

public record AgentContextSnapshot(
        Map<String, Object> ticketFacts,
        Map<String, Object> currentState,
        Map<String, Object> knownSlots,
        List<String> missingSlots,
        List<Map<String, Object>> recentMessages,
        String conversationSummary,
        List<String> matchedSops,
        List<Map<String, Object>> toolEvidence,
        Map<String, Object> approvalContext,
        Map<String, Object> riskPolicy,
        String currentNode,
        TicketIntent previousIntent) {
}
