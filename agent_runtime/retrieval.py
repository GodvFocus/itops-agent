"""提供 Qdrant 兼容的 SOP 向量入库与检索能力。

Embedding 使用 Ollama 部署的 bge-m3 模型（1024 维向量），
不再保留 hash embedding 降级逻辑。
"""

from __future__ import annotations

from dataclasses import dataclass
from functools import lru_cache
import math
from typing import Any

from agent_runtime.config import get_runtime_settings
from agent_runtime.embedding import EMBEDDING_DIMENSIONS, Embedder, create_embedder
from agent_runtime.models import SopMatch, SopMetadata, SopRetrievalResult
from agent_runtime.sop_catalog import get_seed_sops


def _risk_order(value: str) -> int:
    return {"LOW": 1, "MEDIUM": 2, "HIGH": 3, "FORBIDDEN": 4}[value]


@dataclass(slots=True)
class QdrantPoint:
    point_id: str
    vector: list[float]
    payload: dict[str, Any]
    score: float = 0.0


class InMemoryQdrantCollection:
    """本地兜底实现保持 Qdrant 的 upsert/search 语义，便于未部署 Qdrant 时使用。"""

    def __init__(self):
        self._points: dict[str, QdrantPoint] = {}

    def upsert(self, points: list[QdrantPoint]) -> None:
        for point in points:
            self._points[point.point_id] = point

    def search(self, query_vector: list[float], limit: int) -> list[QdrantPoint]:
        scored: list[QdrantPoint] = []
        for point in self._points.values():
            score = _cosine_similarity(query_vector, point.vector)
            scored.append(QdrantPoint(point.point_id, point.vector, point.payload, score))
        scored.sort(key=lambda item: item.score, reverse=True)
        return scored[:limit]


class QdrantCompatibleVectorStore:
    """
    优先尝试连接真实 Qdrant。
    本地没有 client 或没有 URL 时回退到内存实现，保证离线环境也能验收。
    """

    def __init__(self, collection_name: str | None = None, url: str | None = None):
        settings = get_runtime_settings()
        self.collection_name = collection_name or settings.qdrant_collection_name
        self.url = url if url is not None else settings.qdrant_url
        self._backend = self._build_backend()

    def _build_backend(self):
        if self.url:
            try:
                from qdrant_client import QdrantClient  # type: ignore
                from qdrant_client.http.models import Distance, PointStruct, VectorParams  # type: ignore
            except ImportError:
                return InMemoryQdrantCollection()

            client = QdrantClient(url=self.url)
            try:
                client.get_collection(self.collection_name)
            except Exception:
                client.create_collection(
                    collection_name=self.collection_name,
                    # bge-m3 输出 1024 维向量
                    vectors_config=VectorParams(size=EMBEDDING_DIMENSIONS, distance=Distance.COSINE),
                )

            class RemoteBackend:
                def upsert(self_inner, points: list[QdrantPoint]) -> None:
                    client.upsert(
                        collection_name=self.collection_name,
                        points=[
                            PointStruct(id=point.point_id, vector=point.vector, payload=point.payload)
                            for point in points
                        ],
                    )

                def search(self_inner, query_vector: list[float], limit: int) -> list[QdrantPoint]:
                    results = client.search(
                        collection_name=self.collection_name,
                        query_vector=query_vector,
                        limit=limit,
                    )
                    return [
                        QdrantPoint(
                            point_id=str(result.id),
                            vector=query_vector,
                            payload=dict(result.payload or {}),
                            score=float(result.score or 0.0),
                        )
                        for result in results
                    ]

            return RemoteBackend()
        return InMemoryQdrantCollection()

    def upsert(self, points: list[QdrantPoint]) -> None:
        self._backend.upsert(points)

    def search(self, query_vector: list[float], limit: int = 3) -> list[QdrantPoint]:
        return self._backend.search(query_vector, limit)


