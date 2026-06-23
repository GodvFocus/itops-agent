package com.itops.itopsagent.service.harness;

import com.itops.itopsagent.entity.enums.TicketStatus;

public interface HarnessTicketStatePort {

    TicketStatus getCurrentStatus(String ticketId);

    void transition(String ticketId, TicketStatus targetStatus, String comment);
}
