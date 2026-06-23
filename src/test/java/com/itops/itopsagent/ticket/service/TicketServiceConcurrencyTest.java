package com.itops.itopsagent.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.itops.itopsagent.dto.CreateTicketRequest;
import com.itops.itopsagent.dto.TicketResponse;
import com.itops.itopsagent.dto.TransitionTicketStatusRequest;
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
import com.itops.itopsagent.utils.exception.TicketConflictException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TicketServiceConcurrencyTest {

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

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        auditLogMapper.deleteAllRecords();
        agentStepLogMapper.deleteAllRecords();
        conversationMessageMapper.deleteAllRecords();
        ticketContextMapper.deleteAllRecords();
        ticketStatusHistoryMapper.deleteAllRecords();
        ticketMapper.deleteAllRecords();
        executorService = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    void shouldRejectSecondUpdateWhenVersionIsStale() throws Exception {
        TicketResponse created = ticketService.createTicket(new CreateTicketRequest(
                "VPN issue",
                "VPN connection keeps failing",
                "U1001",
                UserRole.EMPLOYEE,
                TicketPriority.MEDIUM));

        TicketResponse triaged = ticketService.transitionStatus(
                created.ticketId(),
                new TransitionTicketStatusRequest(TicketStatus.TRIAGING, "E2001", UserRole.IT_ENGINEER, created.version(), "triage"));

        Callable<String> moveToPlanning = () -> {
            try {
                ticketService.transitionStatus(
                        created.ticketId(),
                        new TransitionTicketStatusRequest(TicketStatus.PLANNING, "E2001", UserRole.IT_ENGINEER, triaged.version(), "plan"));
                return "SUCCESS";
            } catch (TicketConflictException exception) {
                return "CONFLICT";
            }
        };

        List<Future<String>> futures = executorService.invokeAll(List.of(moveToPlanning, moveToPlanning));
        List<String> results = List.of(futures.get(0).get(), futures.get(1).get());

        assertThat(results).containsExactlyInAnyOrder("SUCCESS", "CONFLICT");
        assertThat(ticketService.getTicket(created.ticketId()).status()).isEqualTo(TicketStatus.PLANNING);
    }
}
