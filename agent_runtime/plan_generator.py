"""根据命中的 SOP 生成候选 Plan，并做 Schema 级基础校验。"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any

from agent_runtime.models import CandidatePlan, PlanStep, SopMetadata
from agent_runtime.sop_catalog import get_sop_by_id
from agent_runtime.tool_registry import load_tool_registry, resolve_tool_entry

DEFAULT_PLAN_SCHEMA_PATH = Path("docs/itops_agent_codex_task_pack/contracts/agent_plan.schema.json")


class PlanSchemaValidator:
    def __init__(self, schema_path: str | Path = DEFAULT_PLAN_SCHEMA_PATH):
        self.schema = json.loads(Path(schema_path).read_text(encoding="utf-8"))

    def validate(self, plan_payload: dict[str, Any]) -> CandidatePlan:
        plan = CandidatePlan.model_validate(plan_payload)
        intent_enums = set(self.schema["properties"]["intent"]["enum"])
        risk_enums = set(self.schema["properties"]["riskLevel"]["enum"])
        if plan.intent not in intent_enums:
            raise ValueError(f"Plan intent {plan.intent} is not allowed by schema")
        if plan.riskLevel not in risk_enums:
            raise ValueError(f"Plan risk {plan.riskLevel} is not allowed by schema")
        for step in plan.steps:
            if not resolve_tool_entry(step.tool, step.action):
                raise ValueError(f"Plan uses unregistered tool action: {step.tool}.{step.action}")
        return plan


class CandidatePlanGenerator:
    def __init__(self, schema_validator: PlanSchemaValidator | None = None):
        self.schema_validator = schema_validator or PlanSchemaValidator()
        self.tool_registry = load_tool_registry()

    def generate(self, context: dict[str, Any], selected_sop_id: str) -> CandidatePlan:
        sop = get_sop_by_id(selected_sop_id)
        known_slots = context.get("known_slots", {})
        ticket_facts = context.get("ticket_facts", {})
        steps: list[PlanStep] = []

        for blueprint in [*sop.auto_executable_steps, *sop.approval_required_steps]:
            steps.append(self._build_step(len(steps) + 1, blueprint.tool, blueprint.action, blueprint.reason, blueprint.requiredApproval, known_slots, ticket_facts, sop))

        plan_payload = {
            "planId": self._build_plan_id(ticket_facts.get("ticketId", ""), selected_sop_id),
            "ticketId": ticket_facts.get("ticketId", ""),
            "intent": sop.intent,
            "riskLevel": self._resolve_plan_risk(sop, steps),
            "goal": f"基于 {sop.name} 生成候选处理计划，交由 Harness 做最终放行判断。",
            "steps": [step.model_dump() for step in steps],
        }
        return self.schema_validator.validate(plan_payload)

    def _build_step(
        self,
        step_no: int,
        tool: str,
        action: str,
        reason: str,
        required_approval: bool,
        known_slots: dict[str, Any],
        ticket_facts: dict[str, Any],
        sop: SopMetadata,
    ) -> PlanStep:
        registry_entry = self.tool_registry[(tool, action)]
        if f"{tool}.{action}" not in sop.allowed_tools:
            raise ValueError(f"SOP {sop.sop_id} tried to use tool outside allow list: {tool}.{action}")

        params = self._resolve_params(registry_entry.requiredParams, known_slots, ticket_facts, sop)
        risk_level = registry_entry.defaultRisk
        if required_approval:
            risk_level = "HIGH" if risk_level != "FORBIDDEN" else risk_level
        return PlanStep(
            stepNo=step_no,
            tool=tool,
            action=action,
            actionType=registry_entry.actionType,
            params=params,
            riskLevel=risk_level,
            requiredApproval=required_approval or self._registry_requires_approval(registry_entry.approvalRequired, known_slots),
            reason=reason,
        )

    def _resolve_params(
        self,
        required_params: list[str],
        known_slots: dict[str, Any],
        ticket_facts: dict[str, Any],
        sop: SopMetadata,
    ) -> dict[str, Any]:
        params: dict[str, Any] = {}
        for param in required_params:
            if param in known_slots:
                params[param] = known_slots[param]
                continue
            if param == "recipientId":
                params[param] = known_slots.get("employeeId") or ticket_facts.get("creatorId") or "SERVICE_DESK"
                continue
            if param == "message":
                params[param] = self._build_notification_message(sop, known_slots, ticket_facts)
                continue
            if param == "targetSystem" and known_slots.get("targetSystem"):
                params[param] = known_slots["targetSystem"]
                continue
            if param == "permissionLevel" and known_slots.get("permissionLevel"):
                params[param] = known_slots["permissionLevel"]
                continue
            params[param] = ""
        return params

    def _build_notification_message(self, sop: SopMetadata, known_slots: dict[str, Any], ticket_facts: dict[str, Any]) -> str:
        employee_id = known_slots.get("employeeId") or ticket_facts.get("creatorId") or "未知员工"
        system = known_slots.get("targetSystem") or known_slots.get("deviceType") or "当前场景"
        return f"工单 {ticket_facts.get('ticketId', '')} 已生成 {sop.name} 候选计划，请关注 {system} 相关处理结果。"

    def _registry_requires_approval(self, registry_value: bool | str, known_slots: dict[str, Any]) -> bool:
        if isinstance(registry_value, bool):
            return registry_value
        if str(registry_value).lower() != "conditional":
            return False
        return str(known_slots.get("permissionLevel", "")).upper() == "ADMIN"

    def _resolve_plan_risk(self, sop: SopMetadata, steps: list[PlanStep]) -> str:
        levels = [sop.risk_level, *(step.riskLevel for step in steps)]
        return max(levels, key=lambda value: {"LOW": 1, "MEDIUM": 2, "HIGH": 3, "FORBIDDEN": 4}[value])

    def _build_plan_id(self, ticket_id: str, selected_sop_id: str) -> str:
        raw = f"{ticket_id}:{selected_sop_id}".encode("utf-8")
        return "plan-" + hashlib.sha1(raw).hexdigest()[:12]
