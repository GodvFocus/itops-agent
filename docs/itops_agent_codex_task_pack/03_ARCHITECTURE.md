# Architecture

## 总体架构

```text
Vue Frontend
  -> Spring Boot Backend / Enterprise Agent Harness
       -> MySQL
       -> Redis
       -> RabbitMQ
       -> Mock Enterprise Tools
  -> Python Agent Runtime
       -> LangGraph
       -> LLM Client
       -> Qdrant
```

## 服务边界

### Spring Boot Backend / Enterprise Agent Harness

职责：

1. 工单生命周期
2. 工单状态机
3. 用户角色权限
4. Agent 任务管理
5. Plan Validator
6. Risk Evaluator
7. Policy Engine
8. Approval Gate
9. Tool Registry
10. Tool Gateway
11. ToolCallLog
12. AuditLog
13. Redis 锁和幂等
14. RabbitMQ 生产 / 消费
15. MySQL 事实存储
16. Context API

### Python Agent Runtime

职责：

1. LLM Client 抽象
2. LangGraph Workflow
3. Intent Classification
4. Slot Extraction
5. Missing Slot Question
6. Context Builder
7. SOP Retriever
8. Plan Generator
9. Harness API Client
10. Result Summarizer
11. Evaluation Script

### Qdrant

职责：

1. SOP 文档片段向量检索
2. 可选：历史已解决工单摘要检索

### Redis

职责：

1. ticket 执行锁
2. 写操作幂等 key
3. 工具调用限流
4. 短期上下文缓存

Redis 不是最终事实源。

### MySQL

职责：

1. 工单事实
2. 工单状态
3. 原始对话
4. 结构化上下文
5. 工具调用日志
6. 审批任务
7. 审计日志
8. 幂等记录
9. SOP 元数据

MySQL 是最终事实源。

## 关键设计原则

### Agent Runtime 和 Enterprise Harness 分离

Python Agent Runtime 负责智能推理和生成候选计划。

Java Enterprise Harness 负责最终裁决：

```text
Agent: 我建议执行 unlockAccount
Harness: 我检查状态、权限、风险、审批、幂等后决定是否允许
```

### Agent 不直接调用工具

禁止：

```text
Python Agent -> AccountTool
```

必须：

```text
Python Agent -> Java Harness -> Tool Gateway -> Mock Enterprise Tool
```

### Java 状态机是业务事实

LangGraph State 只保存 Agent 推理状态。最终工单状态以 Java / MySQL 为准。
