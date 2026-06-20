# MVP Scope

## MVP 只做什么

### 支持工单类型

1. ACCOUNT_LOGIN_ISSUE：账号锁定 / 登录异常
2. VPN_CONNECTION_ISSUE：VPN 连接失败
3. PERMISSION_REQUEST：系统权限申请
4. UNKNOWN：其他问题，转人工

### 支持角色

1. EMPLOYEE：员工，提交工单、补充信息、确认是否解决
2. IT_ENGINEER：IT 工程师，查看工单、接管工单、查看 Agent 轨迹
3. APPROVER：审批人，审批高风险操作
4. ADMIN：管理员，查看审计和配置

### 支持工具

Mock 企业系统即可：

1. AccountTool
   - queryAccountStatus
   - unlockAccount
2. VpnTool
   - queryVpnPermission
   - queryVpnLoginFailure
3. MfaTool
   - queryMfaStatus
   - resetMfaBindingRequest
4. PermissionTool
   - queryPermission
   - grantPermission
5. NotificationTool
   - sendNotification

### Agent 自动化策略

| 操作类型 | 策略 |
|---|---|
| 查询类 READ 操作 | 自动执行 |
| 普通员工账号解锁 | 可自动执行 |
| 普通系统低权限授权 | 可自动执行或模拟审批 |
| 生产系统管理员权限 | 必须人工审批 |
| 禁用账号 | 禁止 |
| 删除权限 | 禁止 |

## MVP 不做什么

不要实现：

1. 万能 IT 问答
2. 多 Agent 协作
3. 真实企业系统集成
4. 完整 ITIL 流程
5. 复杂长期用户记忆
6. Agent 自动学习新 SOP
7. 自动改写知识库
8. 多租户
9. 复杂微服务治理
10. Kubernetes
11. Elasticsearch
12. Kafka
13. Nacos
14. XXL-Job
15. 复杂监控平台
16. ServiceNow / Jira 级完整前端

## 成功标准

MVP 必须能稳定演示三条链路：

1. 账号锁定：自动查询、低风险解锁、用户确认、关闭工单
2. VPN 异常：补全信息、SOP 检索、多工具排查、生成摘要、必要时升级
3. 高风险权限：识别高风险、Harness 拦截、生成审批、审批后继续或结束
