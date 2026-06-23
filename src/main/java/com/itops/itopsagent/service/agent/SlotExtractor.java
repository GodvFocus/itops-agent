package com.itops.itopsagent.service.agent;

import com.itops.itopsagent.entity.enums.TicketIntent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SlotExtractor {

    private static final Pattern EMPLOYEE_ID_PATTERN = Pattern.compile("(?i)\\b([A-Z]\\d{4,})\\b");
    private static final Pattern ERROR_PATTERN = Pattern.compile("(提示|报错|error|显示)[:：\\s]*([^，。；;\\n]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION_PATTERN = Pattern.compile("(([0-9一二两三四五六七八九十]+)\\s*(天|周|个月|月|小时))|(长期)|(永久)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TARGET_SYSTEM_PATTERN = Pattern.compile("(OA|ERP|CRM|SAP|JIRA|GITLAB|邮箱|EMAIL|财务系统|HR系统|BI)", Pattern.CASE_INSENSITIVE);

    private final AgentStructuredOutputValidator validator;

    public SlotExtractor(AgentStructuredOutputValidator validator) {
        this.validator = validator;
    }

    public SlotExtractionResult extract(AgentContextSnapshot snapshot, TicketIntent intent) {
        Map<String, Object> slots = new LinkedHashMap<>(snapshot.knownSlots());
        String text = buildText(snapshot);
        String lower = text.toLowerCase(Locale.ROOT);

        putIfAbsent(slots, "employeeId", firstGroup(EMPLOYEE_ID_PATTERN, text));
        putIfAbsent(slots, "targetSystem", normalizeSystem(firstGroup(TARGET_SYSTEM_PATTERN, text)));
        putIfAbsent(slots, "errorMessage", extractErrorMessage(text, lower));
        putIfAbsent(slots, "deviceType", extractDeviceType(lower));
        putIfAbsent(slots, "networkType", extractNetworkType(lower));
        putIfAbsent(slots, "mfaRecentlyChanged", extractMfaChanged(lower));
        putIfAbsent(slots, "permissionLevel", extractPermissionLevel(lower));
        putIfAbsent(slots, "reason", extractReason(text, lower));
        putIfAbsent(slots, "duration", firstGroup(DURATION_PATTERN, text));

        List<String> missingSlots = resolveMissingSlots(intent, slots);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("slots", filterSlotsForIntent(intent, slots));
        payload.put("missingSlots", missingSlots);
        payload.put("reasoning", "基于规则从标题、描述和最近对话中抽取槽位");
        return validator.validateSlotPayload(payload);
    }

    private Map<String, Object> filterSlotsForIntent(TicketIntent intent, Map<String, Object> slots) {
        List<String> allowList = switch (intent) {
            case ACCOUNT_LOGIN_ISSUE -> List.of("employeeId", "targetSystem", "errorMessage");
            case VPN_CONNECTION_ISSUE -> List.of("employeeId", "deviceType", "errorMessage", "networkType", "mfaRecentlyChanged");
            case PERMISSION_REQUEST -> List.of("employeeId", "targetSystem", "permissionLevel", "reason", "duration");
            case UNKNOWN -> List.of();
        };
        Map<String, Object> filtered = new LinkedHashMap<>();
        allowList.stream()
                .filter(slots::containsKey)
                .forEach(key -> filtered.put(key, slots.get(key)));
        return filtered;
    }

    private List<String> resolveMissingSlots(TicketIntent intent, Map<String, Object> slots) {
        List<String> required = switch (intent) {
            case ACCOUNT_LOGIN_ISSUE -> List.of("employeeId", "targetSystem");
            case VPN_CONNECTION_ISSUE -> List.of("employeeId", "deviceType", "errorMessage");
            case PERMISSION_REQUEST -> List.of("employeeId", "targetSystem", "permissionLevel", "reason", "duration");
            case UNKNOWN -> List.of();
        };
        return required.stream()
                .filter(key -> isEmpty(slots.get(key)))
                .toList();
    }

    private String buildText(AgentContextSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        builder.append(snapshot.ticketFacts().getOrDefault("title", "")).append('\n');
        builder.append(snapshot.ticketFacts().getOrDefault("description", "")).append('\n');
        snapshot.recentMessages().forEach(message -> builder.append(message.getOrDefault("content", "")).append('\n'));
        return builder.toString();
    }

    private String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            for (int index = 1; index <= matcher.groupCount(); index++) {
                if (matcher.group(index) != null) {
                    return matcher.group(index).trim();
                }
            }
        }
        return null;
    }

    private String extractErrorMessage(String text, String lower) {
        String matched = firstGroup(ERROR_PATTERN, text);
        if (matched != null) {
            return matched;
        }
        if (lower.contains("账号已锁定")) {
            return "账号已锁定";
        }
        if (lower.contains("认证失败")) {
            return "认证失败";
        }
        if (lower.contains("invalid credentials")) {
            return "invalid credentials";
        }
        return null;
    }

    private String normalizeSystem(String value) {
        if (value == null) {
            return null;
        }
        return value.toUpperCase(Locale.ROOT).replace("邮箱", "EMAIL");
    }

    private String extractDeviceType(String lower) {
        if (containsAny(lower, "windows", "win11", "win10", "电脑", "笔记本")) {
            return "WINDOWS";
        }
        if (containsAny(lower, "macbook", "mac os", "macos", "mac")) {
            return "MAC";
        }
        if (containsAny(lower, "iphone", "ios")) {
            return "IOS";
        }
        if (containsAny(lower, "android", "安卓")) {
            return "ANDROID";
        }
        if (containsAny(lower, "手机")) {
            return "MOBILE";
        }
        return null;
    }

    private String extractNetworkType(String lower) {
        if (containsAny(lower, "内网", "公司网络", "办公网")) {
            return "CORPORATE";
        }
        if (containsAny(lower, "家庭宽带", "家里", "家用")) {
            return "HOME";
        }
        if (containsAny(lower, "热点")) {
            return "HOTSPOT";
        }
        if (containsAny(lower, "公共wifi", "公共 wifi")) {
            return "PUBLIC_WIFI";
        }
        if (containsAny(lower, "外网")) {
            return "INTERNET";
        }
        return null;
    }

    private Boolean extractMfaChanged(String lower) {
        if (containsAny(lower, "换绑", "更换手机", "重置 mfa", "重置mfa", "mfa recently changed", "刚换手机")) {
            return Boolean.TRUE;
        }
        if (containsAny(lower, "没有改 mfa", "未改mfa", "mfa 没变")) {
            return Boolean.FALSE;
        }
        return null;
    }

    private String extractPermissionLevel(String lower) {
        if (containsAny(lower, "管理员", "admin", "administrator")) {
            return "ADMIN";
        }
        if (containsAny(lower, "写入", "编辑", "read write", "read-write")) {
            return "READ_WRITE";
        }
        if (containsAny(lower, "只读", "read only", "read-only", "查看")) {
            return "READ_ONLY";
        }
        return null;
    }

    private String extractReason(String text, String lower) {
        String normalized = text.replace('\n', ' ');
        List<String> markers = List.of("用于", "因为", "原因是");
        for (String marker : markers) {
            int start = lower.indexOf(marker);
            if (start >= 0) {
                String segment = normalized.substring(start + marker.length()).trim();
                int end = findSentenceEnd(segment);
                return segment.substring(0, end).trim();
            }
        }
        return null;
    }

    private int findSentenceEnd(String segment) {
        List<Integer> indexes = new ArrayList<>();
        for (String delimiter : List.of("。", "，", ",", ";", "；")) {
            int index = segment.indexOf(delimiter);
            if (index >= 0) {
                indexes.add(index);
            }
        }
        return indexes.stream().filter(Objects::nonNull).min(Integer::compareTo).orElse(segment.length());
    }

    private void putIfAbsent(Map<String, Object> slots, String key, Object value) {
        if (value == null) {
            return;
        }
        if (isEmpty(slots.get(key))) {
            slots.put(key, value);
        }
    }

    private boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean isEmpty(Object value) {
        return value == null || (value instanceof String text && text.trim().isEmpty());
    }
}
