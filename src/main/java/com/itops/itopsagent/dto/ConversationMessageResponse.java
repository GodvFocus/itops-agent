package com.itops.itopsagent.dto;

import com.itops.itopsagent.entity.enums.ConversationMessageType;
import com.itops.itopsagent.entity.enums.ConversationRole;
import java.time.Instant;

public record ConversationMessageResponse(
        /** 消息主键。 */
        Long id,
        /** 消息角色。 */
        ConversationRole role,
        /** 消息内容。 */
        String content,
        /** 消息类型。 */
        ConversationMessageType messageType,
        /** 创建时间。 */
        Instant createdAt) {
}
