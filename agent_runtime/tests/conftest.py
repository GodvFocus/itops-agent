"""pytest 全局配置：自动 mock embedding 避免测试依赖外部 Ollama 服务。

测试环境无法保证 Ollama + bge-m3 可用，因此所有测试自动使用确定性 mock embedding。
mock 使用与旧 SimpleHashEmbedding 相同的哈希策略，但输出 1024 维向量以匹配 bge-m3。
"""

from __future__ import annotations

import hashlib
import math
import re
from unittest.mock import patch

import pytest


def _mock_embed(self, text: str) -> list[float]:
    """确定性 embedding，基于哈希分桶 + L2 归一化，输出 1024 维向量。"""
    dimensions = 1024
    vector = [0.0] * dimensions
    for token in _tokenize(text):
        digest = hashlib.sha256(token.encode("utf-8")).digest()
        bucket = int.from_bytes(digest[:2], "big") % dimensions
        sign = 1.0 if digest[2] % 2 == 0 else -1.0
        vector[bucket] += sign
    norm = math.sqrt(sum(v * v for v in vector))
    if norm == 0:
        return vector
    return [v / norm for v in vector]


def _tokenize(text: str) -> list[str]:
    normalized = text.upper()
    english_tokens = re.findall(r"[A-Z0-9_]+", normalized)
    chinese = "".join(c for c in text if "\u4e00" <= c <= "\u9fff")
    bigrams = [chinese[i:i + 2] for i in range(max(0, len(chinese) - 1))]
    return english_tokens + bigrams


@pytest.fixture(autouse=True, scope="session")
def mock_embedding():
    """所有测试自动 mock OllamaEmbeddingClient.embed，避免依赖外部服务。"""
    from agent_runtime.embedding import OllamaEmbeddingClient
    from agent_runtime.retrieval import get_default_retriever

    # 清除 lru_cache，确保下次调用使用 mock 后的 embedder
    get_default_retriever.cache_clear()

    with patch.object(OllamaEmbeddingClient, "embed", _mock_embed):
        yield

    get_default_retriever.cache_clear()
