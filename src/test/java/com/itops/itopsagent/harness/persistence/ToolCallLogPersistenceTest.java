package com.itops.itopsagent.harness.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.itops.itopsagent.dto.CandidatePlanRequest;
import com.itops.itopsagent.dto.CreateTicketRequest;
import com.itops.itopsagent.dto.PlanStepRequest;
import com.itops.itopsagent.dto.TicketResponse;
import com.itops.itopsagent.dto.TransitionTicketStatusRequest;
import com.itops.itopsagent.entity.enums.RiskLevel;
import com.itops.itopsagent.entity.enums.TicketIntent;
import com.itops.itopsagent.entity.enums.TicketPriority;
import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.ToolCallStatus;
import com.itops.itopsagent.entity.enums.UserRole;
import com.itops.itopsagent.mapper.AgentStepLogMapper;
import com.itops.itopsagent.mapper.AuditLogMapper;
import com.itops.itopsagent.mapper.ConversationMessageMapper;
import com.itops.itopsagent.mapper.IdempotencyRecordMapper;
import com.itops.itopsagent.mapper.TicketContextMapper;
import com.itops.itopsagent.mapper.TicketMapper;
import com.itops.itopsagent.mapper.TicketStatusHistoryMapper;
import com.itops.itopsagent.mapper.ToolCallLogMapper;
import com.itops.itopsagent.service.TicketService;
import com.itops.itopsagent.service.harness.HarnessPlanValidationService;
import com.itops.itopsagent.service.harness.ToolTaskProcessor;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ToolCallLogPersistenceTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private HarnessPlanValidationService harnessPlanValidationService;

    @Autowired
    private ToolTaskProcessor toolTaskProcessor;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private TicketStatusHistoryMapper ticketStatusHistoryMapper;

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Autowired
    private AgentStepLogMapper agentStepLogMapper;

    @Autowired
    private ConversationMessageMapper conversationMessageMapper;

    @Autowired
    private TicketContextMapper ticketContextMapper;

    @Autowired
    private ToolCallLogMapper toolCallLogMapper;

    @Autowired
    private IdempotencyRecordMapper idempotencyRecordMapper;

    @BeforeEach
    void setUp() {
        toolCallLogMapper.deleteAllRecords();
        idempotencyRecordMapper.deleteAllRecords();
        auditLogMapper.deleteAllRecords();
        agentStepLogMapper.deleteAllRecords();
        conversationMessageMapper.deleteAllRecords();
        ticketContextMapper.deleteAllRecords();
        ticketStatusHistoryMapper.deleteAllRecords();
        ticketMapper.deleteAllRecords();
    }

    @Test
    void shouldPersistToolCallLogAndIdempotencyRecord() {
        TicketResponse created = ticketService.createTicket(new CreateTicketRequest(
                "账号解锁",
                "我的账号被锁住了",
                "U5001",
                UserRole.EMPLOYEE,
                TicketPriority.MEDIUM));
        TicketResponse triaged = ticketService.transitionStatus(
                created.ticketId(),
                new TransitionTicketStatusRequest(TicketStatus.TRIAGING, "E5001", UserRole.IT_ENGINEER, created.version(), "开始分诊"));
        ticketService.transitionStatus(
                created.ticketId(),
                new TransitionTicketStatusRequest(TicketStatus.PLANNING, "E5001", UserRole.IT_ENGINEER, triaged.version(), "进入计划阶段"));

        harnessPlanValidationService.executePlan(new CandidatePlanRequest(
                "plan-persist-001",
                created.ticketId(),
                TicketIntent.ACCOUNT_LOGIN_ISSUE,
                RiskLevel.MEDIUM,
                "解锁账号并通知用户",
                List.of(
                        new PlanStepRequest(
                                1,
                                "AccountTool",
                                "unlockAccount",
                                "WRITE",
                                Map.of("employeeId", "U5001"),
                                RiskLevel.MEDIUM,
                                false,
                                "执行账号解锁"),
                        new PlanStepRequest(
                                2,
                                "NotificationTool",
                                "sendNotification",
                                "WRITE",
                                Map.of("recipientId", "U5001", "message", "账号已解锁"),
                                RiskLevel.LOW,
                                false,
                                "通知用户处理结果"))));

        assertThat(toolTaskProcessor.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(toolCallLogMapper.findByTicketIdOrderByCreatedAtAsc(created.ticketId()))
                .extracting("status")
                .contains(ToolCallStatus.QUEUED, ToolCallStatus.SUCCESS);
        assertThat(idempotencyRecordMapper.findByIdemKey("idem:unlockAccount:" + created.ticketId() + ":U5001")).isNotNull();
    }
}
