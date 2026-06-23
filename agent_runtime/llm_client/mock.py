"""Phase 2 使用规则驱动的 Mock LLM，先确保节点输入输出契约稳定。"""

from __future__ import annotations

import re
from dataclasses import dataclass

from agent_runtime.llm_client.base import LLMRequest


@dataclass(slots=True)
class MockLLMClient:
    def invoke(self, request: LLMRequest) -> dict:
        text = _build_text(request.context).lower()
        if request.node_name == "classify_intent":
            if "vpn" in text:
                return {"intent": "VPN_CONNECTION_ISSUE", "confidence": 0.97, "reasoning": "命中 VPN 关键词"}
            if any(token in text for token in ("权限", "permission", "grant access", "开通")):
                return {"intent": "PERMISSION_REQUEST", "confidence": 0.95, "reasoning": "命中权限申请关键词"}
            if any(token in text for token in ("登录", "login", "sign in", "账号", "锁定", "password", "invalid credentials")):
                return {"intent": "ACCOUNT_LOGIN_ISSUE", "confidence": 0.91, "reasoning": "命中登录异常关键词"}
            return {"intent": "UNKNOWN", "confidence": 0.52, "reasoning": "未命中 MVP 支持范围"}

        if request.node_name == "extract_slots":
            intent = request.context.get("intent", "UNKNOWN")
            slots = {}
            employee = re.search(r"\b([A-Z]\d{4,})\b", _build_text(request.context), re.IGNORECASE)
            system = re.search(r"(OA|ERP|CRM|SAP|JIRA|GITLAB|EMAIL|邮箱|BI|HR系统)", _build_text(request.context), re.IGNORECASE)
            if employee:
                slots["employeeId"] = employee.group(1).upper()
            if system:
                slots["targetSystem"] = system.group(1).upper().replace("邮箱", "EMAIL")
            if "认证失败" in _build_text(request.context):
                slots["errorMessage"] = "认证失败"
            if "账号已锁定" in _build_text(request.context):
                slots["errorMessage"] = "账号已锁定"
            if "invalid credentials" in text:
                slots["errorMessage"] = "invalid credentials"
            if "登录失败" in _build_text(request.context):
                slots["errorMessage"] = "登录失败"
            if "sign in failed" in text:
                slots["errorMessage"] = "sign in failed"
            if "未开通" in _build_text(request.context):
                slots["errorMessage"] = "VPN 未开通"
            if "access denied" in text:
                slots["errorMessage"] = "access denied"
            if "无权限" in _build_text(request.context):
                slots["errorMessage"] = "无权限访问"
            if "windows" in text or "电脑" in text:
                slots["deviceType"] = "WINDOWS"
            if "mac" in text:
                slots["deviceType"] = "MAC"
            if "iphone" in text or "ios" in text:
                slots["deviceType"] = "IOS"
            if "android" in text or "安卓" in text:
                slots["deviceType"] = "ANDROID"
            if "只读" in text:
                slots["permissionLevel"] = "READ_ONLY"
            if "写入" in text:
                slots["permissionLevel"] = "READ_WRITE"
            if "管理员" in text or "admin" in text:
                slots["permissionLevel"] = "ADMIN"
            if "用于" in _build_text(request.context):
                slots["reason"] = _build_text(request.context).split("用于", 1)[1].split("。", 1)[0].strip()
            if "因为" in _build_text(request.context):
                slots["reason"] = _build_text(request.context).split("因为", 1)[1].split("。", 1)[0].strip()
            duration = re.search(r"([0-9一二两三四五六七八九十]+\s*(天|周|个月|月|小时))", _build_text(request.context))
            if duration:
                slots["duration"] = duration.group(1)
            missing = _resolve_missing(intent, slots)
            return {"slots": slots, "missingSlots": missing, "reasoning": "根据规则抽取槽位"}

        if request.node_name == "generate_question":
            intent = request.context.get("intent", "UNKNOWN")
            missing_slots = request.context.get("missingSlots", [])
            if intent == "UNKNOWN":
                return {
                    "shouldAskUser": True,
                    "question": "这个问题当前不在 MVP 支持范围内，建议转人工处理。",
                    "nextStep": "ESCALATE_TO_HUMAN",
                }
            if not missing_slots:
                return {"shouldAskUser": False, "question": "", "nextStep": "UNDERSTANDING_READY"}
            labels = "、".join(missing_slots)
            return {
                "shouldAskUser": True,
                "question": f"为了继续处理，请补充：{labels}。",
                "nextStep": "ASK_USER_FOR_MISSING_SLOTS",
            }

        raise ValueError(f"Unsupported node: {request.node_name}")


def _build_text(context: dict) -> str:
    parts = []
    ticket_facts = context.get("ticket_facts", {})
    parts.append(str(ticket_facts.get("title", "")))
    parts.append(str(ticket_facts.get("description", "")))
    for message in context.get("recent_messages", []):
        parts.append(str(message.get("content", "")))
    return "\n".join(parts)


def _resolve_missing(intent: str, slots: dict) -> list[str]:
    required = {
        "ACCOUNT_LOGIN_ISSUE": ["employeeId", "targetSystem"],
        "VPN_CONNECTION_ISSUE": ["employeeId", "deviceType", "errorMessage"],
        "PERMISSION_REQUEST": ["employeeId", "targetSystem", "permissionLevel", "reason", "duration"],
        "UNKNOWN": [],
    }[intent]
    return [slot for slot in required if not slots.get(slot)]
