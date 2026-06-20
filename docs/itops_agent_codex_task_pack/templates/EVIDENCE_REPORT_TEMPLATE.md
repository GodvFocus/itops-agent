# Evidence Report Template

Codex 每完成一个阶段或触发停止条件，必须按此模板返回。

## 1. 当前阶段

- Phase:
- Status: DONE / BLOCKED / PARTIAL
- Summary:

## 2. 完成内容

列出已实现能力：

1.
2.
3.

## 3. 改动文件

列出新增 / 修改文件：

```text
path/to/file
path/to/file
```

## 4. 关键设计说明

说明本阶段关键设计，不超过 10 条。

## 5. API / Schema / Event 变更

如有新增接口、Schema、事件，列出。

## 6. 测试证据

必须包含：

```bash
# 实际执行过的测试命令
```

测试结果摘要：

```text
passed:
failed:
skipped:
```

## 7. 验收标准对照

逐条对照阶段验收标准：

| 验收项 | 结果 | 证据 |
|---|---|---|
| | PASS/FAIL | |

## 8. 风险和未完成项

列出：

1.
2.
3.

## 9. 是否触发停止规则

- Triggered: YES / NO
- Rule:
- Explanation:

## 10. 下一步建议

只建议下一阶段应该做什么，不要直接实现。
