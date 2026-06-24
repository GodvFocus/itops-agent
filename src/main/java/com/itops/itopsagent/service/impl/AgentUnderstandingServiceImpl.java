package com.itops.itopsagent.service.impl;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itops.itopsagent.dto.AgentContextResponse;
import com.itops.itopsagent.dto.CandidatePlanRequest;
import com.itops.itopsagent.dto.ConversationMessageResponse;
import com.itops.itopsagent.dto.TicketContextResponse;
import com.itops.itopsagent.dto.UpdateAgentContextRequest;
import com.itops.itopsagent.entity.Ticket;
import com.itops.itopsagent.entity.enums.AgentStepStatus;
import com.itops.itopsagent.entity.enums.ConversationMessageType;
import com.itops.itopsagent.entity.enums.ConversationRole;
import com.itops.itopsagent.mapper.TicketMapper;
import com.itops.itopsagent.service.AgentStepLogService;
import com.itops.itopsagent.service.AgentUnderstandingService;
import com.itops.itopsagent.service.ConversationMessageService;
import com.itops.itopsagent.service.TicketContextService;
import com.itops.itopsagent.service.agent.AgentRuntimeAnalysisResult;
import com.itops.itopsagent.service.agent.ContextBuilder;
import com.itops.itopsagent.service.agent.PythonAgentRuntimeClient;
import com.itops.itopsagent.utils.exception.TicketNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final PythonAgentRuntimeClient pythonAgentRuntimeClient;
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
        AgentContextResponse runtimeContext = contextBuilder.buildContextResponse(ticket, currentContext, recentMessages);
        String inputHash = hash(runtimeContext);

        try {
            AgentRuntimeAnalysisResult runtimeResult = pythonAgentRuntimeClient.analyze(runtimeContext);
            recordSuccess(ticketId, "classify_intent", inputHash, withRuntimeMeta(runtimeResult.intent(), runtimeResult.workflowMode()));
            recordSuccess(ticketId, "extract_slots", inputHash, withRuntimeMeta(runtimeResult.slots(), runtimeResult.workflowMode()));
            recordSuccess(ticketId, "generate_question", inputHash, withRuntimeMeta(runtimeResult.question(), runtimeResult.workflowMode()));
            if (!runtimeResult.retrieval().isEmpty()) {
                recordSuccess(ticketId, "retrieve_sop", inputHash, withRuntimeMeta(runtimeResult.retrieval(), runtimeResult.workflowMode()));
            }
            if (!runtimeResult.planSnapshot().isEmpty()) {
                recordSuccess(ticketId, "generate_plan", inputHash, withRuntimeMeta(runtimeResult.planSnapshot(), runtimeResult.workflowMode()));
            }

            CandidatePlanRequest candidatePlan = runtimeResult.candidatePlan();
            TicketContextResponse updatedContext = ticketContextService.saveContext(ticketId, new UpdateAgentContextRequest(
                    runtimeResult.resolvedIntent(),
                    runtimeResult.knownSlots(),
                    runtimeResult.missingSlots(),
                    runtimeResult.matchedSopIds(),
                    runtimeResult.planSnapshot(),
                    runtimeResult.resolvedRiskLevel(),
                    candidatePlan == null ? runtimeResult.nextStep() : "PLAN_READY"));

            if (runtimeResult.shouldAskUser() && !runtimeResult.questionText().isBlank()) {
                ConversationMessageType messageType = runtimeResult.resolvedIntent().name().equals("UNKNOWN")
                        ? ConversationMessageType.AGENT_ESCALATION
                        : ConversationMessageType.AGENT_FOLLOW_UP;
                conversationMessageService.appendMessageIfChanged(ticketId, ConversationRole.AGENT, messageType, runtimeResult.questionText());
            } else {
                String summary = buildSummaryMessage(updatedContext, candidatePlan != null);
                conversationMessageService.appendMessageIfChanged(ticketId, ConversationRole.AGENT, ConversationMessageType.AGENT_SUMMARY, summary);
            }
        } catch (RuntimeException exception) {
            agentStepLogService.record(
                    ticketId,
                    "python_agent_runtime",
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

    private Map<String, Object> withRuntimeMeta(Map<String, Object> payload, String workflowMode) {
        Map<String, Object> enriched = new LinkedHashMap<>(payload);
        enriched.put("workflowMode", workflowMode);
        enriched.put("runtime", "python");
        return enriched;
    }

    private String buildSummaryMessage(TicketContextResponse context, boolean planReady) {
        if (planReady) {
            return "已识别为 " + context.intent().name() + "，并由 Python Runtime 生成 Candidate Plan，已交给 Java Harness 继续裁决。";
        }
        return "已识别为 " + context.intent().name() + "，关键信息已满足当前理解阶段要求。";
    }

    private String hash(AgentContextResponse runtimeContext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] raw = digest.digest(objectMapper.writeValueAsString(runtimeContext).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (NoSuchAlgorithmException | JacksonException exception) {
            throw new IllegalStateException("Failed to hash agent context", exception);
        }
    }
}
