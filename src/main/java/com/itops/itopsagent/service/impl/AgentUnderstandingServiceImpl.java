package com.itops.itopsagent.service.impl;

import com.itops.itopsagent.dto.ConversationMessageResponse;
import com.itops.itopsagent.dto.TicketContextResponse;
import com.itops.itopsagent.dto.UpdateAgentContextRequest;
import com.itops.itopsagent.entity.Ticket;
import com.itops.itopsagent.entity.enums.AgentStepStatus;
import com.itops.itopsagent.entity.enums.ConversationMessageType;
import com.itops.itopsagent.entity.enums.ConversationRole;
import com.itops.itopsagent.entity.enums.RiskLevel;
import com.itops.itopsagent.mapper.TicketMapper;
import com.itops.itopsagent.service.AgentStepLogService;
import com.itops.itopsagent.service.AgentUnderstandingService;
import com.itops.itopsagent.service.ConversationMessageService;
import com.itops.itopsagent.service.TicketContextService;
import com.itops.itopsagent.service.agent.AgentContextSnapshot;
import com.itops.itopsagent.service.agent.ContextBuilder;
import com.itops.itopsagent.service.agent.IntentClassificationResult;
import com.itops.itopsagent.service.agent.IntentClassifier;
import com.itops.itopsagent.service.agent.MissingSlotQuestionGenerator;
import com.itops.itopsagent.service.agent.QuestionGenerationResult;
import com.itops.itopsagent.service.agent.SlotExtractionResult;
import com.itops.itopsagent.service.agent.SlotExtractor;
import com.itops.itopsagent.utils.exception.TicketNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentUnderstandingServiceImpl implements AgentUnderstandingService {

    private final TicketMapper ticketMapper;
    private final TicketContextService ticketContextService;
    private final ConversationMessageService conversationMessageService;
    private final AgentStepLogService agentStepLogService;
    private final ContextBuilder contextBuilder;
    private final IntentClassifier intentClassifier;
    private final SlotExtractor slotExtractor;
    private final MissingSlotQuestionGenerator questionGenerator;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void analyzeTicket(String ticketId) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new TicketNotFoundException(ticketId);
        }
        TicketContextResponse currentContext = ticketContextService.getContext(ticketId);
        List<ConversationMessageResponse> recentMessages = conversationMessageService.listRecentMessages(ticketId, 6);
        AgentContextSnapshot snapshot = contextBuilder.buildSnapshot(ticket, currentContext, recentMessages);
        String inputHash = hash(snapshot);

        try {
            IntentClassificationResult intentResult = intentClassifier.classify(snapshot);
            recordSuccess(ticketId, "classify_intent", inputHash, Map.of(
                    "intent", intentResult.intent().name(),
                    "confidence", intentResult.confidence(),
                    "reasoning", intentResult.reasoning()));

            SlotExtractionResult slotResult = slotExtractor.extract(snapshot, intentResult.intent());
            recordSuccess(ticketId, "extract_slots", inputHash, Map.of(
                    "slots", slotResult.slots(),
                    "missingSlots", slotResult.missingSlots(),
                    "reasoning", slotResult.reasoning()));

            QuestionGenerationResult questionResult = questionGenerator.generate(intentResult.intent(), slotResult.missingSlots());
            recordSuccess(ticketId, "generate_question", inputHash, Map.of(
                    "shouldAskUser", questionResult.shouldAskUser(),
                    "question", questionResult.question(),
                    "nextStep", questionResult.nextStep()));

            TicketContextResponse updatedContext = ticketContextService.saveContext(ticketId, new UpdateAgentContextRequest(
                    intentResult.intent(),
                    slotResult.slots(),
                    slotResult.missingSlots(),
                    List.of(),
                    Map.of(),
                    resolveRiskLevel(intentResult.intent()),
                    questionResult.nextStep()));

            if (questionResult.shouldAskUser() && !questionResult.question().isBlank()) {
                ConversationMessageType messageType = intentResult.intent().name().equals("UNKNOWN")
                        ? ConversationMessageType.AGENT_ESCALATION
                        : ConversationMessageType.AGENT_FOLLOW_UP;
                conversationMessageService.appendMessageIfChanged(ticketId, ConversationRole.AGENT, messageType, questionResult.question());
            } else {
                String summary = buildSummaryMessage(updatedContext);
                conversationMessageService.appendMessageIfChanged(ticketId, ConversationRole.AGENT, ConversationMessageType.AGENT_SUMMARY, summary);
            }
        } catch (RuntimeException exception) {
            agentStepLogService.record(
                    ticketId,
                    "analyze_ticket",
                    inputHash,
                    Map.of("error", exception.getMessage()),
                    AgentStepStatus.FAILED,
                    exception.getMessage());
            throw exception;
        }
    }

    private void recordSuccess(String ticketId, String nodeName, String inputHash, Map<String, Object> output) {
        agentStepLogService.record(ticketId, nodeName, inputHash, output, AgentStepStatus.SUCCESS, null);
    }

    private String buildSummaryMessage(TicketContextResponse context) {
        return "已识别为 " + context.intent().name() + "，关键槽位已满足当前阶段要求，可进入后续处理。";
    }

    private RiskLevel resolveRiskLevel(com.itops.itopsagent.entity.enums.TicketIntent intent) {
        return switch (intent) {
            case PERMISSION_REQUEST -> RiskLevel.MEDIUM;
            case UNKNOWN -> RiskLevel.MEDIUM;
            case ACCOUNT_LOGIN_ISSUE, VPN_CONNECTION_ISSUE -> RiskLevel.LOW;
        };
    }

    private String hash(AgentContextSnapshot snapshot) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] raw = digest.digest(objectMapper.writeValueAsString(snapshot).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (NoSuchAlgorithmException | JacksonException exception) {
            throw new IllegalStateException("Failed to hash agent context", exception);
        }
    }
}
