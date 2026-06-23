"""使用 Pydantic 固化 Agent 节点输出契约。"""

from __future__ import annotations

from typing import Any
from typing import Literal

from pydantic import BaseModel, ConfigDict


class IntentResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    intent: str
    confidence: float
    reasoning: str


class SlotResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    slots: dict[str, Any]
    missingSlots: list[str]
    reasoning: str


class QuestionResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    shouldAskUser: bool
    question: str
    nextStep: str


class ToolRegistryEntry(BaseModel):
    model_config = ConfigDict(extra="forbid")

    tool: str
    action: str
    actionType: Literal["READ", "WRITE"]
    defaultRisk: Literal["LOW", "MEDIUM", "HIGH", "FORBIDDEN"]
    requiredParams: list[str]
    approvalRequired: bool | str
    idempotencyKeyPattern: str | None = None


class SopStepBlueprint(BaseModel):
    model_config = ConfigDict(extra="forbid")

    tool: str
    action: str
    reason: str
    requiredApproval: bool = False


class SopMetadata(BaseModel):
    model_config = ConfigDict(extra="forbid")

    sop_id: str
    name: str
    intent: Literal["ACCOUNT_LOGIN_ISSUE", "VPN_CONNECTION_ISSUE", "PERMISSION_REQUEST", "UNKNOWN"]
    required_slots: list[str]
    applicable_conditions: list[str]
    risk_level: Literal["LOW", "MEDIUM", "HIGH", "FORBIDDEN"]
    allowed_tools: list[str]
    auto_executable_steps: list[SopStepBlueprint]
    approval_required_steps: list[SopStepBlueprint]
    escalation_rules: list[str]


class SopMatch(BaseModel):
    model_config = ConfigDict(extra="forbid")

    sop_id: str
    name: str
    score: float
    reasoning: str
    matched_conditions: list[str]


class SopRetrievalResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    selectedSopId: str
    reasoning: str
    matchedSops: list[SopMatch]


class PlanStep(BaseModel):
    model_config = ConfigDict(extra="forbid")

    stepNo: int
    tool: str
    action: str
    actionType: Literal["READ", "WRITE", "APPROVAL_REQUIRED", "FORBIDDEN"]
    params: dict[str, Any]
    riskLevel: Literal["LOW", "MEDIUM", "HIGH", "FORBIDDEN"]
    requiredApproval: bool
    reason: str


class CandidatePlan(BaseModel):
    model_config = ConfigDict(extra="forbid")

    planId: str
    ticketId: str
    intent: Literal["ACCOUNT_LOGIN_ISSUE", "VPN_CONNECTION_ISSUE", "PERMISSION_REQUEST", "UNKNOWN"]
    riskLevel: Literal["LOW", "MEDIUM", "HIGH", "FORBIDDEN"]
    goal: str | None = None
    steps: list[PlanStep]
