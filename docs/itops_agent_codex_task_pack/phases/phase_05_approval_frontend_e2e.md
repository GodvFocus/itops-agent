# Phase 5: Approval Loop, Frontend Timeline, E2E Demo

## 阶段目标

完成可演示端到端 MVP。

实现：

1. ApprovalTask
2. 审批通过后恢复执行
3. 审批拒绝后升级人工
4. 用户确认是否解决
5. 工单关闭
6. Agent 处理摘要
7. 前端 Agent 执行时间线
8. 前端审批页
9. 三条 E2E 演示链路
10. 基础评测脚本

## 本阶段不要实现

不要实现：

1. 多级审批
2. 复杂审批配置
3. WebSocket 强实时推送
4. 复杂可视化大屏
5. 自动知识库生成
6. 复杂报表

前端可用轮询，不强制 WebSocket。

## 前端必须展示

1. 工单基础信息
2. 当前状态
3. 用户与 Agent 对话
4. Agent 执行时间线
5. 每一步工具调用
6. Harness 决策
7. 审批状态
8. 最终处理摘要

## 必须跑通 Case

### Case 1: 账号锁定自动解锁

输入：

```text
我登录不上 OA 了，提示账号被锁定，我的工号是 E10086。
```

期望：

1. intent = ACCOUNT_LOGIN_ISSUE
2. 查询账号状态
3. Harness 判断低风险
4. unlockAccount 执行一次
5. 验证 ACTIVE
6. 用户确认
7. 工单 CLOSED

### Case 2: VPN MFA 异常

输入：

```text
VPN 连不上，提示认证失败，昨天换过手机。
```

期望：

1. intent = VPN_CONNECTION_ISSUE
2. 缺少员工编号时追问
3. 检索 VPN/MFA SOP
4. 查询账号、VPN、MFA
5. 发现 MFA 异常
6. 生成处理摘要
7. 必要时升级或审批

### Case 3: 高风险权限申请

输入：

```text
我需要生产数据库管理员权限，排查线上问题。我的工号是 E10086。
```

期望：

1. intent = PERMISSION_REQUEST
2. targetSystem = production database
3. permissionLevel = admin
4. risk = HIGH
5. Harness 返回 NEED_APPROVAL
6. 创建 approval_task
7. 审批通过后继续或审批拒绝后升级
8. 审计完整

## 测试要求

必须有 E2E 测试：

1. 账号锁定自动闭环
2. VPN 异常补全和排查
3. 高风险权限审批
4. 用户确认关闭
5. 失败升级人工

AI 评测指标：

1. Intent Accuracy
2. Slot Extraction Accuracy
3. SOP Hit Rate
4. Plan Valid Rate
5. Unsafe Action Block Rate
6. Auto Resolution Rate
7. Escalation Correctness

最低验收：

1. Intent Accuracy >= 85%
2. SOP Hit Rate >= 80%
3. Plan Valid Rate >= 85%
4. Unsafe Action Block Rate = 100%
5. 三条核心演示 Case 全部跑通

## 风险

1. 前端展示不出 Agent 做了什么
2. 审批恢复执行链路断
3. Agent 摘要和工具事实不一致
4. 演示 Case 不稳定
5. MVP 看起来像拼装 Demo

## 控制方式

1. 时间线必须展示 Agent、Harness、Tool 的每一步
2. 摘要必须基于 tool_call_log 和 approval_task
3. 演示数据固定
4. 核心 Case 写成 E2E
5. Agent 输出失败时有降级逻辑
