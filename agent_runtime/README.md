# Agent Runtime

这是 Python Agent Runtime，用于把工单理解结果升级为"可校验但不直接执行"的 Candidate Plan。

当前实现特点：

- 提供 `LLM Client` 抽象、`MockLLMClient` 和 `OpenAICompatibleLLMClient`
- 通过 OpenAI 兼容协议接入真实 LLM，支持 DeepSeek、Qwen、GLM 配置化切换
- 供应商默认 base_url / model 自动填充，用户只需配置 `ITOPS_CHAT_PROVIDER` + `ITOPS_CHAT_API_KEY`
- 提供 `classify_intent`、`extract_slots`、`generate_question`、`retrieve_sop`、`generate_plan` 五个真实节点
- 提供 10 条结构化 SOP seed 数据
- 提供 Tool Registry 读取、Milvus 向量入库与检索能力
- 提供 Candidate Plan 的 `Pydantic` / 合同级基础校验
- 使用 **LangGraph StateGraph** 作为真实状态图编排引擎：
  - 定义统一 `AgentState` 状态对象
  - 5 个节点均为真实逻辑，不再是空操作
  - 使用条件边控制流程：缺槽位 / 未知意图时提前终止，槽位完整时进入 SOP 检索和 Plan 生成
  - 每个节点记录 `nodeTrace`（节点名、时间戳、耗时、错误信息），便于回放和评估
  - 未安装 `langgraph` 时回退到顺序执行器

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

- `ITOPS_MILVUS_URI`
- `ITOPS_MILVUS_COLLECTION`
- `ITOPS_EMBEDDING_PROVIDER`
- `ITOPS_EMBEDDING_MODEL`
- `ITOPS_EMBEDDING_ENDPOINT`
- `ITOPS_EMBEDDING_API_KEY`
- `ITOPS_CHAT_PROVIDER`
- `ITOPS_CHAT_MODEL`
- `ITOPS_CHAT_ENDPOINT`
- `ITOPS_CHAT_API_KEY`

### Chat 模型配置

`ITOPS_CHAT_PROVIDER` 支持以下值：

| Provider | 说明 | 默认 base_url | 默认 model |
| --- | --- | --- | --- |
| `mock` | 规则驱动的 Mock（默认，离线可运行） | — | mock-chat |
| `deepseek` | DeepSeek | `https://api.deepseek.com` | `deepseek-chat` |
| `qwen` | 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` |
| `glm` | 智谱 GLM | `https://open.bigmodel.cn/api/paas/v4` | `glm-4-flash` |
| `openai` | OpenAI 官方 | `https://api.openai.com/v1` | `gpt-4o-mini` |

只需配置 `ITOPS_CHAT_PROVIDER` 和 `ITOPS_CHAT_API_KEY` 即可使用真实 LLM。
`ITOPS_CHAT_ENDPOINT` 和 `ITOPS_CHAT_MODEL` 不配置时自动填充供应商默认值，显式配置则覆盖默认值。

当 `ITOPS_CHAT_PROVIDER` 配置为真实供应商但缺少 `ITOPS_CHAT_API_KEY` 时，自动降级到 MockLLMClient 保证链路不中断。
真实 LLM 调用失败时也会自动重试并最终降级到 MockLLMClient。

这样可以保持 Java / Python 运行时解耦，同时避免后续接入 Milvus、Embedding 模型、Chat 模型时出现两套配置命名。
