package com.itops.itopsagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.itops.itopsagent.entity.enums.RiskLevel;
import com.itops.itopsagent.entity.enums.TicketIntent;
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
@TableName("ticket_context")
public class TicketContext {

    /** 工单上下文主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联工单 ID，一个工单只保留一份最新结构化上下文。 */
    @TableField("ticket_id")
    private String ticketId;

    /** 当前识别到的意图。 */
    private TicketIntent intent;

    /** 已抽取槽位 JSON，作为可回放的结构化事实快照。 */
    @TableField("slots_json")
    private String slotsJson;

    /** 当前仍缺失的槽位列表 JSON。 */
    @TableField("missing_slots_json")
    private String missingSlotsJson;

    /** 预留给后续 SOP 检索的结果占位，当前阶段固定为空数组。 */
    @TableField("matched_sop_ids_json")
    private String matchedSopIdsJson;

    /** 预留给后续计划生成的结构化字段，当前阶段固定为空对象。 */
    @TableField("current_plan_json")
    private String currentPlanJson;

    /** 当前上下文对应的风险等级，为后续 Harness 评估留出接口。 */
    @TableField("risk_level")
    private RiskLevel riskLevel;

    /** 最近一次 Agent 节点名称，方便恢复执行和前端展示。 */
    @TableField("last_agent_step")
    private String lastAgentStep;

    /** 最近更新时间。 */
    @TableField("updated_at")
    private Instant updatedAt;
}
