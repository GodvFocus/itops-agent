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
import com.itops.itopsagent.mapper.AgentStepLogMapper;
import com.itops.itopsagent.mapper.ConversationMessageMapper;
import com.itops.itopsagent.mapper.TicketMapper;
import com.itops.itopsagent.mapper.TicketContextMapper;
import com.itops.itopsagent.mapper.TicketStatusHistoryMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    @Autowired
    private AgentStepLogMapper agentStepLogMapper;

    @Autowired
    private ConversationMessageMapper conversationMessageMapper;

    @Autowired
    private TicketContextMapper ticketContextMapper;

    @BeforeEach
    void setUp() {
        auditLogMapper.deleteAllRecords();
        agentStepLogMapper.deleteAllRecords();
        conversationMessageMapper.deleteAllRecords();
        ticketContextMapper.deleteAllRecords();
        ticketStatusHistoryMapper.deleteAllRecords();
        ticketMapper.deleteAllRecords();
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
                .andExpect(jsonPath("$[0].status", is("NEED_MORE_INFO")));

        mockMvc.perform(get("/api/tickets/{ticketId}", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId", is(ticketId)))
                .andExpect(jsonPath("$.creatorRole", is("EMPLOYEE")))
                .andExpect(jsonPath("$.intent", is("VPN_CONNECTION_ISSUE")))
                .andExpect(jsonPath("$.ticketContext.intent", is("VPN_CONNECTION_ISSUE")))
                .andExpect(jsonPath("$.statusHistory", hasSize(3)))
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
                .andExpect(jsonPath("$.message", is("Transition not allowed from NEED_MORE_INFO to CLOSED for role IT_ENGINEER")));
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
                .andExpect(jsonPath("$.message", is("Role EMPLOYEE cannot move ticket from NEED_MORE_INFO to TRIAGING")));
    }

    @Test
    void shouldAskForMissingEmployeeIdAndExposeAgentContext() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "OA 登录失败",
                                "description", "今天登录 OA 提示账号已锁定，帮我看看",
                                "creatorId", "U1009",
                                "creatorRole", "EMPLOYEE"))))
                .andExpect(status().isCreated())
                .andReturn();

        String ticketId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("ticketId").asText();

        mockMvc.perform(get("/api/tickets/{ticketId}", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent", is("ACCOUNT_LOGIN_ISSUE")))
                .andExpect(jsonPath("$.ticketContext.missingSlots", hasSize(1)))
                .andExpect(jsonPath("$.ticketContext.missingSlots[0]", is("employeeId")))
                .andExpect(jsonPath("$.conversationMessages[1].messageType", is("AGENT_FOLLOW_UP")));

        mockMvc.perform(get("/api/agent/context/{ticketId}", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.knownSlots.targetSystem", is("OA")))
                .andExpect(jsonPath("$.missingSlots[0]", is("employeeId")))
                .andExpect(jsonPath("$.currentNode", is("ASK_USER_FOR_MISSING_SLOTS")));
    }

    @Test
    void shouldUpdateContextAfterUserReplyAndCompleteUnderstanding() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "申请 ERP 只读权限",
                                "description", "需要申请 ERP 只读权限两周",
                                "creatorId", "U1010",
                                "creatorRole", "EMPLOYEE"))))
                .andExpect(status().isCreated())
                .andReturn();

        String ticketId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("ticketId").asText();

        mockMvc.perform(post("/api/tickets/{ticketId}/messages", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "我的员工编号是 U1010，原因是用于月底对账"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketContext.intent", is("PERMISSION_REQUEST")))
                .andExpect(jsonPath("$.ticketContext.missingSlots", hasSize(0)))
                .andExpect(jsonPath("$.conversationMessages[3].messageType", is("AGENT_SUMMARY")))
                .andExpect(jsonPath("$.agentStepLogs", hasSize(6)));
    }

    @Test
    void shouldMarkUnknownIssueForManualTakeover() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "会议室投影仪坏了",
                                "description", "会议室投影仪画面闪烁，还伴随奇怪噪音",
                                "creatorId", "U1011",
                                "creatorRole", "EMPLOYEE"))))
                .andExpect(status().isCreated())
                .andReturn();

        String ticketId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("ticketId").asText();

        mockMvc.perform(get("/api/tickets/{ticketId}", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent", is("UNKNOWN")))
                .andExpect(jsonPath("$.ticketContext.lastAgentStep", is("ESCALATE_TO_HUMAN")))
                .andExpect(jsonPath("$.conversationMessages[1].messageType", is("AGENT_ESCALATION")));
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
