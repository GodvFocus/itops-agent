package com.itops.itopsagent.dto;

import com.itops.itopsagent.entity.enums.RiskLevel;
import com.itops.itopsagent.entity.enums.TicketIntent;
import java.util.List;

public record CandidatePlanRequest(
        String planId,
        String ticketId,
        TicketIntent intent,
        RiskLevel riskLevel,
        String goal,
        List<PlanStepRequest> steps) {
}
