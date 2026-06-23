package com.itops.itopsagent.service.agent;

import com.itops.itopsagent.entity.enums.TicketIntent;

public record IntentClassificationResult(
        TicketIntent intent,
        double confidence,
        String reasoning) {
}