class SopRetriever:
    def __init__(self, store: QdrantCompatibleVectorStore, embedder: Embedder, sop_catalog: tuple[SopMetadata, ...]):
        self.store = store
        self.embedder = embedder
        self.sop_catalog = sop_catalog
        self._bootstrap()

    def _bootstrap(self) -> None:
        points = []
        for sop in self.sop_catalog:
            searchable_text = self._build_sop_document(sop)
            points.append(
                QdrantPoint(
                    point_id=sop.sop_id,
                    vector=self.embedder.embed(searchable_text),
                    payload=sop.model_dump(),
                )
            )
        self.store.upsert(points)

    def retrieve(self, context: dict[str, Any], top_k: int = 3) -> SopRetrievalResult:
        query_text = self._build_query_text(context)
        query_vector = self.embedder.embed(query_text)
        candidates = self.store.search(query_vector, limit=max(top_k, len(self.sop_catalog)))
        ranked: list[tuple[float, SopMetadata, list[str]]] = []

        for point in candidates:
            sop = SopMetadata.model_validate(point.payload)
            score = point.score
            if sop.intent != context.get("intent"):
                score -= 0.85
            boost, matched_conditions = self._boost_score(sop, context, query_text)
            score += boost
            ranked.append((score, sop, matched_conditions))

        ranked.sort(key=lambda item: (item[0], _risk_order(item[1].risk_level)), reverse=True)
        top_matches = ranked[:top_k]
        selected = top_matches[0][1]
        reasoning = f"根据向量相似度与业务条件加权，优先选择 {selected.name}。"
        return SopRetrievalResult(
            selectedSopId=selected.sop_id,
            reasoning=reasoning,
            matchedSops=[
                SopMatch(
                    sop_id=sop.sop_id,
                    name=sop.name,
                    score=round(score, 4),
                    reasoning=f"命中条件：{', '.join(matched_conditions) if matched_conditions else '主要依赖语义相似度'}",
                    matched_conditions=matched_conditions,
                )
                for score, sop, matched_conditions in top_matches
            ],
        )

    def _build_sop_document(self, sop: SopMetadata) -> str:
        parts = [
            sop.sop_id,
            sop.name,
            sop.intent,
            " ".join(sop.required_slots),
            " ".join(sop.applicable_conditions),
            " ".join(sop.allowed_tools),
            " ".join(step.reason for step in sop.auto_executable_steps),
            " ".join(step.reason for step in sop.approval_required_steps),
            " ".join(sop.escalation_rules),
        ]
        return "\n".join(parts)

    def _build_query_text(self, context: dict[str, Any]) -> str:
        ticket_facts = context.get("ticket_facts", {})
        recent_messages = context.get("recent_messages", [])
        known_slots = context.get("known_slots", {})
        parts = [
            str(ticket_facts.get("title", "")),
            str(ticket_facts.get("description", "")),
            str(context.get("intent", "")),
            " ".join(f"{key}:{value}" for key, value in known_slots.items()),
        ]
        parts.extend(str(message.get("content", "")) for message in recent_messages)
        return "\n".join(parts)

    def _boost_score(self, sop: SopMetadata, context: dict[str, Any], query_text: str) -> tuple[float, list[str]]:
        lower = query_text.lower()
        matched_conditions: list[str] = []
        boost = 0.0
        known_slots = context.get("known_slots", {})
        target_system = str(known_slots.get("targetSystem", "")).upper()
        permission_level = str(known_slots.get("permissionLevel", "")).upper()
        device_type = str(known_slots.get("deviceType", "")).upper()
        mfa_changed = bool(known_slots.get("mfaRecentlyChanged"))

        def hit(*tokens: str) -> bool:
            return any(token.lower() in lower for token in tokens)

        is_email = target_system == "EMAIL" or hit("邮箱", "邮件", "email", "exchange")
        is_locked = hit("锁定", "locked", "账号已锁定", "账号被锁定", "账户已锁定", "账户被锁定")
        is_login_error = hit("invalid credentials", "密码错误", "登录失败", "sign in failed")
        is_vpn_auth_fail = "vpn" in lower and hit("认证失败", "authentication failed", "无法连接", "连不上")
        is_vpn_perm_missing = "vpn" in lower and hit("未开通", "无权限", "access denied")
        is_prod_or_sensitive = hit("生产", "prod", "线上", "敏感")
        is_explicit_mfa_change = hit("换绑", "重绑", "重新绑定", "验证器", "重置mfa", "reset mfa")

        if sop.sop_id == "SOP-EMAIL-LOGIN-MANUAL-001" and is_email:
            matched_conditions.append("邮箱登录问题")
            boost += 1.45

        if sop.sop_id == "SOP-ACC-LOCKED-001" and is_locked and not is_email:
            matched_conditions.append("账号锁定")
            boost += 1.2

        if sop.sop_id == "SOP-ACC-LOGIN-ABNORMAL-001" and is_login_error and not is_locked and not is_email:
            matched_conditions.append("登录异常")
            boost += 1.0

        if sop.sop_id == "SOP-VPN-AUTH-FAIL-001" and is_vpn_auth_fail:
            matched_conditions.append("VPN 认证失败")
            boost += 0.8
            if mfa_changed:
                matched_conditions.append("存在 MFA 异常线索")
                boost += 0.1

        if sop.sop_id == "SOP-VPN-PERM-MISSING-001" and is_vpn_perm_missing:
            matched_conditions.append("VPN 权限缺失")
            boost += 1.05

        if sop.sop_id == "SOP-MFA-DEVICE-CHANGE-001" and (
            is_explicit_mfa_change or (mfa_changed and device_type in {"IOS", "ANDROID"})
        ):
            matched_conditions.append("MFA 设备变更")
            boost += 1.35
            if hit("vpn", "认证失败"):
                matched_conditions.append("伴随 VPN 认证失败")
                boost += 0.25

        if sop.sop_id == "SOP-PERM-JIRA-STANDARD-001" and target_system == "JIRA" and permission_level != "ADMIN":
            matched_conditions.append("Jira 普通权限申请")
            boost += 1.1

        if sop.sop_id == "SOP-PERM-GITLAB-STANDARD-001" and target_system == "GITLAB" and permission_level != "ADMIN":
            matched_conditions.append("GitLab 普通权限申请")
            boost += 1.1

        if sop.sop_id == "SOP-PERM-PROD-ADMIN-001" and permission_level == "ADMIN" and is_prod_or_sensitive:
            matched_conditions.append("生产系统管理员权限")
            boost += 0.95

        if sop.sop_id == "SOP-PERM-HIGH-RISK-APPROVAL-001" and permission_level == "ADMIN":
            matched_conditions.append("高风险管理员权限")
            boost += 1.25
            if is_prod_or_sensitive:
                matched_conditions.append("生产或敏感环境")
                boost += 0.45

        return boost, matched_conditions


def _cosine_similarity(left: list[float], right: list[float]) -> float:
    numerator = sum(a * b for a, b in zip(left, right))
    left_norm = math.sqrt(sum(a * a for a in left))
    right_norm = math.sqrt(sum(b * b for b in right))
    if left_norm == 0 or right_norm == 0:
        return 0.0
    return numerator / (left_norm * right_norm)


@lru_cache(maxsize=1)
def get_default_retriever() -> SopRetriever:
    """创建默认的 SOP 检索器，使用 Ollama bge-m3 真实 embedding。"""
    return SopRetriever(
        store=QdrantCompatibleVectorStore(),
        embedder=create_embedder(),
        sop_catalog=get_seed_sops(),
    )
