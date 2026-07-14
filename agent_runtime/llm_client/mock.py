"""使用规则驱动的 Mock LLM，优先保证节点契约与演示链路稳定。

MockLLMClient 是真实 LLM 的离线兜底实现：
- 意图分类：关键词匹配
- 槽位抽取：正则与规则匹配
- 追问生成：基于缺失槽位的模板文案

共享的槽位解析逻辑（resolve_missing / build_text / SLOT_LABELS）已抽取到 slot_utils，
MockLLMClient 和真实 LLM 客户端共用同一套必填槽位定义。
"""

from __future__ import annotations

import re
from dataclasses import dataclass

from agent_runtime.llm_client.base import LLMRequest
from agent_runtime.llm_client.slot_utils import SLOT_LABELS, build_text, resolve_missing


@dataclass(slots=True)
class MockLLMClient:
    def invoke(self, request: LLMRequest) -> dict:
        text = build_text(request.context)
        lower = text.lower()

        if request.node_name == "classify_intent":
            if "vpn" in lower:
                return {"intent": "VPN_CONNECTION_ISSUE", "confidence": 0.97, "reasoning": "命中 VPN 关键字"}
            if any(
                token in lower
                for token in (
                    "权限",
                    "permission",
                    "grant access",
                    "开通",
                    "管理员权限",
                    "admin",
                    "administrator",
                    "只读",
                    "read only",
                    "read-only",
                    "写入",
                    "read write",
                    "read-write",
                )
            ):
                return {"intent": "PERMISSION_REQUEST", "confidence": 0.95, "reasoning": "命中权限申请关键字"}
            if any(
                token in lower
                for token in (
                    "登录",
                    "login",
                    "sign in",
                    "账号",
                    "账户",
                    "邮箱",
                    "email",
                    "邮件",
                    "锁定",
                    "locked",
                    "password",
                    "invalid credentials",
                )
            ):
                return {"intent": "ACCOUNT_LOGIN_ISSUE", "confidence": 0.91, "reasoning": "命中登录异常关键字"}
            return {"intent": "UNKNOWN", "confidence": 0.52, "reasoning": "未命中 MVP 支持范围"}

        if request.node_name == "extract_slots":
            intent = request.context.get("intent", "UNKNOWN")
            slots = _extract_slots(text, lower)
            missing = resolve_missing(intent, slots)
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
            labels = "、".join(SLOT_LABELS.get(slot, slot) for slot in missing_slots)
            return {
                "shouldAskUser": True,
                "question": f"为了继续处理，请补充：{labels}。",
                "nextStep": "ASK_USER_FOR_MISSING_SLOTS",
            }

        raise ValueError(f"Unsupported node: {request.node_name}")


def _extract_slots(text: str, lower: str) -> dict:
    slots: dict[str, object] = {}

    employee = re.search(r"\b([A-Z]\d{4,})\b", text, re.IGNORECASE)
    if employee:
        slots["employeeId"] = employee.group(1).upper()

    if any(token in lower for token in ("production database", "prod database", "生产数据库", "生产库")):
        slots["targetSystem"] = "production database"
    else:
        system = re.search(r"(OA|ERP|CRM|SAP|JIRA|GITLAB|EMAIL|BI|HR系统|数据库|邮箱)", text, re.IGNORECASE)
        if system:
            raw = system.group(1)
            normalized = raw.upper()
            if raw in {"邮箱"}:
                slots["targetSystem"] = "EMAIL"
            elif raw in {"数据库"}:
                slots["targetSystem"] = "database"
            elif raw == "HR系统":
                slots["targetSystem"] = "HR系统"
            else:
                slots["targetSystem"] = normalized

    if any(token in lower for token in ("账号被锁定", "账号已锁定", "账户被锁定", "账户已锁定", "locked")):
        slots["errorMessage"] = "账号已锁定"
    elif any(token in lower for token in ("认证失败", "authentication failed")):
        slots["errorMessage"] = "认证失败"
    elif any(token in lower for token in ("登录失败", "login failed", "sign in failed")):
        slots["errorMessage"] = "登录失败"
    elif "invalid credentials" in lower:
        slots["errorMessage"] = "invalid credentials"
    elif any(token in lower for token in ("未开通", "无权限", "access denied")):
        slots["errorMessage"] = "access denied"

    if any(token in lower for token in ("iphone", "ios")):
        slots["deviceType"] = "IOS"
    elif any(token in lower for token in ("android", "安卓")):
        slots["deviceType"] = "ANDROID"
    elif "手机" in lower:
        slots["deviceType"] = "MOBILE"
    elif any(token in lower for token in ("windows", "电脑", "pc")):
        slots["deviceType"] = "WINDOWS"
    elif "mac" in lower:
        slots["deviceType"] = "MAC"

    if any(token in lower for token in ("管理员", "admin", "administrator")):
        slots["permissionLevel"] = "ADMIN"
    elif any(token in lower for token in ("写入", "read write", "read-write")):
        slots["permissionLevel"] = "READ_WRITE"
    elif any(token in lower for token in ("只读", "read only", "read-only")):
        slots["permissionLevel"] = "READ_ONLY"

    if "用于" in text:
        slots["reason"] = _extract_segment(text, "用于")
    elif "因为" in text:
        slots["reason"] = _extract_segment(text, "因为")
    elif "原因是" in text:
        slots["reason"] = _extract_segment(text, "原因是")
    elif "由于" in text:
        slots["reason"] = _extract_segment(text, "由于")

    duration = re.search(r"([0-9一二两三四五六七八九十半]+\s*(天|周|个月|月|小时))", text)
    if duration:
        slots["duration"] = duration.group(1)

    if any(
        token in lower
        for token in ("换过手机", "换手机", "昨天换过手机", "刚换手机", "更换手机", "换绑", "重新绑定", "重置mfa", "重置 mfa")
    ):
        slots["mfaRecentlyChanged"] = True

    return slots


def _extract_segment(text: str, marker: str) -> str:
    fragment = text.split(marker, 1)[1]
    for delimiter in ("。", "，", ",", ";", "；"):
        if delimiter in fragment:
            fragment = fragment.split(delimiter, 1)[0]
            break
    return fragment.strip(" ：:。；，,")
