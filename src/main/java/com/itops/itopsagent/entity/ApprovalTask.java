package com.itops.itopsagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.itops.itopsagent.entity.enums.ApprovalStatus;
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
@TableName("approval_task")
public class ApprovalTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("approval_id")
    private String approvalId;

    @TableField("ticket_id")
    private String ticketId;

    @TableField("plan_id")
    private String planId;

    private ApprovalStatus status;

    @TableField("approval_type")
    private String approvalType;

    @TableField("requested_by")
    private String requestedBy;

    @TableField("requested_reason")
    private String requestedReason;

    @TableField("plan_json")
    private String planJson;

    @TableField("context_json")
    private String contextJson;

    @TableField("approver_id")
    private String approverId;

    @TableField("approver_comment")
    private String approverComment;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("decided_at")
    private Instant decidedAt;

    @TableField("updated_at")
    private Instant updatedAt;
}
