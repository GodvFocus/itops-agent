package com.itops.itopsagent.service;

import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.UserRole;

public interface TicketStateMachineService {

    void assertTransitionAllowed(TicketStatus currentStatus, TicketStatus targetStatus, UserRole actorRole);
}
