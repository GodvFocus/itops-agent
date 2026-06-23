package com.itops.itopsagent.service.harness;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MockEnterpriseToolGateway implements ToolGateway {

    @Override
    public Map<String, Object> execute(ToolExecutionTask task) {
        maybeSimulateFailure(task);
        return switch (task.tool() + "." + task.action()) {
            case "AccountTool.queryAccountStatus" -> Map.of(
                    "employeeId", task.params().get("employeeId"),
                    "accountStatus", defaultValue(task.params().get("accountStatus"), "LOCKED"));
            case "AccountTool.unlockAccount" -> Map.of(
                    "employeeId", task.params().get("employeeId"),
                    "previousStatus", defaultValue(task.params().get("accountStatus"), "LOCKED"),
                    "currentStatus", "UNLOCKED");
            case "VpnTool.queryVpnPermission" -> Map.of(
                    "employeeId", task.params().get("employeeId"),
                    "vpnGranted", defaultValue(task.params().get("vpnGranted"), true));
            case "VpnTool.queryVpnLoginFailure" -> Map.of(
                    "employeeId", task.params().get("employeeId"),
                    "failureCount", defaultValue(task.params().get("failureCount"), 3));
            case "MfaTool.queryMfaStatus" -> Map.of(
                    "employeeId", task.params().get("employeeId"),
                    "bindingStatus", defaultValue(task.params().get("bindingStatus"), "BOUND"));
            case "MfaTool.resetMfaBindingRequest" -> Map.of(
                    "employeeId", task.params().get("employeeId"),
                    "requestStatus", "PENDING_APPROVAL");
            case "PermissionTool.queryPermission" -> Map.of(
                    "employeeId", task.params().get("employeeId"),
                    "targetSystem", task.params().get("targetSystem"),
                    "permissionLevel", defaultValue(task.params().get("currentPermission"), "NONE"));
            case "PermissionTool.grantPermission" -> Map.of(
                    "employeeId", task.params().get("employeeId"),
                    "targetSystem", task.params().get("targetSystem"),
                    "permissionLevel", task.params().get("permissionLevel"),
                    "granted", true);
            case "NotificationTool.sendNotification" -> {
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("recipientId", task.params().get("recipientId"));
                response.put("message", task.params().get("message"));
                response.put("delivered", true);
                yield response;
            }
            default -> throw new IllegalArgumentException("Unsupported tool task: " + task.tool() + "." + task.action());
        };
    }

    private void maybeSimulateFailure(ToolExecutionTask task) {
        Object simulateFailures = task.params().get("simulateFailures");
        if (simulateFailures == null) {
            return;
        }
        int failures = Integer.parseInt(String.valueOf(simulateFailures));
        if (task.attemptNo() <= failures) {
            throw new IllegalStateException("模拟企业系统暂时失败");
        }
    }

    private Object defaultValue(Object value, Object defaultValue) {
        return value == null ? defaultValue : value;
    }
}
