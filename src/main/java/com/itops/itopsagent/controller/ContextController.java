package com.itops.itopsagent.controller;

import com.itops.itopsagent.dto.AgentContextResponse;
import com.itops.itopsagent.dto.UpdateAgentContextRequest;
import com.itops.itopsagent.entity.Ticket;
import com.itops.itopsagent.mapper.TicketMapper;
import com.itops.itopsagent.service.ConversationMessageService;
import com.itops.itopsagent.service.TicketContextService;
import com.itops.itopsagent.service.agent.ContextBuilder;
import com.itops.itopsagent.utils.exception.TicketNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/context")
@RequiredArgsConstructor
public class ContextController {

    private final TicketMapper ticketMapper;
    private final TicketContextService ticketContextService;
    private final ConversationMessageService conversationMessageService;
    private final ContextBuilder contextBuilder;

    @GetMapping("/{ticketId}")
    public AgentContextResponse getContext(@PathVariable String ticketId) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new TicketNotFoundException(ticketId);
        }
        return contextBuilder.buildContextResponse(
                ticket,
                ticketContextService.getContext(ticketId),
                conversationMessageService.listRecentMessages(ticketId, 6));
    }

    @PostMapping("/{ticketId}")
    public com.itops.itopsagent.dto.TicketContextResponse saveContext(
            @PathVariable String ticketId, @RequestBody UpdateAgentContextRequest request) {
        return ticketContextService.saveContext(ticketId, request);
    }
}
