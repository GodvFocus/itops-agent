# Phase 4: Enterprise Harness, Tool Gateway, Async Execution

## 阶段目标

实现 Java Enterprise Agent Harness，让 Agent 生成的 Candidate Plan 在 Java 控制下安全执行。

实现：

1. Tool Registry
2. Plan Validator
3. Risk Evaluator
4. Policy Engine
5. Tool Gateway
6. Mock Enterprise Tools
7. RabbitMQ ToolExecutionTask
8. Redis ticket 执行锁
9. Redis + MySQL 幂等记录
10. ToolCallLog
11. 失败重试和升级人工
12. 危险操作拦截

## 本阶段不要实现

不要实现：

1. 真实企业系统
2. 复杂分布式事务
3. 复杂工作流引擎
4. 复杂监控平台
5. 太多工具
6. Python Agent 直接调用工具

## Tool Registry

必须注册：

1. AccountTool.queryAccountStatus READ LOW
2. AccountTool.unlockAccount WRITE MEDIUM
3. VpnTool.queryVpnPermission READ LOW
4. VpnTool.queryVpnLoginFailure READ LOW
5. MfaTool.queryMfaStatus READ LOW
6. MfaTool.resetMfaBindingRequest WRITE HIGH NEED_APPROVAL
7. PermissionTool.queryPermission READ LOW
8. PermissionTool.grantPermission WRITE MEDIUM/HIGH 取决于系统和权限
9. NotificationTool.sendNotification WRITE LOW

## 风险策略

必须实现：

1. READ 自动执行
2. 普通账号解锁可自动执行
3. 普通系统普通权限可自动执行或模拟审批
4. 生产系统管理员权限必须审批
5. 禁用账号禁止
6. 删除权限禁止
7. 未注册工具拒绝
8. 参数缺失拒绝
9. 状态不允许时拒绝

## 幂等要求

所有 WRITE 操作必须有 idem_key。

示例：

```text
idem:unlockAccount:{ticketId}:{employeeId}
idem:grantPermission:{ticketId}:{employeeId}:{targetSystem}:{permissionLevel}
```

必须使用：

1. Redis 快速锁
2. MySQL idempotency_record 作为最终幂等记录

## 验收标准

1. 查询类工具自动执行
2. 普通账号解锁自动执行
3. 生产系统管理员权限申请进入审批
4. 未注册工具被拒绝
5. 参数缺失被拒绝
6. 重复消息不会重复执行 WRITE 操作
7. 每次工具调用都有 ToolCallLog
8. 工具执行失败可以重试或升级人工
9. 同一 ticket 被两个 Worker 消费，只能一个执行成功

## 测试要求

必须有：

1. Tool Gateway 单元测试
2. Risk Policy 单元测试
3. 幂等测试
4. Redis 锁并发测试
5. RabbitMQ 重试测试
6. 死信队列测试
7. ToolCallLog 落库测试
8. Harness 拦截危险操作测试

重点测试：

1. 重复投递 unlockAccount 10 次，只执行一次
2. grantProductionAdminPermission 必须 NEED_APPROVAL
3. 不存在的工具名必须 REJECTED
4. 同一 ticket 并发执行只成功一次

## 风险

1. Harness 太薄，变成形式主义
2. 幂等只靠 Redis，不可靠
3. MQ 重试导致重复授权
4. Agent 绕过 Java 直接调工具
5. 状态机和工具执行状态不一致

## 控制方式

1. Agent 只能调用 Java Harness
2. Redis 锁 + MySQL 幂等记录
3. 所有 WRITE 操作有 idem_key
4. 工具执行结果落 ToolCallLog
5. 状态流转由 Java 统一控制
