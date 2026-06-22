package com.itops.itopsagent.ticket.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.UserRole;
import com.itops.itopsagent.service.impl.TicketStateMachineServiceImpl;
import com.itops.itopsagent.utils.exception.InvalidTicketStateTransitionException;
import com.itops.itopsagent.utils.exception.TicketTransitionForbiddenException;
import org.junit.jupiter.api.Test;

class TicketStateMachineTest {

    private final TicketStateMachineServiceImpl ticketStateMachine = new TicketStateMachineServiceImpl();

    @Test
    void shouldAllowConfiguredHappyPathTransitions() {
        assertDoesNotThrow(() -> ticketStateMachine.assertTransitionAllowed(TicketStatus.NEW, TicketStatus.TRIAGING, UserRole.IT_ENGINEER));
        assertDoesNotThrow(() -> ticketStateMachine.assertTransitionAllowed(TicketStatus.PLAN_VALIDATING, TicketStatus.WAITING_APPROVAL, UserRole.IT_ENGINEER));
        assertDoesNotThrow(() -> ticketStateMachine.assertTransitionAllowed(TicketStatus.WAITING_APPROVAL, TicketStatus.EXECUTING, UserRole.APPROVER));
        assertDoesNotThrow(() -> ticketStateMachine.assertTransitionAllowed(TicketStatus.WAITING_USER_CONFIRM, TicketStatus.RESOLVED, UserRole.EMPLOYEE));
        assertDoesNotThrow(() -> ticketStateMachine.assertTransitionAllowed(TicketStatus.RESOLVED, TicketStatus.CLOSED, UserRole.IT_ENGINEER));
    }

    @Test
    void shouldRejectClosedTicketReturningToExecuting() {
        assertThrows(
                InvalidTicketStateTransitionException.class,
                () -> ticketStateMachine.assertTransitionAllowed(TicketStatus.CLOSED, TicketStatus.EXECUTING, UserRole.ADMIN));
    }

    @Test
    void shouldRejectWaitingApprovalDirectlyClosed() {
        assertThrows(
                InvalidTicketStateTransitionException.class,
                () -> ticketStateMachine.assertTransitionAllowed(TicketStatus.WAITING_APPROVAL, TicketStatus.CLOSED, UserRole.ADMIN));
    }

    @Test
    void shouldEnforceRoleRestrictions() {
        assertThrows(
                TicketTransitionForbiddenException.class,
                () -> ticketStateMachine.assertTransitionAllowed(TicketStatus.NEW, TicketStatus.TRIAGING, UserRole.EMPLOYEE));
    }
}
