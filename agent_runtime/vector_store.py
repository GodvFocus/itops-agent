"""Milvus 向量存储，用于 SOP 向量入库与检索。

通过 pymilvus 的 MilvusClient 高层 API 连接本地 Docker 上的 Milvus 服务。
启动时会校验 Milvus 连接可用性，连接失败则直接报错退出，不保留降级逻辑。
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any

from agent_runtime.config import get_runtime_settings
from agent_runtime.embedding import EMBEDDING_DIMENSIONS

logger = logging.getLogger(__name__)


@dataclass(slots=True)
class VectorPoint:
    """向量存储中的数据点，与具体向量数据库实现解耦。"""

    point_id: str
    vector: list[float]
    payload: dict[str, Any]
    score: float = 0.0


class MilvusVectorStore:
    """基于 Milvus 的向量存储。

    在构造时连接 Milvus 并校验连接可用性，如果 Milvus 不可达则抛出 RuntimeError。
    不提供内存降级——Milvus 是系统运行的必要依赖。
    """

    def __init__(self, collection_name: str | None = None, uri: str | None = None):
        settings = get_runtime_settings()
        self.collection_name = collection_name or settings.milvus_collection_name
        self.uri = uri if uri is not None else settings.milvus_uri

        self._client = self._connect_and_validate()
        self._ensure_collection()

    def _connect_and_validate(self):
        """连接 Milvus 并校验可用性，失败时报错退出。"""
        from pymilvus import MilvusClient

        logger.info("正在连接 Milvus: %s", self.uri)
        try:
            client = MilvusClient(uri=self.uri)
            # 通过 list_collections 校验连接是否真正可用
            client.list_collections()
        except Exception as exc:
            raise RuntimeError(
                f"无法连接 Milvus ({self.uri})，请确认 Milvus 已启动。"
                f"错误详情: {exc}"
            ) from exc

        logger.info("Milvus 连接成功，collection: %s", self.collection_name)
        return client

    def _ensure_collection(self) -> None:
        """如果 collection 不存在则创建，并确保向量索引就绪。"""
        from pymilvus import DataType

        if self._client.has_collection(self.collection_name):
            logger.info("Milvus collection '%s' 已存在", self.collection_name)
            return

        logger.info("创建 Milvus collection '%s' (dim=%d)", self.collection_name, EMBEDDING_DIMENSIONS)

        schema = self._client.create_schema(auto_id=False, enable_dynamic_field=False)
        schema.add_field("point_id", DataType.VARCHAR, max_length=128, is_primary=True)
        schema.add_field("vector", DataType.FLOAT_VECTOR, dim=EMBEDDING_DIMENSIONS)
        schema.add_field("payload", DataType.JSON)

        index_params = self._client.prepare_index_params()
        index_params.add_index(
            field_name="vector",
            index_type="IVF_FLAT",
            metric_type="COSINE",
            params={"nlist": 128},
        )

        self._client.create_collection(
            collection_name=self.collection_name,
            schema=schema,
            index_params=index_params,
        )
        logger.info("Milvus collection '%s' 创建完成", self.collection_name)

    def upsert(self, points: list[VectorPoint]) -> None:
        """插入或更新向量数据。"""
        if not points:
            return

        data = [
            {
                "point_id": point.point_id,
                "vector": point.vector,
                "payload": point.payload,
            }
            for point in points
        ]
        self._client.upsert(collection_name=self.collection_name, data=data)

    def search(self, query_vector: list[float], limit: int = 3) -> list[VectorPoint]:
        """向量相似度检索，返回按相似度排序的结果。"""
        results = self._client.search(
            collection_name=self.collection_name,
            data=[query_vector],
            limit=limit,
            output_fields=["payload"],
        )

        points: list[VectorPoint] = []
        # MilvusClient.search 返回的是嵌套列表：外层对应每个查询，内层是命中结果
        for hit in results[0]:
            entity = hit.get("entity", {})
            payload = entity.get("payload", {})
            points.append(
                VectorPoint(
                    point_id=str(hit.get("id", "")),
                    vector=query_vector,
                    payload=payload if isinstance(payload, dict) else {},
                    score=float(hit.get("distance", 0.0)),
                )
            )
        return points
