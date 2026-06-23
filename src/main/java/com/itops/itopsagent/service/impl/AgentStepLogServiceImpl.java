package com.itops.itopsagent.service.impl;

import com.itops.itopsagent.dto.AgentStepLogResponse;
import com.itops.itopsagent.entity.AgentStepLog;
import com.itops.itopsagent.entity.enums.AgentStepStatus;
import com.itops.itopsagent.mapper.AgentStepLogMapper;
import com.itops.itopsagent.service.AgentStepLogService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentStepLogServiceImpl implements AgentStepLogService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AgentStepLogMapper agentStepLogMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    @Transactional
    public void record(String ticketId, String nodeName, String inputContextHash, Map<String, Object> output, AgentStepStatus status, String errorMessage) {
        agentStepLogMapper.insert(AgentStepLog.builder()
                .ticketId(ticketId)
                .nodeName(nodeName)
                .inputContextHash(inputContextHash)
                .outputJson(serialize(output))
                .status(status)
                .errorMessage(errorMessage)
                .createdAt(Instant.now(clock))
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentStepLogResponse> listLogs(String ticketId) {
        return agentStepLogMapper.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(log -> new AgentStepLogResponse(
                        log.getNodeName(),
                        log.getInputContextHash(),
                        deserialize(log.getOutputJson()),
                        log.getStatus(),
                        log.getErrorMessage(),
                        log.getCreatedAt()))
                .toList();
    }

    private String serialize(Map<String, Object> output) {
        try {
            return objectMapper.writeValueAsString(output);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize agent step output", exception);
        }
    }

    private Map<String, Object> deserialize(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to deserialize agent step output", exception);
        }
    }
}
