package com.itops.itopsagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.itops.itopsagent.entity.enums.ConversationMessageType;
import com.itops.itopsagent.entity.enums.ConversationRole;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@TableName("conversation_message")
public class ConversationMessage {

    /** 对话消息主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联工单 ID。 */
    @TableField("ticket_id")
    private String ticketId;

    /** 消息发送方角色，用于前端区分气泡和审计来源。 */
    private ConversationRole role;

    /** 原始消息内容，保留前端展示和后续压缩基础。 */
    private String content;

    /** 消息类型，用于区分初始描述、追问与总结。 */
    @TableField("message_type")
    private ConversationMessageType messageType;

    /** 消息写入时间。 */
    @TableField("created_at")
    private Instant createdAt;
}
