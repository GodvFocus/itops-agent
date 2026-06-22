package com.itops.itopsagent.utils.exception;

import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.UserRole;

public class TicketTransitionForbiddenException extends RuntimeException {

    public TicketTransitionForbiddenException(UserRole actorRole, TicketStatus currentStatus, TicketStatus targetStatus) {
        super("Role " + actorRole + " cannot move ticket from " + currentStatus + " to " + targetStatus);
    }
}
