package com.itops.itopsagent.dto;

public record AddConversationMessageRequest(
        /** 用户补充的消息内容。 */
        String content) {
}
