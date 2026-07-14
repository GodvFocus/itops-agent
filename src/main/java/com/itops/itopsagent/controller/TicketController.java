package com.itops.itopsagent.controller;

import com.itops.itopsagent.dto.AddConversationMessageRequest;
import com.itops.itopsagent.dto.CreateTicketRequest;
import com.itops.itopsagent.dto.CreateTicketResponse;
import com.itops.itopsagent.dto.TicketConfirmRequest;
import com.itops.itopsagent.dto.TicketResponse;
import com.itops.itopsagent.dto.TicketSummaryResponse;
import com.itops.itopsagent.dto.TicketTimelineResponse;
import com.itops.itopsagent.dto.TransitionTicketStatusRequest;
import com.itops.itopsagent.service.TicketService;
import com.itops.itopsagent.service.TicketTimelineService;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final TicketTimelineService ticketTimelineService;

    // 创建工单
    @PostMapping
    public ResponseEntity<CreateTicketResponse> createTicket(@RequestBody CreateTicketRequest request) {
        TicketResponse ticket = ticketService.createTicket(request);
        return ResponseEntity.created(URI.create("/api/tickets/" + ticket.ticketId()))
                .body(new CreateTicketResponse(ticket.ticketId(), ticket.status()));
    }

    // 获取工单详情
    @GetMapping("/{ticketId}")
    public TicketResponse getTicket(@PathVariable String ticketId) {
        return ticketService.getTicket(ticketId);
    }

    // 获取工单列表
    @GetMapping
    public List<TicketSummaryResponse> listTickets() {
        return ticketService.listTickets();
    }

    // 工单状态流转
    @PostMapping("/{ticketId}/status")
    public TicketResponse transitionStatus(@PathVariable String ticketId, @RequestBody TransitionTicketStatusRequest request) {
        return ticketService.transitionStatus(ticketId, request);
    }

    // 添加对话消息
    @PostMapping("/{ticketId}/messages")
    public TicketResponse appendMessage(@PathVariable String ticketId, @RequestBody AddConversationMessageRequest request) {
        return ticketService.appendMessage(ticketId, request);
    }

    // 确认工单
    @PostMapping("/{ticketId}/confirm")
    public TicketResponse confirmTicket(@PathVariable String ticketId, @RequestBody TicketConfirmRequest request) {
        return ticketService.confirmTicket(ticketId, request);
    }

    // 获取工单时间线
    @GetMapping("/{ticketId}/timeline")
    public TicketTimelineResponse getTimeline(@PathVariable String ticketId) {
        return ticketTimelineService.getTimeline(ticketId);
    }
}
