package com.itops.itopsagent.service.harness;

import com.itops.itopsagent.entity.enums.RiskLevel;
import com.itops.itopsagent.entity.enums.ToolActionType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

@Service
public class ToolRegistryService {

    private static final Path TOOL_REGISTRY_PATH = Path.of("docs", "itops_agent_codex_task_pack", "contracts", "tool_registry.yaml");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^{}]+)}");
    private volatile Map<String, ToolRegistryEntry> cachedRegistry;

    public Optional<ToolRegistryEntry> find(String tool, String action) {
        return Optional.ofNullable(loadRegistry().get(key(tool, action)));
    }

    public String buildIdemKey(ToolRegistryEntry entry, String ticketId, String planId, Integer stepNo, Map<String, Object> params) {
        if (entry.actionType() != ToolActionType.WRITE) {
            return null;
        }
        String pattern = entry.idempotencyKeyPattern();
        if (pattern == null || pattern.isBlank()) {
            // 部分低风险写操作没有显式模板时，退回到稳定的保守兜底键，避免遗漏幂等保护。
            pattern = "idem:%s:%s:{ticketId}:{planId}:{stepNo}".formatted(entry.tool(), entry.action());
        }
        Map<String, Object> values = new LinkedHashMap<>(params);
        values.put("ticketId", ticketId);
        values.put("planId", planId);
        values.put("stepNo", stepNo);
        String resolved = pattern;
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(pattern);
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            Object value = values.get(placeholder);
            if (value == null || String.valueOf(value).isBlank()) {
                return null;
            }
            resolved = resolved.replace("{" + placeholder + "}", sanitizeValue(value));
        }
        return resolved;
    }

    private Map<String, ToolRegistryEntry> loadRegistry() {
        Map<String, ToolRegistryEntry> existing = cachedRegistry;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (cachedRegistry != null) {
                return cachedRegistry;
            }
            cachedRegistry = readRegistry();
            return cachedRegistry;
        }
    }

    private Map<String, ToolRegistryEntry> readRegistry() {
        try (InputStream inputStream = Files.newInputStream(TOOL_REGISTRY_PATH)) {
            Yaml yaml = new Yaml();
            @SuppressWarnings("unchecked")
            Map<String, Object> root = yaml.load(inputStream);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) root.getOrDefault("tools", List.of());
            Map<String, ToolRegistryEntry> registry = new LinkedHashMap<>();
            for (Map<String, Object> tool : tools) {
                ToolRegistryEntry entry = toEntry(tool);
                registry.put(key(entry.tool(), entry.action()), entry);
            }
            return registry;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read tool registry contract", exception);
        }
    }

    private ToolRegistryEntry toEntry(Map<String, Object> raw) {
        @SuppressWarnings("unchecked")
        List<String> requiredParams = (List<String>) raw.getOrDefault("requiredParams", List.of());
        return new ToolRegistryEntry(
                String.valueOf(raw.get("tool")),
                String.valueOf(raw.get("action")),
                ToolActionType.valueOf(String.valueOf(raw.get("actionType"))),
                RiskLevel.valueOf(String.valueOf(raw.get("defaultRisk"))),
                requiredParams,
                toApprovalRequirement(raw.get("approvalRequired")),
                raw.get("idempotencyKeyPattern") == null ? null : String.valueOf(raw.get("idempotencyKeyPattern")));
    }

    private ApprovalRequirement toApprovalRequirement(Object rawValue) {
        if (rawValue == null) {
            return ApprovalRequirement.NONE;
        }
        String value = String.valueOf(rawValue);
        if ("true".equalsIgnoreCase(value)) {
            return ApprovalRequirement.REQUIRED;
        }
        if ("conditional".equalsIgnoreCase(value)) {
            return ApprovalRequirement.CONDITIONAL;
        }
        return ApprovalRequirement.NONE;
    }

    private String sanitizeValue(Object value) {
        return String.valueOf(value).trim().replaceAll("[^a-zA-Z0-9:_-]", "_");
    }

    private String key(String tool, String action) {
        return tool + "." + action;
    }
}
