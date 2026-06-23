package com.itops.itopsagent.service.impl;

import com.itops.itopsagent.entity.AuditLog;
import com.itops.itopsagent.mapper.AuditLogMapper;
import com.itops.itopsagent.service.AuditLogService;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    @Transactional
    public void record(String ticketId, String actorType, String actorId, String action, String targetType, String targetId, Map<String, Object> detail) {
        // 持久存储标准化审计事件，以便后续流程能够追溯操作人、修改内容及操作时间。
        auditLogMapper.insert(AuditLog.builder()
                .ticketId(ticketId)
                .actorType(actorType)
                .actorId(actorId)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .detailJson(serialize(detail))
                .createdAt(Instant.now(clock))
                .build());
    }

    private String serialize(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize audit detail", exception);
        }
    }
}
