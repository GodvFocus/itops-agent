package com.itops.itopsagent.entity;

import com.itops.itopsagent.entity.enums.RiskLevel;
import com.itops.itopsagent.entity.enums.TicketIntent;
import com.itops.itopsagent.entity.enums.TicketPriority;
import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
@Entity
@Table(name = "ticket")
public class Ticket {

    /** 工单唯一标识。 */
    @Id
    @Column(name = "ticket_id", nullable = false, length = 32)
    private String ticketId;

    /** 工单标题，通常用于列表页展示。 */
    @Column(nullable = false, length = 200)
    private String title;

    /** 工单详细描述，记录用户提交的问题背景。 */
    @Column(nullable = false, length = 4000)
    private String description;

    /** 工单创建人标识。 */
    @Column(name = "creator_id", nullable = false, length = 64)
    private String creatorId;

    /** 工单创建人角色。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "creator_role", nullable = false, length = 32)
    private UserRole creatorRole;

    /** 当前工单状态，由状态机统一管理流转。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TicketStatus status;

    /** 当前识别到的工单意图，Phase 1 默认使用 UNKNOWN。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TicketIntent intent;

    /** 工单优先级。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TicketPriority priority;

    /** 风险等级，为后续审批与自动执行做准备。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 16)
    private RiskLevel riskLevel;

    /** 当前指派处理人。 */
    @Column(name = "assigned_to", length = 64)
    private String assignedTo;

    /** 乐观锁版本号，用于并发更新保护。 */
    @Version
    @Column(nullable = false)
    private long version;

    /** 工单创建时间。 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 最近一次更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 工单进入关闭态时的时间。 */
    @Column(name = "closed_at")
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
