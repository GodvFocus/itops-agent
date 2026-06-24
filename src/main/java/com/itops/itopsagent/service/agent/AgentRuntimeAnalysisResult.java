package com.itops.itopsagent.service.agent;

import com.itops.itopsagent.dto.CandidatePlanRequest;
import com.itops.itopsagent.entity.enums.RiskLevel;
import com.itops.itopsagent.entity.enums.TicketIntent;
import java.util.List;
import java.util.Map;

/**
 * 统一承接 Python Runtime 返回结果，避免业务层直接处理松散 JSON。
 */
public record AgentRuntimeAnalysisResult(
        String workflowMode,
        Map<String, Object> intent,
        Map<String, Object> slots,
        Map<String, Object> question,
        Map<String, Object> retrieval,
        Map<String, Object> planSnapshot,
        CandidatePlanRequest candidatePlan) {

    public TicketIntent resolvedIntent() {
        return TicketIntent.valueOf(String.valueOf(intent.get("intent")));
    }

    public Map<String, Object> knownSlots() {
        Object raw = slots.get("slots");
        if (raw instanceof Map<?, ?> value) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) value;
            return typed;
        }
        return Map.of();
    }

    public List<String> missingSlots() {
        Object raw = slots.get("missingSlots");
        if (raw instanceof List<?> values) {
            return values.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    public boolean shouldAskUser() {
        return Boolean.TRUE.equals(question.get("shouldAskUser"));
    }

    public String questionText() {
        return String.valueOf(question.getOrDefault("question", ""));
    }

    public String nextStep() {
        return String.valueOf(question.getOrDefault("nextStep", "INIT"));
    }

    public List<String> matchedSopIds() {
        Object rawMatches = retrieval.get("matchedSops");
        if (!(rawMatches instanceof List<?> matches)) {
            return List.of();
        }
        return matches.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(match -> String.valueOf(match.get("sop_id")))
                .toList();
    }

    public String selectedSopId() {
        return String.valueOf(retrieval.getOrDefault("selectedSopId", ""));
    }

    public RiskLevel resolvedRiskLevel() {
        if (candidatePlan != null && candidatePlan.riskLevel() != null) {
            return candidatePlan.riskLevel();
        }
        return switch (resolvedIntent()) {
            case ACCOUNT_LOGIN_ISSUE, VPN_CONNECTION_ISSUE -> RiskLevel.LOW;
            case PERMISSION_REQUEST, UNKNOWN -> RiskLevel.MEDIUM;
        };
    }
}
