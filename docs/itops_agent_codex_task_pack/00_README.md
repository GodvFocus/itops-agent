# ITOps Agent Codex Task Pack

本任务包用于交给 Codex 执行企业 IT 工单处理 Agent 项目。Codex 不需要重新阅读完整聊天记录，应以本任务包为唯一任务依据。

## 项目一句话

构建一个面向企业 IT 服务台的智能工单处理系统：Python Agent Runtime 负责工单理解、SOP 检索、计划生成和结果总结；Spring Boot 作为 Enterprise Agent Harness / Agent Control Plane，负责状态机、工具网关、风险策略、审批流、幂等控制、RabbitMQ 异步执行和审计日志。

## 总目标

MVP 只支持三类工单：

1. 账号锁定 / 登录异常
2. VPN 连接失败
3. 系统权限申请

完整闭环：

```text
提交工单
  -> Agent 理解
  -> 信息补全
  -> SOP 检索
  -> Plan 生成
  -> Java Harness 校验
  -> 工具执行 / 人工审批
  -> 用户确认
  -> 关闭或升级人工
```

## 技术栈

- Backend: Spring Boot
- Agent Runtime: Python + LangGraph
- Database: MySQL
- Vector DB: Qdrant
- Cache / Lock / Idempotency: Redis
- Message Queue: RabbitMQ
- Frontend: Vue
- LLM: 通过 LLM Client 抽象，不绑定具体模型

## Codex 执行方式

1. 先阅读 `prompts/CODEX_MASTER_PROMPT.md`
2. 再阅读：
   - `01_PROJECT_BRIEF.md`
   - `02_MVP_SCOPE.md`
   - `03_ARCHITECTURE.md`
   - `07_PHASE_PLAN.md`
   - 当前要执行的 `phases/phase_XX_*.md`
3. 严格按阶段执行。
4. 每个阶段完成后，必须根据 `templates/EVIDENCE_REPORT_TEMPLATE.md` 返回证据。
5. 触发 `08_STOP_RULES.md` 中任一停止条件时，必须停止编码并报告。

## 重要原则

- 不要扩展 MVP。
- 不要做万能 IT Agent。
- 不要做多 Agent。
- 不要接真实企业系统。
- 不要让 Python Agent 直接调用 Mock 企业系统。
- 所有会改变业务状态的操作必须经过 Java Harness。
- MySQL 是业务事实源；Redis 只做锁、幂等、缓存；Qdrant 只做语义检索。
