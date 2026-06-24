package com.itops.itopsagent.dto;

import com.itops.itopsagent.entity.enums.ToolActionType;
import com.itops.itopsagent.entity.enums.ToolCallStatus;
import java.time.Instant;
import java.util.Map;

public record ToolCallLogResponse(
        Long id,
        String planId,
        Integer stepNo,
        String toolName,
        String actionName,
        ToolActionType actionType,
        String idemKey,
        ToolCallStatus status,
        String decision,
        Map<String, Object> request,
        Map<String, Object> response,
        String errorMessage,
        Integer attemptNo,
        Instant createdAt,
        Instant updatedAt) {
}
