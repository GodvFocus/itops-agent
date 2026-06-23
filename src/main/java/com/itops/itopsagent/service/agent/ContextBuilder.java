package com.itops.itopsagent.service.agent;

import com.itops.itopsagent.dto.AgentContextResponse;
import com.itops.itopsagent.dto.ConversationMessageResponse;
import com.itops.itopsagent.dto.TicketContextResponse;
import com.itops.itopsagent.entity.Ticket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ContextBuilder {

    public AgentContextSnapshot buildSnapshot(Ticket ticket, TicketContextResponse context, List<ConversationMessageResponse> recentMessages) {
        return new AgentContextSnapshot(
                Map.of(
                        "ticketId", ticket.getTicketId(),
                        "title", ticket.getTitle(),
                        "description", ticket.getDescription(),
                        "creatorId", ticket.getCreatorId(),
                        "creatorRole", ticket.getCreatorRole().name(),
                        "status", ticket.getStatus().name()),
                Map.of(
                        "status", ticket.getStatus().name(),
                        "version", ticket.getVersion(),
                        "updatedAt", ticket.getUpdatedAt().toString()),
                context.slots(),
                context.missingSlots(),
                recentMessages.stream().map(message -> Map.<String, Object>of(
                                "id", message.id(),
                                "role", message.role().name(),
                                "content", message.content(),
                                "messageType", message.messageType().name(),
                                "createdAt", message.createdAt().toString()))
                        .toList(),
                "",
                context.matchedSopIds(),
                List.of(),
                Map.of(),
                Map.of(),
                context.lastAgentStep(),
                context.intent());
    }

    public AgentContextResponse buildContextResponse(Ticket ticket, TicketContextResponse context, List<ConversationMessageResponse> recentMessages) {
        AgentContextSnapshot snapshot = buildSnapshot(ticket, context, recentMessages);
        return new AgentContextResponse(
                snapshot.ticketFacts(),
                snapshot.currentState(),
                snapshot.knownSlots(),
                snapshot.missingSlots(),
                snapshot.recentMessages(),
                snapshot.conversationSummary(),
                snapshot.matchedSops(),
                snapshot.toolEvidence(),
                snapshot.approvalContext(),
                snapshot.riskPolicy(),
                snapshot.currentNode());
    }

    public Map<String, Object> buildCompletionSummary(TicketContextResponse context) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("intent", context.intent().name());
        summary.put("slots", context.slots());
        summary.put("missingSlots", context.missingSlots());
        summary.put("lastAgentStep", context.lastAgentStep());
        return summary;
    }
}
