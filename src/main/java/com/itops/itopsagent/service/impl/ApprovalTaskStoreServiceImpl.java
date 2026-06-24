package com.itops.itopsagent.service.impl;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itops.itopsagent.dto.ApprovalTaskResponse;
import com.itops.itopsagent.dto.CandidatePlanRequest;
import com.itops.itopsagent.entity.ApprovalTask;
import com.itops.itopsagent.entity.enums.ApprovalStatus;
import com.itops.itopsagent.mapper.ApprovalTaskMapper;
import com.itops.itopsagent.service.ApprovalTaskStoreService;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApprovalTaskStoreServiceImpl implements ApprovalTaskStoreService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ApprovalTaskMapper approvalTaskMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    @Transactional
    public ApprovalTaskResponse createPendingTask(String ticketId, CandidatePlanRequest plan, String requestedReason, List<Map<String, Object>> approvalSteps) {
        ApprovalTask existed = approvalTaskMapper.findPendingByTicketIdAndPlanId(ticketId, plan.planId());
        if (existed != null) {
            return toResponse(existed);
        }
        Instant now = Instant.now(clock);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("approvalSteps", approvalSteps);
        context.put("riskLevel", plan.riskLevel().name());
        context.put("intent", plan.intent().name());
        ApprovalTask task = ApprovalTask.builder()
                .approvalId("APR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase())
                .ticketId(ticketId)
                .planId(plan.planId())
                .status(ApprovalStatus.PENDING)
                .approvalType("MANUAL_APPROVAL")
                .requestedBy("HARNESS")
                .requestedReason(requestedReason)
                .planJson(serialize(plan))
                .contextJson(serialize(context))
                .createdAt(now)
                .updatedAt(now)
                .build();
        approvalTaskMapper.insert(task);
        return toResponse(task);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalTaskResponse> listAll() {
        return approvalTaskMapper.findAllOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalTaskResponse> listByTicketId(String ticketId) {
        return approvalTaskMapper.findByTicketIdOrderByCreatedAtAsc(ticketId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalTaskResponse getByApprovalId(String approvalId) {
        ApprovalTask task = requireTask(approvalId);
        return toResponse(task);
    }

    @Override
    @Transactional
    public ApprovalTaskResponse markApproved(String approvalId, String approverId, String comment) {
        ApprovalTask task = requireTask(approvalId);
        Instant now = Instant.now(clock);
        task.setStatus(ApprovalStatus.APPROVED);
        task.setApproverId(approverId);
        task.setApproverComment(comment);
        task.setDecidedAt(now);
        task.setUpdatedAt(now);
        approvalTaskMapper.updateById(task);
        return toResponse(task);
    }

    @Override
    @Transactional
    public ApprovalTaskResponse markRejected(String approvalId, String approverId, String comment) {
        ApprovalTask task = requireTask(approvalId);
        Instant now = Instant.now(clock);
        task.setStatus(ApprovalStatus.REJECTED);
        task.setApproverId(approverId);
        task.setApproverComment(comment);
        task.setDecidedAt(now);
        task.setUpdatedAt(now);
        approvalTaskMapper.updateById(task);
        return toResponse(task);
    }

    @Override
    @Transactional(readOnly = true)
    public CandidatePlanRequest getPlan(String approvalId) {
        ApprovalTask task = requireTask(approvalId);
        try {
            return objectMapper.readValue(task.getPlanJson(), CandidatePlanRequest.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to deserialize approval plan", exception);
        }
    }

    private ApprovalTask requireTask(String approvalId) {
        ApprovalTask task = approvalTaskMapper.findByApprovalId(approvalId);
        if (task == null) {
            throw new IllegalArgumentException("Approval task not found: " + approvalId);
        }
        return task;
    }

    private ApprovalTaskResponse toResponse(ApprovalTask task) {
        Map<String, Object> context = deserializeMap(task.getContextJson());
        List<Map<String, Object>> approvalSteps = context.containsKey("approvalSteps")
                ? objectMapper.convertValue(context.get("approvalSteps"), new TypeReference<List<Map<String, Object>>>() {
                })
                : List.of();
        return new ApprovalTaskResponse(
                task.getApprovalId(),
                task.getTicketId(),
                task.getPlanId(),
                task.getStatus(),
                task.getApprovalType(),
                task.getRequestedBy(),
                task.getRequestedReason(),
                task.getApproverId(),
                task.getApproverComment(),
                deserializeMap(task.getPlanJson()),
                approvalSteps,
                task.getCreatedAt(),
                task.getDecidedAt(),
                task.getUpdatedAt());
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize approval task", exception);
        }
    }

    private Map<String, Object> deserializeMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to deserialize approval task", exception);
        }
    }
}
