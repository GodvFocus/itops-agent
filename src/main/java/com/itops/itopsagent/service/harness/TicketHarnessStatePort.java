package com.itops.itopsagent.service.harness;

import com.itops.itopsagent.entity.Ticket;
import com.itops.itopsagent.entity.TicketStatusHistory;
import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.UserRole;
import com.itops.itopsagent.mapper.TicketStatusHistoryMapper;
import com.itops.itopsagent.mapper.TicketMapper;
import com.itops.itopsagent.service.AuditLogService;
import com.itops.itopsagent.service.TicketStateMachineService;
import com.itops.itopsagent.utils.exception.TicketConflictException;
import com.itops.itopsagent.utils.exception.TicketNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TicketHarnessStatePort implements HarnessTicketStatePort {

    private final TicketMapper ticketMapper;
    private final TicketStatusHistoryMapper ticketStatusHistoryMapper;
    private final TicketStateMachineService ticketStateMachineService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    public TicketStatus getCurrentStatus(String ticketId) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        return ticket == null ? null : ticket.getStatus();
    }

    @Override
    public void transition(String ticketId, TicketStatus targetStatus, String comment) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new TicketNotFoundException(ticketId);
        }
        if (ticket.getStatus() == targetStatus) {
            return;
        }
        UserRole actorRole = resolveActorRole(ticket.getStatus(), targetStatus);
        String actorId = actorRole == UserRole.APPROVER ? "APPROVER" : "HARNESS";
        ticketStateMachineService.assertTransitionAllowed(ticket.getStatus(), targetStatus, actorRole);
        try {
            Instant now = Instant.now(clock);
            TicketStatus currentStatus = ticket.getStatus();
            ticket.transitionTo(targetStatus, now);
            int updatedRows = ticketMapper.updateById(ticket);
            if (updatedRows == 0) {
                throw new TicketConflictException(ticketId);
            }
            ticketStatusHistoryMapper.insert(TicketStatusHistory.builder()
                    .ticketId(ticketId)
                    .fromStatus(currentStatus)
                    .toStatus(targetStatus)
                    .actorId(actorId)
                    .actorRole(actorRole)
                    .comment(comment)
                    .createdAt(now)
                    .build());
            auditLogService.record(
                    ticketId,
                    "SYSTEM",
                    actorId,
                    "TICKET_STATUS_CHANGED",
                    "TICKET",
                    ticketId,
                    Map.of(
                            "fromStatus", currentStatus.name(),
                            "toStatus", targetStatus.name(),
                            "actorRole", actorRole.name()));
        } catch (TicketConflictException exception) {
            Ticket refreshed = ticketMapper.selectById(ticketId);
            if (refreshed != null && refreshed.getStatus() == targetStatus) {
                return;
            }
            throw exception;
        }
    }

    private UserRole resolveActorRole(TicketStatus currentStatus, TicketStatus targetStatus) {
        if (currentStatus == TicketStatus.WAITING_APPROVAL
                && (targetStatus == TicketStatus.EXECUTING || targetStatus == TicketStatus.ESCALATED)) {
            return UserRole.APPROVER;
        }
        return UserRole.IT_ENGINEER;
    }
}
