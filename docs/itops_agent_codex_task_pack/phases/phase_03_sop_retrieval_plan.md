# Phase 3: SOP Retrieval and Plan Generation

## 阶段目标

让 Agent 从理解工单升级为生成可校验 Candidate Plan，但仍不执行工具。

实现：

1. SOP 元数据管理
2. SOP seed 数据
3. Qdrant 接入
4. SOP 向量入库
5. SOP Retriever
6. Plan Generator
7. Plan JSON Schema 校验
8. Java Harness API stub，可只做接收和基础校验

## 本阶段不要实现

不要实现：

1. 真实工具执行
2. RabbitMQ ToolExecutionTask
3. Redis 幂等
4. 审批执行
5. 复杂重排序模型
6. 自动知识库更新
7. 历史工单复杂检索

## SOP 数据要求

至少 10 条 SOP，其中必须包含：

1. 账号锁定处理 SOP
2. 登录异常处理 SOP
3. VPN 认证失败 SOP
4. VPN 权限缺失 SOP
5. MFA 设备更换 SOP
6. Jira 普通权限申请 SOP
7. GitLab 普通权限申请 SOP
8. 生产系统管理员权限申请 SOP
9. 邮箱无法登录 SOP，可作为未知或转人工对照
10. 高风险权限审批 SOP

每条 SOP 必须包含结构化元数据：

1. sop_id
2. name
3. intent
4. required_slots
5. applicable_conditions
6. risk_level
7. allowed_tools
8. auto_executable_steps
9. approval_required_steps
10. escalation_rules

## Plan 要求

Plan 必须包含：

1. planId
2. ticketId
3. intent
4. riskLevel
5. steps
6. each step:
   - stepNo
   - tool
   - action
   - actionType
   - params
   - riskLevel
   - requiredApproval
   - reason

## 验收标准

1. 账号锁定工单命中账号解锁 SOP
2. VPN MFA 问题命中 VPN/MFA SOP
3. 权限申请命中权限申请 SOP
4. 生产系统管理员权限命中高风险审批 SOP
5. Agent 能生成结构化 Plan
6. Plan 不直接执行
7. Plan 中不能出现未注册工具
8. Plan Schema 校验通过
9. Plan 可以提交给 Java Harness API stub

## 测试要求

准备：

1. 10 条 SOP
2. 30 条工单样本
3. 每条样本标注期望 SOP
4. 每条样本标注期望工具步骤

最低验收：

1. SOP Hit Rate >= 80%
2. Plan Schema Valid Rate >= 90%
3. 未注册工具出现次数 = 0
4. 危险操作直接执行次数 = 0

## 风险

1. SOP 文档写得太随意
2. Qdrant 变成摆设
3. LLM 编造工具
4. Plan 看起来智能但不可执行
5. LLM 跳过审批步骤

## 控制方式

1. 工具必须来自 Tool Registry
2. SOP 必须结构化
3. Plan 必须 Schema 校验
4. Java Harness 后续做最终校验
