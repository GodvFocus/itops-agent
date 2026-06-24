package com.itops.itopsagent.service.impl;

import com.itops.itopsagent.dto.AddConversationMessageRequest;
import com.itops.itopsagent.dto.AgentStepLogResponse;
import com.itops.itopsagent.dto.ConversationMessageResponse;
import com.itops.itopsagent.dto.CreateTicketRequest;
import com.itops.itopsagent.dto.TicketConfirmRequest;
import com.itops.itopsagent.dto.TicketResponse;
import com.itops.itopsagent.dto.TicketContextResponse;
import com.itops.itopsagent.dto.TicketStatusHistoryResponse;
import com.itops.itopsagent.dto.TicketSummaryResponse;
import com.itops.itopsagent.dto.TransitionTicketStatusRequest;
import com.itops.itopsagent.entity.Ticket;
import com.itops.itopsagent.entity.TicketStatusHistory;
import com.itops.itopsagent.entity.enums.ConversationMessageType;
import com.itops.itopsagent.entity.enums.ConversationRole;
import com.itops.itopsagent.entity.enums.RiskLevel;
import com.itops.itopsagent.entity.enums.TicketIntent;
import com.itops.itopsagent.entity.enums.TicketPriority;
import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.UserRole;
import com.itops.itopsagent.mapper.TicketMapper;
import com.itops.itopsagent.mapper.TicketStatusHistoryMapper;
import com.itops.itopsagent.service.AgentStepLogService;
import com.itops.itopsagent.service.AgentUnderstandingService;
import com.itops.itopsagent.service.AuditLogService;
import com.itops.itopsagent.service.ConversationMessageService;
import com.itops.itopsagent.service.TicketService;
import com.itops.itopsagent.service.TicketAutomationService;
import com.itops.itopsagent.service.TicketContextService;
import com.itops.itopsagent.service.TicketStateMachineService;
import com.itops.itopsagent.utils.TicketIdGenerator;
import com.itops.itopsagent.utils.exception.TicketConflictException;
import com.itops.itopsagent.utils.exception.TicketNotFoundException;
import com.itops.itopsagent.utils.exception.TicketValidationException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
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
    /** 会话消息服务。 */
    private final ConversationMessageService conversationMessageService;
    /** 结构化上下文服务。 */
    private final TicketContextService ticketContextService;
    /** Agent 节点日志服务。 */
    private final AgentStepLogService agentStepLogService;
    /** 工单理解编排服务。 */
    private final AgentUnderstandingService agentUnderstandingService;
    /** 工单自动推进服务。 */
    private final TicketAutomationService ticketAutomationService;
    /** 统一时钟，便于测试和时间控制。 */
    private final Clock clock;

    public TicketServiceImpl(
            TicketMapper ticketMapper,
            TicketStatusHistoryMapper ticketStatusHistoryMapper,
            TicketStateMachineService ticketStateMachineService,
            TicketIdGenerator ticketIdGenerator,
            AuditLogService auditLogService,
            ConversationMessageService conversationMessageService,
            TicketContextService ticketContextService,
            AgentStepLogService agentStepLogService,
            AgentUnderstandingService agentUnderstandingService,
            TicketAutomationService ticketAutomationService,
            Clock clock) {
        this.ticketMapper = ticketMapper;
        this.ticketStatusHistoryMapper = ticketStatusHistoryMapper;
        this.ticketStateMachineService = ticketStateMachineService;
        this.ticketIdGenerator = ticketIdGenerator;
        this.auditLogService = auditLogService;
        this.conversationMessageService = conversationMessageService;
        this.ticketContextService = ticketContextService;
        this.agentStepLogService = agentStepLogService;
        this.agentUnderstandingService = agentUnderstandingService;
        this.ticketAutomationService = ticketAutomationService;
        this.clock = clock;
    }

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
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();
        ticketMapper.insert(ticket);
        Ticket saved = ticket;
        ticketStatusHistoryMapper.insert(TicketStatusHistory.builder()
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
        // 初始描述写入 conversation_message，后续 Agent 追问和前端回放都基于同一事实源。
        conversationMessageService.appendMessage(
                saved.getTicketId(),
                ConversationRole.USER,
                ConversationMessageType.TICKET_DESCRIPTION,
                request.description().trim());
        agentUnderstandingService.analyzeTicket(saved.getTicketId());
        ticketAutomationService.progressAfterUnderstanding(saved.getTicketId());
        return toTicketResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponse getTicket(String ticketId) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new TicketNotFoundException(ticketId);
        }
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
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new TicketNotFoundException(ticketId);
        }
        if (request.expectedVersion() != null && request.expectedVersion() != ticket.getVersion()) {
            throw new TicketConflictException(ticketId);
        }
        TicketStatus currentStatus = ticket.getStatus();
        ticketStateMachineService.assertTransitionAllowed(currentStatus, request.targetStatus(), request.actorRole());
        Instant now = Instant.now(clock);
        // 先做显式版本比对，再依赖 MyBatis-Plus 乐观锁插件兜底，避免并发下静默覆盖。
        ticket.transitionTo(request.targetStatus(), now);
        int updatedRows = ticketMapper.updateById(ticket);
        if (updatedRows == 0) {
            throw new TicketConflictException(ticketId);
        }
        ticketStatusHistoryMapper.insert(TicketStatusHistory.builder()
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
        Ticket saved = ticketMapper.selectById(ticketId);
        return toTicketResponse(saved);
    }

    @Override
    @Transactional
    public TicketResponse appendMessage(String ticketId, AddConversationMessageRequest request) {
        if (request == null || isBlank(request.content())) {
            throw new TicketValidationException("content is required");
        }
        if (ticketMapper.selectById(ticketId) == null) {
            throw new TicketNotFoundException(ticketId);
        }
        conversationMessageService.appendMessage(
                ticketId,
                ConversationRole.USER,
                ConversationMessageType.USER_REPLY,
                request.content().trim());
        auditLogService.record(
                ticketId,
                "USER",
                "USER_REPLY",
                "TICKET_MESSAGE_ADDED",
                "TICKET",
                ticketId,
                Map.of("messageLength", request.content().trim().length()));
        agentUnderstandingService.analyzeTicket(ticketId);
        ticketAutomationService.progressAfterUnderstanding(ticketId);
        return getTicket(ticketId);
    }

    @Override
    @Transactional
    public TicketResponse confirmTicket(String ticketId, TicketConfirmRequest request) {
        if (request == null || request.resolved() == null) {
            throw new TicketValidationException("resolved is required");
        }
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new TicketNotFoundException(ticketId);
        }
        String comment = request.comment() == null ? "" : request.comment().trim();
        if (!comment.isEmpty()) {
            conversationMessageService.appendMessage(ticketId, ConversationRole.USER, ConversationMessageType.USER_REPLY, comment);
        }
        if (Boolean.TRUE.equals(request.resolved())) {
            transitionStatus(ticketId, new TransitionTicketStatusRequest(
                    TicketStatus.RESOLVED,
                    ticket.getCreatorId(),
                    UserRole.EMPLOYEE,
                    null,
                    comment.isEmpty() ? "用户确认问题已解决" : comment));
            transitionStatus(ticketId, new TransitionTicketStatusRequest(
                    TicketStatus.CLOSED,
                    "AUTO_CLOSE",
                    UserRole.IT_ENGINEER,
                    null,
                    "用户确认通过，系统自动关闭工单"));
        } else {
            transitionStatus(ticketId, new TransitionTicketStatusRequest(
                    TicketStatus.TRIAGING,
                    ticket.getCreatorId(),
                    UserRole.EMPLOYEE,
                    null,
                    comment.isEmpty() ? "用户反馈问题未解决" : comment));
            transitionStatus(ticketId, new TransitionTicketStatusRequest(
                    TicketStatus.MANUAL_TAKEOVER,
                    "AUTO_ESCALATE",
                    UserRole.IT_ENGINEER,
                    null,
                    "用户确认未解决，转人工继续跟进"));
            conversationMessageService.appendMessageIfChanged(
                    ticketId,
                    ConversationRole.AGENT,
                    ConversationMessageType.AGENT_ESCALATION,
                    "用户反馈问题仍未解决，工单已转人工接管。");
        }
        return getTicket(ticketId);
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
        TicketContextResponse ticketContext = ticketContextService.getContext(ticket.getTicketId());
        List<ConversationMessageResponse> conversationMessages = conversationMessageService.listMessages(ticket.getTicketId());
        List<AgentStepLogResponse> agentStepLogs = agentStepLogService.listLogs(ticket.getTicketId());
        return new TicketResponse(
                ticket.getTicketId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getCreatorId(),
                ticket.getCreatorRole(),
                ticket.getStatus(),
                ticketContext.intent(),
                ticket.getPriority(),
                ticketContext.riskLevel(),
                ticket.getAssignedTo(),
                ticket.getVersion(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getClosedAt(),
                ticketContext,
                conversationMessages,
                agentStepLogs,
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
