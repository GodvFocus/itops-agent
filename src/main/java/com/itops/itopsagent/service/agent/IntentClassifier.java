package com.itops.itopsagent.service.agent;

import com.itops.itopsagent.entity.enums.TicketIntent;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class IntentClassifier {

    private final AgentStructuredOutputValidator validator;

    public IntentClassifier(AgentStructuredOutputValidator validator) {
        this.validator = validator;
    }

    public IntentClassificationResult classify(AgentContextSnapshot snapshot) {
        String text = buildText(snapshot).toLowerCase(Locale.ROOT);
        TicketIntent intent;
        double confidence;
        String reasoning;
        if (containsAny(text, "vpn", "forticlient", "virtual private network")) {
            intent = TicketIntent.VPN_CONNECTION_ISSUE;
            confidence = 0.97;
            reasoning = "命中 VPN 相关关键词";
        } else if (containsAny(text, "权限", "grant access", "permission", "开通", "访问权限", "只读", "管理员权限", "read only")) {
            intent = TicketIntent.PERMISSION_REQUEST;
            confidence = 0.95;
            reasoning = "命中权限申请关键词";
        } else if (containsAny(text, "账号", "账户", "登录", "login", "sign in", "密码", "锁定", "locked", "无法访问")) {
            intent = TicketIntent.ACCOUNT_LOGIN_ISSUE;
            confidence = 0.91;
            reasoning = "命中登录异常关键词";
        } else {
            intent = TicketIntent.UNKNOWN;
            confidence = 0.52;
            reasoning = "未命中 MVP 支持范围内的意图特征";
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("intent", intent.name());
        payload.put("confidence", confidence);
        payload.put("reasoning", reasoning);
        return validator.validateIntentPayload(payload);
    }

    private String buildText(AgentContextSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        builder.append(snapshot.ticketFacts().getOrDefault("title", "")).append('\n');
        builder.append(snapshot.ticketFacts().getOrDefault("description", "")).append('\n');
        snapshot.recentMessages().forEach(message -> builder.append(message.getOrDefault("content", "")).append('\n'));
        return builder.toString();
    }

    private boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
