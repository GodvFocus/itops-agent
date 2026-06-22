package com.itops.itopsagent.service.impl;

import com.itops.itopsagent.dto.CreateTicketRequest;
import com.itops.itopsagent.dto.TicketResponse;
import com.itops.itopsagent.dto.TicketStatusHistoryResponse;
import com.itops.itopsagent.dto.TicketSummaryResponse;
import com.itops.itopsagent.dto.TransitionTicketStatusRequest;
import com.itops.itopsagent.entity.Ticket;
import com.itops.itopsagent.entity.TicketStatusHistory;
import com.itops.itopsagent.entity.enums.RiskLevel;
import com.itops.itopsagent.entity.enums.TicketIntent;
import com.itops.itopsagent.entity.enums.TicketPriority;
import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.UserRole;
import com.itops.itopsagent.mapper.TicketMapper;
import com.itops.itopsagent.mapper.TicketStatusHistoryMapper;
import com.itops.itopsagent.service.AuditLogService;
import com.itops.itopsagent.service.TicketService;
import com.itops.itopsagent.service.TicketStateMachineService;
import com.itops.itopsagent.utils.TicketIdGenerator;
import com.itops.itopsagent.utils.exception.TicketConflictException;
import com.itops.itopsagent.utils.exception.TicketNotFoundException;
import com.itops.itopsagent.utils.exception.TicketValidationException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    /** 工单持久化入口。 */
    private final TicketMapper ticketMapper;
    /** 工单状态历史持久化入口。 */
    private final TicketStatusHistoryMapper ticketStatusHistoryMapper;
    /** 状态机规则校验服务。 */
    private final TicketStateMachineService ticketStateMachineService;
    /** 工单编号生成器。 */
    private final TicketIdGenerator ticketIdGenerator;
    /** 审计日志服务。 */
    private final AuditLogService auditLogService;
    /** 统一时钟，便于测试和时间控制。 */
    private final Clock clock;

    @Override
    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {
        validateCreateRequest(request);
        Instant now = Instant.now(clock);
        UserRole creatorRole = request.creatorRole() == null ? UserRole.EMPLOYEE : request.creatorRole();
        TicketPriority priority = request.priority() == null ? TicketPriority.MEDIUM : request.priority();
        // Phase 1 先构建最小可用工单，意图和风险使用保守默认值。
        Ticket ticket = Ticket.builder()
                .ticketId(ticketIdGenerator.nextId())
                .title(request.title().trim())
                .description(request.description().trim())
                .creatorId(request.creatorId().trim())
                .creatorRole(creatorRole)
                .status(TicketStatus.NEW)
                .intent(TicketIntent.UNKNOWN)
                .priority(priority)
                .riskLevel(RiskLevel.LOW)
                .createdAt(now)
                .updatedAt(now)
                .build();
        Ticket saved = ticketMapper.save(ticket);
        ticketStatusHistoryMapper.save(TicketStatusHistory.builder()
                .ticketId(saved.getTicketId())
                .toStatus(TicketStatus.NEW)
                .actorId(saved.getCreatorId())
                .actorRole(saved.getCreatorRole())
                .comment("Ticket created")
                .createdAt(now)
                .build());
        // 创建动作也记入审计日志，保证“谁创建了什么工单”可追溯。
        auditLogService.record(
                saved.getTicketId(),
                "USER",
                saved.getCreatorId(),
                "TICKET_CREATED",
                "TICKET",
                saved.getTicketId(),
                Map.of("status", saved.getStatus().name(), "priority", saved.getPriority().name()));
        return toTicketResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponse getTicket(String ticketId) {
        Ticket ticket = ticketMapper.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        return toTicketResponse(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketSummaryResponse> listTickets() {
        return ticketMapper.findAllByOrderByCreatedAtDesc().stream()
                .map(ticket -> new TicketSummaryResponse(
                        ticket.getTicketId(),
                        ticket.getTitle(),
                        ticket.getStatus(),
                        ticket.getPriority(),
                        ticket.getVersion(),
                        ticket.getCreatedAt(),
                        ticket.getUpdatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public TicketResponse transitionStatus(String ticketId, TransitionTicketStatusRequest request) {
        validateTransitionRequest(request);
        try {
            Ticket ticket = ticketMapper.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
            if (request.expectedVersion() != null && request.expectedVersion() != ticket.getVersion()) {
                throw new TicketConflictException(ticketId);
            }
            TicketStatus currentStatus = ticket.getStatus();
            ticketStateMachineService.assertTransitionAllowed(currentStatus, request.targetStatus(), request.actorRole());
            Instant now = Instant.now(clock);
            // 先做显式版本比对，再依赖 JPA 乐观锁兜底，避免并发下静默覆盖。
            ticket.transitionTo(request.targetStatus(), now);
            Ticket saved = ticketMapper.saveAndFlush(ticket);
            ticketStatusHistoryMapper.save(TicketStatusHistory.builder()
                    .ticketId(ticketId)
                    .fromStatus(currentStatus)
                    .toStatus(request.targetStatus())
                    .actorId(request.actorId().trim())
                    .actorRole(request.actorRole())
                    .comment(request.comment())
                    .createdAt(now)
                    .build());
            auditLogService.record(
                    ticketId,
                    "USER",
                    request.actorId().trim(),
                    "TICKET_STATUS_CHANGED",
                    "TICKET",
                    ticketId,
                    Map.of(
                            "fromStatus", currentStatus.name(),
                            "toStatus", request.targetStatus().name(),
                            "actorRole", request.actorRole().name()));
            return toTicketResponse(saved);
        } catch (ObjectOptimisticLockingFailureException exception) {
            // saveAndFlush 阶段若命中乐观锁异常，统一转换为业务层冲突错误。
            throw new TicketConflictException(ticketId);
        }
    }

    private TicketResponse toTicketResponse(Ticket ticket) {
        // 详情接口需要直接带出完整状态历史，方便前端展示时间线。
        List<TicketStatusHistoryResponse> statusHistory = ticketStatusHistoryMapper
                .findByTicketIdOrderByCreatedAtAsc(ticket.getTicketId())
                .stream()
                .map(history -> new TicketStatusHistoryResponse(
                        history.getFromStatus(),
                        history.getToStatus(),
                        history.getActorId(),
                        history.getActorRole(),
                        history.getComment(),
                        history.getCreatedAt()))
                .toList();
        return new TicketResponse(
                ticket.getTicketId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getCreatorId(),
                ticket.getCreatorRole(),
                ticket.getStatus(),
                ticket.getIntent(),
                ticket.getPriority(),
                ticket.getRiskLevel(),
                ticket.getAssignedTo(),
                ticket.getVersion(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getClosedAt(),
                statusHistory);
    }

    private void validateCreateRequest(CreateTicketRequest request) {
        if (request == null) {
            throw new TicketValidationException("Request body is required");
        }
        if (isBlank(request.title())) {
            throw new TicketValidationException("title is required");
        }
        if (isBlank(request.description())) {
            throw new TicketValidationException("description is required");
        }
        if (isBlank(request.creatorId())) {
            throw new TicketValidationException("creatorId is required");
        }
    }

    private void validateTransitionRequest(TransitionTicketStatusRequest request) {
        if (request == null) {
            throw new TicketValidationException("Request body is required");
        }
        if (request.targetStatus() == null) {
            throw new TicketValidationException("targetStatus is required");
        }
        if (isBlank(request.actorId())) {
            throw new TicketValidationException("actorId is required");
        }
        if (request.actorRole() == null) {
            throw new TicketValidationException("actorRole is required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
