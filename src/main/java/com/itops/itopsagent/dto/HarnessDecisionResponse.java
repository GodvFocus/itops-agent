package com.itops.itopsagent.dto;

import java.util.List;
import java.util.Map;

public record HarnessDecisionResponse(
        String ticketId,
        String planId,
        String decision,
        String executionMode,
        String reason,
        String approvalType,
        List<Map<String, Object>> rejectedSteps,
        List<Map<String, Object>> approvedSteps) {
}
