package com.itops.itopsagent.service.agent;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itops.itopsagent.config.ItopsRuntimeProperties;
import com.itops.itopsagent.dto.AgentContextResponse;
import com.itops.itopsagent.dto.CandidatePlanRequest;
import com.itops.itopsagent.dto.PlanStepRequest;
import com.itops.itopsagent.entity.enums.RiskLevel;
import com.itops.itopsagent.entity.enums.TicketIntent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 通过受控进程调用 Python Agent Runtime，让 Java 继续掌管状态机、Harness 与持久化事实。
 */
@Service
@RequiredArgsConstructor
public class PythonAgentRuntimeClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final ItopsRuntimeProperties runtimeProperties;

    public AgentRuntimeAnalysisResult analyze(AgentContextResponse context) {
        Process process = startProcess();
        try {
            try (var outputStream = process.getOutputStream()) {
                objectMapper.writeValue(outputStream, buildPayload(context));
            }

            Duration timeout = runtimeProperties.getAgentRuntime().getTimeout();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Python Agent Runtime 执行超时");
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Python Agent Runtime 执行失败: " + (stderr.isBlank() ? stdout : stderr));
            }
            if (stdout.isBlank()) {
                throw new IllegalStateException("Python Agent Runtime 未返回有效结果");
            }
            return parse(stdout);
        } catch (IOException exception) {
            process.destroyForcibly();
            throw new IllegalStateException("调用 Python Agent Runtime 失败", exception);
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 Python Agent Runtime 结果时被中断", exception);
        }
    }

    private Process startProcess() {
        String pythonExecutable = runtimeProperties.getAgentRuntime().getPythonExecutable();
        ProcessBuilder processBuilder = new ProcessBuilder(
                pythonExecutable,
                "-m",
                "agent_runtime.runtime_cli",
                "analyze");
        // 以仓库根目录作为工作目录，保证 Python module 与 docs 合同路径都能稳定解析。
        processBuilder.directory(Path.of("").toAbsolutePath().toFile());
        try {
            return processBuilder.start();
        } catch (IOException exception) {
            throw new IllegalStateException("无法启动 Python Agent Runtime 进程: " + pythonExecutable, exception);
        }
    }

    private Map<String, Object> buildPayload(AgentContextResponse context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticket_facts", context.ticketFacts());
        payload.put("current_state", context.currentState());
        payload.put("known_slots", context.knownSlots());
        payload.put("missing_slots", context.missingSlots());
        payload.put("recent_messages", context.recentMessages());
        payload.put("conversation_summary", context.conversationSummary());
        payload.put("matched_sops", context.matchedSops());
        payload.put("tool_evidence", context.toolEvidence());
        payload.put("approval_context", context.approvalContext());
        payload.put("risk_policy", context.riskPolicy());
        payload.put("current_node", context.currentNode());
        return payload;
    }

    private AgentRuntimeAnalysisResult parse(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            Map<String, Object> intent = toMap(root.path("intent"));
            Map<String, Object> slots = toMap(root.path("slots"));
            Map<String, Object> question = toMap(root.path("question"));
            Map<String, Object> retrieval = root.has("retrieval") ? toMap(root.path("retrieval")) : Map.of();
            Map<String, Object> planSnapshot = root.has("plan") ? toMap(root.path("plan")) : Map.of();
            if (!retrieval.isEmpty()) {
                planSnapshot = new LinkedHashMap<>(planSnapshot);
                planSnapshot.put("selectedSopId", retrieval.getOrDefault("selectedSopId", ""));
            }
            CandidatePlanRequest candidatePlan = root.has("plan") ? toCandidatePlan(root.path("plan")) : null;
            return new AgentRuntimeAnalysisResult(
                    root.path("workflowMode").asText("sequential_fallback"),
                    intent,
                    slots,
                    question,
                    retrieval,
                    planSnapshot,
                    candidatePlan);
        } catch (JacksonException exception) {
            throw new IllegalStateException("解析 Python Agent Runtime 输出失败", exception);
        }
    }

    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, MAP_TYPE);
    }

    private CandidatePlanRequest toCandidatePlan(JsonNode planNode) {
        List<PlanStepRequest> steps = java.util.stream.StreamSupport.stream(planNode.path("steps").spliterator(), false)
                .map(stepNode -> new PlanStepRequest(
                        stepNode.path("stepNo").asInt(),
                        stepNode.path("tool").asText(),
                        stepNode.path("action").asText(),
                        stepNode.path("actionType").asText(),
                        toMap(stepNode.path("params")),
                        RiskLevel.valueOf(stepNode.path("riskLevel").asText()),
                        stepNode.path("requiredApproval").asBoolean(false),
                        stepNode.path("reason").asText()))
                .toList();
        return new CandidatePlanRequest(
                planNode.path("planId").asText(),
                planNode.path("ticketId").asText(),
                TicketIntent.valueOf(planNode.path("intent").asText()),
                RiskLevel.valueOf(planNode.path("riskLevel").asText()),
                planNode.path("goal").isMissingNode() || planNode.path("goal").isNull() ? null : planNode.path("goal").asText(),
                steps);
    }
}
