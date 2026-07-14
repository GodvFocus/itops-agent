"""真实 Embedding 客户端，通过 Ollama 的 OpenAI 兼容协议调用 bge-m3 模型。

Ollama 提供 OpenAI 兼容的 /v1/embeddings 接口，因此可以直接使用 openai SDK 调用。
bge-m3 模型输出 1024 维向量。

配置通过 ITOPS_EMBEDDING_* 环境变量控制：
- ITOPS_EMBEDDING_PROVIDER=ollama
- ITOPS_EMBEDDING_MODEL=bge-m3:latest
- ITOPS_EMBEDDING_ENDPOINT=http://localhost:11434/v1
- ITOPS_EMBEDDING_API_KEY=ollama（Ollama 不校验 key，但 SDK 需要非空值）
"""

from __future__ import annotations

import logging
from typing import Protocol

logger = logging.getLogger(__name__)

# bge-m3 模型输出的向量维度
EMBEDDING_DIMENSIONS = 1024


class Embedder(Protocol):
    """Embedding 客户端协议，SopRetriever 依赖此接口而非具体实现。"""

    def embed(self, text: str) -> list[float]: ...


class OllamaEmbeddingClient:
    """通过 Ollama 的 OpenAI 兼容协议调用真实 Embedding 模型。

    支持本地 Ollama 上部署的 bge-m3 或其他 embedding 模型。
    """

    def __init__(self, api_key: str, base_url: str, model: str):
        if not base_url:
            raise ValueError(
                "OllamaEmbeddingClient 需要 base_url，请在 .env 中配置 ITOPS_EMBEDDING_ENDPOINT"
            )

        self.api_key = api_key or "ollama"
        self.base_url = base_url
        self.model = model
        self._client = self._create_client()

    def _create_client(self):
        """创建 OpenAI SDK 客户端实例，指向 Ollama 服务。"""
        from openai import OpenAI

        return OpenAI(api_key=self.api_key, base_url=self.base_url)

    def embed(self, text: str) -> list[float]:
        """调用 Ollama embedding API，返回指定文本的向量表示。"""
        response = self._client.embeddings.create(model=self.model, input=text)
        return list(response.data[0].embedding)


def create_embedder(settings=None) -> Embedder:
    """根据运行时配置创建 Embedding 客户端实例。

    参数:
        settings: RuntimeSettings 实例。为 None 时自动加载默认配置。

    返回:
        Embedder 实例。
    """
    if settings is None:
        from agent_runtime.config import get_runtime_settings

        settings = get_runtime_settings()

    provider = settings.embedding.provider.strip().lower()

    if provider in ("ollama", "openai"):
        return OllamaEmbeddingClient(
            api_key=settings.embedding.api_key,
            base_url=settings.embedding.endpoint,
            model=settings.embedding.model,
        )

    raise ValueError(
        f"不支持的 embedding provider: {provider}，"
        f"请在 .env 中配置 ITOPS_EMBEDDING_PROVIDER=ollama"
    )
