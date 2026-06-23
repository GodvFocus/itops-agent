package com.itops.itopsagent.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.itops.itopsagent.dto.CreateTicketRequest;
import com.itops.itopsagent.dto.TicketResponse;
import com.itops.itopsagent.dto.TransitionTicketStatusRequest;
import com.itops.itopsagent.entity.AuditLog;
import com.itops.itopsagent.entity.enums.TicketPriority;
import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.UserRole;
import com.itops.itopsagent.mapper.AuditLogMapper;
import com.itops.itopsagent.mapper.AgentStepLogMapper;
import com.itops.itopsagent.mapper.ConversationMessageMapper;
import com.itops.itopsagent.mapper.TicketMapper;
import com.itops.itopsagent.mapper.TicketContextMapper;
import com.itops.itopsagent.mapper.TicketStatusHistoryMapper;
import com.itops.itopsagent.service.TicketService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AuditLogPersistenceTest {

    @Autowired
    private TicketService ticketService;

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

    @BeforeEach
    void setUp() {
        auditLogMapper.deleteAllRecords();
        agentStepLogMapper.deleteAllRecords();
        conversationMessageMapper.deleteAllRecords();
        ticketContextMapper.deleteAllRecords();
        ticketStatusHistoryMapper.deleteAllRecords();
        ticketMapper.deleteAllRecords();
    }

    @Test
    void shouldPersistAuditAndStatusHistoryRecords() {
        TicketResponse created = ticketService.createTicket(new CreateTicketRequest(
                "Password reset",
                "Account is locked",
                "U1008",
                UserRole.EMPLOYEE,
                TicketPriority.MEDIUM));

        ticketService.transitionStatus(
                created.ticketId(),
                new TransitionTicketStatusRequest(TicketStatus.TRIAGING, "E3001", UserRole.IT_ENGINEER, created.version(), "start triage"));

        List<AuditLog> logs = auditLogMapper.findByTicketIdOrderByCreatedAtAsc(created.ticketId());

        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getAction()).isEqualTo("TICKET_CREATED");
        assertThat(logs.get(1).getAction()).isEqualTo("TICKET_STATUS_CHANGED");
        assertThat(ticketStatusHistoryMapper.findByTicketIdOrderByCreatedAtAsc(created.ticketId())).hasSize(2);
    }
}
