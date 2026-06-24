package com.itops.itopsagent.service.agent;

import com.itops.itopsagent.dto.CandidatePlanRequest;
import com.itops.itopsagent.dto.TicketContextResponse;
import com.itops.itopsagent.entity.Ticket;
import java.util.List;
import java.util.Map;

public interface SopPlanService {

    PlanBuildResult buildPlan(Ticket ticket, TicketContextResponse context);

    record PlanBuildResult(
            String selectedSopId,
            List<String> matchedSopIds,
            CandidatePlanRequest plan,
            Map<String, Object> planSnapshot) {
    }
}
