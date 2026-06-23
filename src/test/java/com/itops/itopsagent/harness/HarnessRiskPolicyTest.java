package com.itops.itopsagent.harness;

import static org.assertj.core.api.Assertions.assertThat;

import com.itops.itopsagent.dto.CandidatePlanRequest;
import com.itops.itopsagent.dto.HarnessDecisionResponse;
import com.itops.itopsagent.dto.PlanStepRequest;
import com.itops.itopsagent.entity.enums.RiskLevel;
import com.itops.itopsagent.entity.enums.TicketIntent;
import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.harness.support.HarnessTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HarnessRiskPolicyTest {

    @Test
    void shouldRequireApprovalForProductionAdminPermission() {
        try (HarnessTestSupport support = new HarnessTestSupport()) {
            support.ticketPort.put("T-APPROVAL-001", TicketStatus.PLAN_VALIDATING);

            HarnessDecisionResponse response = support.harnessService.validatePlan(new CandidatePlanRequest(
                    "plan-approval-001",
                    "T-APPROVAL-001",
                    TicketIntent.PERMISSION_REQUEST,
                    RiskLevel.HIGH,
                    "申请生产管理员权限",
                    List.of(new PlanStepRequest(
                            1,
                            "PermissionTool",
                            "grantPermission",
                            "WRITE",
                            Map.of("employeeId", "U1001", "targetSystem", "prod-erp", "permissionLevel", "ADMIN"),
                            RiskLevel.MEDIUM,
                            false,
                            "申请生产管理员权限"))));

            assertThat(response.decision()).isEqualTo("NEED_APPROVAL");
            assertThat(response.executionMode()).isEqualTo("PAUSE");
            assertThat(response.approvedSteps()).hasSize(1);
        }
    }

    @Test
    void shouldRejectUnknownToolAndMissingParams() {
        try (HarnessTestSupport support = new HarnessTestSupport()) {
            support.ticketPort.put("T-REJECT-001", TicketStatus.PLAN_VALIDATING);

            HarnessDecisionResponse unknownTool = support.harnessService.validatePlan(new CandidatePlanRequest(
                    "plan-reject-001",
                    "T-REJECT-001",
                    TicketIntent.PERMISSION_REQUEST,
                    RiskLevel.MEDIUM,
                    "非法工具测试",
                    List.of(new PlanStepRequest(
                            1,
                            "UnknownTool",
                            "doSomething",
                            "WRITE",
                            Map.of("employeeId", "U1001"),
                            RiskLevel.MEDIUM,
                            false,
                            "非法工具测试"))));
            HarnessDecisionResponse missingParam = support.harnessService.validatePlan(new CandidatePlanRequest(
                    "plan-reject-002",
                    "T-REJECT-001",
                    TicketIntent.ACCOUNT_LOGIN_ISSUE,
                    RiskLevel.MEDIUM,
                    "缺参测试",
                    List.of(new PlanStepRequest(
                            1,
                            "AccountTool",
                            "unlockAccount",
                            "WRITE",
                            Map.of(),
                            RiskLevel.MEDIUM,
                            false,
                            "缺少 employeeId"))));

            assertThat(unknownTool.decision()).isEqualTo("REJECTED");
            assertThat(unknownTool.rejectedSteps()).hasSize(1);
            assertThat(missingParam.decision()).isEqualTo("REJECTED");
            assertThat(missingParam.rejectedSteps()).hasSize(1);
        }
    }

    @Test
    void shouldRejectDangerousDisabledAccountOperation() {
        try (HarnessTestSupport support = new HarnessTestSupport()) {
            support.ticketPort.put("T-DANGER-001", TicketStatus.PLAN_VALIDATING);

            HarnessDecisionResponse response = support.harnessService.validatePlan(new CandidatePlanRequest(
                    "plan-danger-001",
                    "T-DANGER-001",
                    TicketIntent.ACCOUNT_LOGIN_ISSUE,
                    RiskLevel.HIGH,
                    "禁用账号解锁",
                    List.of(new PlanStepRequest(
                            1,
                            "AccountTool",
                            "unlockAccount",
                            "WRITE",
                            Map.of("employeeId", "U1002", "accountStatus", "DISABLED"),
                            RiskLevel.HIGH,
                            false,
                            "危险操作测试"))));

            assertThat(response.decision()).isEqualTo("REJECTED");
            assertThat(response.rejectedSteps()).hasSize(1);
        }
    }
}
