package com.itops.itopsagent.service.harness;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itops.itopsagent.entity.ToolCallLog;
import com.itops.itopsagent.entity.enums.ToolActionType;
import com.itops.itopsagent.entity.enums.ToolCallStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HarnessToolCallLogService {

    private final ToolCallLogStore toolCallLogStore;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public void record(
            String ticketId,
            String planId,
            Integer stepNo,
            String tool,
            String action,
            ToolActionType actionType,
            String idemKey,
            ToolCallStatus status,
            String decision,
            Map<String, Object> request,
            Map<String, Object> response,
            String errorMessage,
            int attemptNo) {
        Instant now = Instant.now(clock);
        toolCallLogStore.save(ToolCallLog.builder()
                .ticketId(ticketId)
                .planId(planId)
                .stepNo(stepNo)
                .toolName(tool)
                .actionName(action)
                .actionType(actionType)
                .idemKey(idemKey)
                .status(status)
                .decision(decision)
                .requestJson(serialize(request))
                .responseJson(response == null ? null : serialize(response))
                .errorMessage(errorMessage)
                .attemptNo(attemptNo)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    public List<ToolCallLog> findByTicketId(String ticketId) {
        return toolCallLogStore.findByTicketId(ticketId);
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize tool call payload", exception);
        }
    }
}
