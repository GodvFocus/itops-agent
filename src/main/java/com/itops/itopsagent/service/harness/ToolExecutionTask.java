package com.itops.itopsagent.service.harness;

import com.itops.itopsagent.entity.enums.ToolActionType;
import java.util.Map;

public record ToolExecutionTask(
        String ticketId,
        String planId,
        Integer stepNo,
        String tool,
        String action,
        ToolActionType actionType,
        Map<String, Object> params,
        String idemKey,
        int attemptNo) {
}
