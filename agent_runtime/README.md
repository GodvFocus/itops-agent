# Agent Runtime

这是 `Phase 4` 阶段仍在使用的 Python Agent Runtime，用于把工单理解结果升级为“可校验但不直接执行”的 Candidate Plan。

当前实现特点：

- 提供 `LLM Client` 抽象与 `MockLLMClient`
- 提供 `classify_intent`、`extract_slots`、`generate_question`、`retrieve_sop`、`generate_plan` 五个节点
- 提供 10 条结构化 SOP seed 数据
- 提供 Tool Registry 读取、Qdrant 兼容向量入库与检索能力
- 提供 Candidate Plan 的 `Pydantic` / 合同级基础校验
- 优先尝试构建 LangGraph 工作流；本地未安装 `langgraph` 时回退到顺序执行器

当前目录仍不会直接执行任何工具操作，生成的 Plan 只会交给 Java Harness 做最终放行、异步执行和审计治理。

## 配置约定

Python Runtime 不直接解析 Spring 的 `application.yaml`，而是复用同一套配置命名。
本地开发时会按下面顺序取值：

1. 系统环境变量
2. 仓库根目录 `.env`
3. 仓库根目录 `.env.local`
4. `agent_runtime/.env`

如果你不想每次手动设置环境变量，直接在仓库根目录创建 `.env` 即可。可参考 [`.env.example`](../.env.example)。

安装 Python 依赖可使用：

```bash
D:/anaconda3/envs/lc/python.exe -m pip install -r agent_runtime/requirements.txt
```

支持的键包括：

- `ITOPS_QDRANT_ENABLED`
- `ITOPS_QDRANT_URL`
- `ITOPS_QDRANT_COLLECTION`
- `ITOPS_EMBEDDING_PROVIDER`
- `ITOPS_EMBEDDING_MODEL`
- `ITOPS_EMBEDDING_ENDPOINT`
- `ITOPS_EMBEDDING_API_KEY`
- `ITOPS_CHAT_PROVIDER`
- `ITOPS_CHAT_MODEL`
- `ITOPS_CHAT_ENDPOINT`
- `ITOPS_CHAT_API_KEY`

这样可以保持 Java / Python 运行时解耦，同时避免后续接入 Qdrant、Embedding 模型、Chat 模型时出现两套配置命名。
