package com.itops.itopsagent.service;

import com.itops.itopsagent.dto.CreateTicketRequest;
import com.itops.itopsagent.dto.TicketResponse;
import com.itops.itopsagent.dto.TicketSummaryResponse;
import com.itops.itopsagent.dto.TransitionTicketStatusRequest;
import java.util.List;

public interface TicketService {

    TicketResponse createTicket(CreateTicketRequest request);

    TicketResponse getTicket(String ticketId);

    List<TicketSummaryResponse> listTickets();

    TicketResponse transitionStatus(String ticketId, TransitionTicketStatusRequest request);
}
