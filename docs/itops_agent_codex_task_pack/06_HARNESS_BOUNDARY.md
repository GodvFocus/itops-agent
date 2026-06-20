# Harness Boundary

## 核心结论

Harness 是针对 Agent 的，但不应全部放在 Python Agent Runtime 中。

本项目采用双层设计：

1. Python Agent Runtime：负责 Agent 内部执行稳定性
2. Java Enterprise Agent Harness：负责企业级执行治理

## Python Agent Runtime 负责

1. LLM 输出解析
2. JSON Schema 初步校验
3. LangGraph 节点编排
4. 上下文组装
5. 意图分类
6. 槽位抽取
7. SOP 检索
8. Plan 生成
9. 结果总结
10. 调用 Java Harness API

## Java Enterprise Harness 负责

1. 工具是否注册
2. 当前工单状态是否允许执行
3. 当前用户或 Agent 是否有权限
4. 工具参数是否合法
5. 操作风险等级
6. 是否需要审批
7. 是否命中幂等记录
8. 是否拿到 ticket 执行锁
9. 是否允许异步执行
10. 审计日志
11. 工具调用日志
12. 状态机流转

## 禁止事项

禁止 Python Agent 直接调用 Mock 企业系统。

禁止 Agent 自己生成计划、自己裁决风险、自己执行工具、自己记录完成。

## 推荐调用链

```text
User -> Spring Boot 创建 Ticket
  -> RabbitMQ TicketAgentTask
  -> Python LangGraph 读取 Ticket Context
  -> Agent 生成 Candidate Plan
  -> Python 做 Schema 初校验
  -> Java Harness 做最终校验
  -> Java Tool Gateway 执行工具或创建审批
  -> 工具结果 / 审批结果写 MySQL
  -> Python 根据结果继续推理或总结
```
