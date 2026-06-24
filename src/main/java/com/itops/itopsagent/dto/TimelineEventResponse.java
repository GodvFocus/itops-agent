package com.itops.itopsagent.dto;

import java.time.Instant;

public record TimelineEventResponse(
        String lane,
        String type,
        String title,
        String detail,
        String status,
        Instant createdAt) {
}
