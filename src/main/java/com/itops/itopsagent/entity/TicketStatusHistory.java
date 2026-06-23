package com.itops.itopsagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.UserRole;
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
@TableName("ticket_status_history")
public class TicketStatusHistory {

    /** 状态历史记录主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的工单 ID。 */
    @TableField("ticket_id")
    private String ticketId;

    /** 变更前状态，创建工单时允许为空。 */
    @TableField("from_status")
    private TicketStatus fromStatus;

    /** 变更后状态。 */
    @TableField("to_status")
    private TicketStatus toStatus;

    /** 发起本次状态变更的操作者 ID。 */
    @TableField("actor_id")
    private String actorId;

    /** 发起本次状态变更的操作者角色。 */
    @TableField("actor_role")
    private UserRole actorRole;

    /** 本次状态变更备注。 */
    @TableField("comment_text")
    private String comment;

    /** 状态变更发生时间。 */
    @TableField("created_at")
    private Instant createdAt;

}
