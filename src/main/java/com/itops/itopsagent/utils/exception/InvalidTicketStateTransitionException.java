package com.itops.itopsagent.utils.exception;

import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.UserRole;

public class InvalidTicketStateTransitionException extends RuntimeException {

    public InvalidTicketStateTransitionException(TicketStatus currentStatus, TicketStatus targetStatus, UserRole actorRole) {
        super("Transition not allowed from " + currentStatus + " to " + targetStatus + " for role " + actorRole);
    }
}
