package com.itops.itopsagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.itops.itopsagent.entity.enums.RiskLevel;
import com.itops.itopsagent.entity.enums.TicketIntent;
import com.itops.itopsagent.entity.enums.TicketPriority;
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
@TableName("ticket")
public class Ticket {

    /** 工单唯一标识。 */
    @TableId(value = "ticket_id", type = IdType.INPUT)
    private String ticketId;

    /** 工单标题，通常用于列表页展示。 */
    private String title;

    /** 工单详细描述，记录用户提交的问题背景。 */
    private String description;

    /** 工单创建人标识。 */
    @TableField("creator_id")
    private String creatorId;

    /** 工单创建人角色。 */
    @TableField("creator_role")
    private UserRole creatorRole;

    /** 当前工单状态，由状态机统一管理流转。 */
    private TicketStatus status;

    /** 当前识别到的工单意图，Phase 1 默认使用 UNKNOWN。 */
    private TicketIntent intent;

    /** 工单优先级。 */
    private TicketPriority priority;

    /** 风险等级，为后续审批与自动执行做准备。 */
    @TableField("risk_level")
    private RiskLevel riskLevel;

    /** 当前指派处理人。 */
    @TableField("assigned_to")
    private String assignedTo;

    /** 乐观锁版本号，用于并发更新保护。 */
    @Version
    private long version;

    /** 工单创建时间。 */
    @TableField("created_at")
    private Instant createdAt;

    /** 最近一次更新时间。 */
    @TableField("updated_at")
    private Instant updatedAt;

    /** 工单进入关闭态时的时间。 */
    @TableField("closed_at")
    private Instant closedAt;

    /**
     * 更新工单状态。
     * 状态是否合法由外层状态机校验，这里只负责聚合自身字段同步更新。
     */
    public void transitionTo(TicketStatus nextStatus, Instant changedAt) {
        this.status = nextStatus;
        this.updatedAt = changedAt;
        // 只有进入最终关闭态时才写入关闭时间，避免中间态被错误标记为已关闭。
        this.closedAt = nextStatus == TicketStatus.CLOSED ? changedAt : this.closedAt;
    }
}
