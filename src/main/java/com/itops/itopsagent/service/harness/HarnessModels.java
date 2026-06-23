package com.itops.itopsagent.service.harness;

import com.itops.itopsagent.dto.HarnessDecisionResponse;
import com.itops.itopsagent.entity.enums.IdempotencyStatus;
import com.itops.itopsagent.entity.enums.RiskLevel;
import com.itops.itopsagent.entity.enums.ToolActionType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

enum ApprovalRequirement {
    NONE,
    REQUIRED,
    CONDITIONAL
}

enum StepDecision {
    APPROVED,
    NEED_APPROVAL,
    REJECTED
}

enum IdempotencyClaimStatus {
    EXECUTE,
    DUPLICATE,
    LOCKED
}

record ToolRegistryEntry(
        String tool,
        String action,
        ToolActionType actionType,
        RiskLevel defaultRisk,
        List<String> requiredParams,
        ApprovalRequirement approvalRequirement,
        String idempotencyKeyPattern) {
}

record PlanStepAssessment(
        Integer stepNo,
        String tool,
        String action,
        ToolActionType actionType,
        Map<String, Object> params,
        RiskLevel effectiveRisk,
        StepDecision decision,
        boolean requiredApproval,
        String idemKey,
        String reason) {

    Map<String, Object> toResponseMap() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("stepNo", stepNo);
        response.put("tool", tool);
        response.put("action", action);
        response.put("decision", decision.name());
        response.put("requiredApproval", requiredApproval);
        response.put("riskLevel", effectiveRisk.name());
        response.put("idemKey", idemKey == null ? "" : idemKey);
        response.put("reason", reason);
        return response;
    }
}

record HarnessPlanEvaluation(
        HarnessDecisionResponse response,
        List<PlanStepAssessment> executableSteps,
        List<PlanStepAssessment> approvalSteps,
        List<PlanStepAssessment> rejectedSteps) {
}

record IdempotencyClaim(
        IdempotencyClaimStatus status,
        IdempotencyStatus recordStatus,
        String resultJson) {

    static IdempotencyClaim execute() {
        return new IdempotencyClaim(IdempotencyClaimStatus.EXECUTE, IdempotencyStatus.IN_PROGRESS, null);
    }

    static IdempotencyClaim duplicate(String resultJson) {
        return new IdempotencyClaim(IdempotencyClaimStatus.DUPLICATE, IdempotencyStatus.SUCCESS, resultJson);
    }

    static IdempotencyClaim locked() {
        return new IdempotencyClaim(IdempotencyClaimStatus.LOCKED, IdempotencyStatus.IN_PROGRESS, null);
    }
}
