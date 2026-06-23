package com.itops.itopsagent.service;

import com.itops.itopsagent.dto.TicketContextResponse;
import com.itops.itopsagent.dto.UpdateAgentContextRequest;

public interface TicketContextService {

    TicketContextResponse getContext(String ticketId);

    TicketContextResponse saveContext(String ticketId, UpdateAgentContextRequest request);
}
