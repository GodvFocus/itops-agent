package com.itops.itopsagent.ticket.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itops.itopsagent.dto.CreateTicketRequest;
import com.itops.itopsagent.dto.TransitionTicketStatusRequest;
import com.itops.itopsagent.entity.enums.TicketPriority;
import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.UserRole;
import com.itops.itopsagent.mapper.AuditLogMapper;
import com.itops.itopsagent.mapper.TicketMapper;
import com.itops.itopsagent.mapper.TicketStatusHistoryMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class TicketControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private TicketStatusHistoryMapper ticketStatusHistoryMapper;

    @Autowired
    private AuditLogMapper auditLogMapper;

    @BeforeEach
    void setUp() {
        auditLogMapper.deleteAll();
        ticketStatusHistoryMapper.deleteAll();
        ticketMapper.deleteAll();
    }

    @Test
    void shouldCreateListAndFetchTicketDetail() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTicketRequest(
                                "VPN unreachable",
                                "Cannot connect to company VPN",
                                "U1001",
                                UserRole.EMPLOYEE,
                                TicketPriority.HIGH))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("NEW")))
                .andReturn();

        String ticketId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("ticketId").asText();

        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ticketId", is(ticketId)))
                .andExpect(jsonPath("$[0].status", is("NEW")));

        mockMvc.perform(get("/api/tickets/{ticketId}", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId", is(ticketId)))
                .andExpect(jsonPath("$.creatorRole", is("EMPLOYEE")))
                .andExpect(jsonPath("$.statusHistory", hasSize(1)))
                .andExpect(jsonPath("$.statusHistory[0].toStatus", is("NEW")));
    }

    @Test
    void shouldRejectIllegalStatusTransition() throws Exception {
        String ticketId = createTicket();

        mockMvc.perform(post("/api/tickets/{ticketId}/status", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransitionTicketStatusRequest(
                                TicketStatus.CLOSED,
                                "U2001",
                                UserRole.IT_ENGINEER,
                                null,
                                "invalid close"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Transition not allowed from NEW to CLOSED for role IT_ENGINEER")));
    }

    @Test
    void shouldRejectForbiddenRoleTransition() throws Exception {
        String ticketId = createTicket();

        mockMvc.perform(post("/api/tickets/{ticketId}/status", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransitionTicketStatusRequest(
                                TicketStatus.TRIAGING,
                                "U1001",
                                UserRole.EMPLOYEE,
                                null,
                                "trying to triage"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Role EMPLOYEE cannot move ticket from NEW to TRIAGING")));
    }

    private String createTicket() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Need access",
                                "description", "Grant access to tool",
                                "creatorId", "U1002",
                                "creatorRole", "EMPLOYEE"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(createResult.getResponse().getContentAsString()).get("ticketId").asText();
    }
}
