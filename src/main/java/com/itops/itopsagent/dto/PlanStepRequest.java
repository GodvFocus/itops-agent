package com.itops.itopsagent.dto;

import com.itops.itopsagent.entity.enums.RiskLevel;
import java.util.Map;

public record PlanStepRequest(
        Integer stepNo,
        String tool,
        String action,
        String actionType,
        Map<String, Object> params,
        RiskLevel riskLevel,
        Boolean requiredApproval,
        String reason) {
}
