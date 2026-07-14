"""pytest 全局配置：自动 mock embedding 和向量存储，避免测试依赖外部服务。

测试环境无法保证 Ollama + bge-m3 和 Milvus 可用，因此所有测试自动使用：
- 确定性 mock embedding（哈希分桶 + L2 归一化，1024 维）
- 内存 mock 向量存储（模拟 MilvusVectorStore 的 upsert/search 接口）
"""

from __future__ import annotations

import hashlib
import math
import re
from unittest.mock import patch
from typing import Any

import pytest

from agent_runtime.vector_store import VectorPoint


# ---------------------------------------------------------------------------
# Mock embedding
# ---------------------------------------------------------------------------


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


# ---------------------------------------------------------------------------
# Mock vector store
# ---------------------------------------------------------------------------


class _MockVectorStore:
    """测试用的内存向量存储，模拟 MilvusVectorStore 的 upsert/search 接口。"""

    def __init__(self, *args: Any, **kwargs: Any):
        self._points: dict[str, VectorPoint] = {}

    def upsert(self, points: list[VectorPoint]) -> None:
        for point in points:
            self._points[point.point_id] = point

    def search(self, query_vector: list[float], limit: int = 3) -> list[VectorPoint]:
        scored: list[VectorPoint] = []
        for point in self._points.values():
            score = _cosine_similarity(query_vector, point.vector)
            scored.append(VectorPoint(point.point_id, point.vector, point.payload, score))
        scored.sort(key=lambda item: item.score, reverse=True)
        return scored[:limit]


def _cosine_similarity(left: list[float], right: list[float]) -> float:
    numerator = sum(a * b for a, b in zip(left, right))
    left_norm = math.sqrt(sum(a * a for a in left))
    right_norm = math.sqrt(sum(b * b for b in right))
    if left_norm == 0 or right_norm == 0:
        return 0.0
    return numerator / (left_norm * right_norm)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture(autouse=True, scope="session")
def mock_external_services():
    """所有测试自动 mock embedding 和向量存储，避免依赖外部服务。"""
    from agent_runtime.embedding import OllamaEmbeddingClient
    from agent_runtime.retrieval import get_default_retriever

    # 清除 lru_cache，确保下次调用使用 mock
    get_default_retriever.cache_clear()

    with patch.object(OllamaEmbeddingClient, "embed", _mock_embed), \
         patch("agent_runtime.retrieval.MilvusVectorStore", _MockVectorStore):
        yield

    get_default_retriever.cache_clear()
