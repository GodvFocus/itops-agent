package com.itops.itopsagent.service;

import com.itops.itopsagent.dto.TicketTimelineResponse;

public interface TicketTimelineService {

    TicketTimelineResponse getTimeline(String ticketId);
}
