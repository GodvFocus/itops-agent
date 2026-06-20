# Phase Acceptance Checklist

Codex 完成阶段前必须自查。

## 通用检查

- [ ] 没有实现下一阶段内容
- [ ] 没有引入未批准技术栈
- [ ] 没有扩大 MVP 范围
- [ ] 有测试
- [ ] 有错误处理
- [ ] 有日志
- [ ] 有 README 或注释说明关键设计
- [ ] 没有让 Python Agent 绕过 Java Harness
- [ ] WRITE 操作有幂等设计
- [ ] 关键事实落 MySQL

## AI 相关检查

- [ ] LLM 输出有 Schema 校验
- [ ] Context Builder 不拼全量历史
- [ ] 工具结果不做有损压缩
- [ ] UNKNOWN 能转人工
- [ ] Agent Plan 不直接执行

## Java 相关检查

- [ ] 状态机禁止非法流转
- [ ] 工具调用有 ToolCallLog
- [ ] 审批结果可审计
- [ ] RabbitMQ 消息可重试
- [ ] Redis 锁不是唯一事实源
- [ ] MySQL 幂等记录可追溯
