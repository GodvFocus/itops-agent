package com.itops.itopsagent.service.harness;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itops.itopsagent.entity.IdempotencyRecord;
import com.itops.itopsagent.entity.enums.IdempotencyStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class HarnessIdempotencyService {

    private final IdempotencyRecordStore idempotencyRecordStore;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    /** Redis 快速锁先挡住并发重复执行，再由 MySQL 幂等表提供最终事实。 */
    private final ConcurrentHashMap<String, String> fastClaims = new ConcurrentHashMap<>();

    public HarnessIdempotencyService(IdempotencyRecordStore idempotencyRecordStore, ObjectMapper objectMapper, Clock clock) {
        this.idempotencyRecordStore = idempotencyRecordStore;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public IdempotencyClaim claim(ToolExecutionTask task) {
        if (task.idemKey() == null || task.idemKey().isBlank()) {
            return IdempotencyClaim.execute();
        }
        IdempotencyRecord existing = idempotencyRecordStore.findByIdemKey(task.idemKey());
        if (existing != null && existing.getStatus() == IdempotencyStatus.SUCCESS) {
            return IdempotencyClaim.duplicate(existing.getResultJson());
        }
        String owner = task.ticketId() + ":" + task.planId() + ":" + task.stepNo() + ":" + task.attemptNo();
        if (fastClaims.putIfAbsent(task.idemKey(), owner) != null) {
            return IdempotencyClaim.locked();
        }
        Instant now = Instant.now(clock);
        idempotencyRecordStore.saveOrUpdate(IdempotencyRecord.builder()
                .id(existing == null ? null : existing.getId())
                .idemKey(task.idemKey())
                .ticketId(task.ticketId())
                .planId(task.planId())
                .stepNo(task.stepNo())
                .toolName(task.tool())
                .actionName(task.action())
                .status(IdempotencyStatus.IN_PROGRESS)
                .resultJson(existing == null ? null : existing.getResultJson())
                .errorMessage(null)
                .createdAt(existing == null ? now : existing.getCreatedAt())
                .updatedAt(now)
                .build());
        return IdempotencyClaim.execute();
    }

    public void markSuccess(ToolExecutionTask task, Map<String, Object> result) {
        if (task.idemKey() == null || task.idemKey().isBlank()) {
            return;
        }
        IdempotencyRecord existing = idempotencyRecordStore.findByIdemKey(task.idemKey());
        Instant now = Instant.now(clock);
        idempotencyRecordStore.saveOrUpdate(IdempotencyRecord.builder()
                .id(existing == null ? null : existing.getId())
                .idemKey(task.idemKey())
                .ticketId(task.ticketId())
                .planId(task.planId())
                .stepNo(task.stepNo())
                .toolName(task.tool())
                .actionName(task.action())
                .status(IdempotencyStatus.SUCCESS)
                .resultJson(serialize(result))
                .errorMessage(null)
                .createdAt(existing == null ? now : existing.getCreatedAt())
                .updatedAt(now)
                .build());
        fastClaims.remove(task.idemKey());
    }

    public void markFailure(ToolExecutionTask task, String errorMessage) {
        if (task.idemKey() == null || task.idemKey().isBlank()) {
            return;
        }
        IdempotencyRecord existing = idempotencyRecordStore.findByIdemKey(task.idemKey());
        Instant now = Instant.now(clock);
        idempotencyRecordStore.saveOrUpdate(IdempotencyRecord.builder()
                .id(existing == null ? null : existing.getId())
                .idemKey(task.idemKey())
                .ticketId(task.ticketId())
                .planId(task.planId())
                .stepNo(task.stepNo())
                .toolName(task.tool())
                .actionName(task.action())
                .status(IdempotencyStatus.FAILED)
                .resultJson(existing == null ? null : existing.getResultJson())
                .errorMessage(errorMessage)
                .createdAt(existing == null ? now : existing.getCreatedAt())
                .updatedAt(now)
                .build());
        fastClaims.remove(task.idemKey());
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize idempotency result", exception);
        }
    }
}
