package com.itops.itopsagent.service.harness;

import com.itops.itopsagent.dto.TransitionTicketStatusRequest;
import com.itops.itopsagent.entity.Ticket;
import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.UserRole;
import com.itops.itopsagent.mapper.TicketMapper;
import com.itops.itopsagent.service.TicketService;
import com.itops.itopsagent.utils.exception.TicketConflictException;
import com.itops.itopsagent.utils.exception.TicketNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TicketHarnessStatePort implements HarnessTicketStatePort {

    private final TicketMapper ticketMapper;
    private final TicketService ticketService;

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
        try {
            ticketService.transitionStatus(
                    ticketId,
                    new TransitionTicketStatusRequest(targetStatus, "HARNESS", UserRole.IT_ENGINEER, ticket.getVersion(), comment));
        } catch (TicketConflictException exception) {
            Ticket refreshed = ticketMapper.selectById(ticketId);
            if (refreshed != null && refreshed.getStatus() == targetStatus) {
                return;
            }
            throw exception;
        }
    }
}
