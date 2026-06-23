package com.itops.itopsagent.harness;

import static org.assertj.core.api.Assertions.assertThat;

import com.itops.itopsagent.entity.enums.ToolActionType;
import com.itops.itopsagent.service.harness.MockEnterpriseToolGateway;
import com.itops.itopsagent.service.harness.ToolExecutionTask;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolGatewayTest {

    private final MockEnterpriseToolGateway gateway = new MockEnterpriseToolGateway();

    @Test
    void shouldExecuteReadAndUnlockTools() {
        Map<String, Object> queryResult = gateway.execute(new ToolExecutionTask(
                "T-001",
                "plan-001",
                1,
                "AccountTool",
                "queryAccountStatus",
                ToolActionType.READ,
                Map.of("employeeId", "U1001"),
                null,
                1));
        Map<String, Object> unlockResult = gateway.execute(new ToolExecutionTask(
                "T-001",
                "plan-001",
                2,
                "AccountTool",
                "unlockAccount",
                ToolActionType.WRITE,
                Map.of("employeeId", "U1001"),
                "idem:unlockAccount:T-001:U1001",
                1));

        assertThat(queryResult.get("accountStatus")).isEqualTo("LOCKED");
        assertThat(unlockResult.get("currentStatus")).isEqualTo("UNLOCKED");
    }
}
