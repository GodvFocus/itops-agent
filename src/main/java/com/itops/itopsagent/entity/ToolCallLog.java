package com.itops.itopsagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.itops.itopsagent.entity.enums.ToolActionType;
import com.itops.itopsagent.entity.enums.ToolCallStatus;
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
@TableName("tool_call_log")
public class ToolCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("ticket_id")
    private String ticketId;

    @TableField("plan_id")
    private String planId;

    @TableField("step_no")
    private Integer stepNo;

    @TableField("tool_name")
    private String toolName;

    @TableField("action_name")
    private String actionName;

    @TableField("action_type")
    private ToolActionType actionType;

    @TableField("idem_key")
    private String idemKey;

    private ToolCallStatus status;

    /** decision 保留 Harness 门禁结论，便于区分拒绝、审批和真正执行。 */
    private String decision;

    @TableField("request_json")
    private String requestJson;

    @TableField("response_json")
    private String responseJson;

    @TableField("error_message")
    private String errorMessage;

    @TableField("attempt_no")
    private Integer attemptNo;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;
}
