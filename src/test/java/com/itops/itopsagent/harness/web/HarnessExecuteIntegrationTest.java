package com.itops.itopsagent.harness.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itops.itopsagent.entity.Ticket;
import com.itops.itopsagent.entity.enums.RiskLevel;
import com.itops.itopsagent.entity.enums.TicketIntent;
import com.itops.itopsagent.entity.enums.TicketPriority;
import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.UserRole;
import com.itops.itopsagent.mapper.ApprovalTaskMapper;
import com.itops.itopsagent.mapper.TicketMapper;
import com.itops.itopsagent.mapper.TicketStatusHistoryMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HarnessExecuteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApprovalTaskMapper approvalTaskMapper;

    @Autowired
    private TicketStatusHistoryMapper ticketStatusHistoryMapper;

    @Autowired
    private TicketMapper ticketMapper;

    @BeforeEach
    void setUp() {
        approvalTaskMapper.deleteAllRecords();
        ticketStatusHistoryMapper.deleteAllRecords();
        ticketMapper.deleteAllRecords();
    }

    @Test
    void shouldCreateApprovalTaskWhenExecuteEndpointReturnsNeedApproval() throws Exception {
        Instant now = Instant.parse("2026-06-24T04:00:00Z");
        ticketMapper.insert(Ticket.builder()
                .ticketId("T-HARNESS-EXEC-001")
                .title("生产库管理员权限申请")
                .description("用于线上排障")
                .creatorId("U1001")
                .creatorRole(UserRole.EMPLOYEE)
                .status(TicketStatus.PLANNING)
                .intent(TicketIntent.PERMISSION_REQUEST)
                .priority(TicketPriority.HIGH)
                .riskLevel(RiskLevel.HIGH)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        mockMvc.perform(post("/api/harness/plans/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "planId", "plan-approval-api-001",
                                "ticketId", "T-HARNESS-EXEC-001",
                                "intent", "PERMISSION_REQUEST",
                                "riskLevel", "HIGH",
                                "goal", "申请生产数据库管理员权限",
                                "steps", List.of(
                                        Map.of(
                                                "stepNo", 1,
                                                "tool", "PermissionTool",
                                                "action", "grantPermission",
                                                "actionType", "WRITE",
                                                "params", Map.of(
                                                        "employeeId", "E10086",
                                                        "targetSystem", "production database",
                                                        "permissionLevel", "ADMIN"),
                                                "riskLevel", "MEDIUM",
                                                "requiredApproval", true,
                                                "reason", "高风险授权必须先审批"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("NEED_APPROVAL")))
                .andExpect(jsonPath("$.executionMode", is("PAUSE")));

        mockMvc.perform(get("/api/approvals").param("ticketId", "T-HARNESS-EXEC-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ticketId", is("T-HARNESS-EXEC-001")))
                .andExpect(jsonPath("$[0].planId", is("plan-approval-api-001")))
                .andExpect(jsonPath("$[0].status", is("PENDING")));

        mockMvc.perform(get("/api/tickets/{ticketId}", "T-HARNESS-EXEC-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("WAITING_APPROVAL")));
    }
}
