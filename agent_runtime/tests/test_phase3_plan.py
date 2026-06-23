import json
from pathlib import Path

from agent_runtime.context.context_builder import ContextBuilder
from agent_runtime.graph.workflow import run_workflow
from agent_runtime.llm_client.mock import MockLLMClient
from agent_runtime.models import CandidatePlan
from agent_runtime.plan_generator import PlanSchemaValidator
from agent_runtime.sop_catalog import get_seed_sops
from agent_runtime.tool_registry import is_registered_tool


def test_seed_sops_should_satisfy_phase3_metadata_contract():
    sops = get_seed_sops()
    assert len(sops) >= 10
    required_names = {
        "账号锁定处理 SOP",
        "登录异常处理 SOP",
        "VPN 认证失败 SOP",
        "VPN 权限缺失 SOP",
        "MFA 设备更换 SOP",
        "Jira 普通权限申请 SOP",
        "GitLab 普通权限申请 SOP",
        "生产系统管理员权限申请 SOP",
        "邮箱无法登录 SOP",
        "高风险权限审批 SOP",
    }
    assert required_names.issubset({sop.name for sop in sops})
    for sop in sops:
        assert sop.sop_id
        assert sop.intent
        assert sop.required_slots is not None
        assert sop.allowed_tools
        assert sop.auto_executable_steps is not None
        assert sop.approval_required_steps is not None
        assert sop.escalation_rules


def test_phase3_sample_set_should_meet_hit_rate_and_plan_schema_targets():
    samples = json.loads(Path("agent_runtime/tests/phase3_plan_samples.json").read_text(encoding="utf-8"))
    builder = ContextBuilder()
    client = MockLLMClient()
    validator = PlanSchemaValidator()

    hit_count = 0
    plan_valid_count = 0
    unregistered_tool_count = 0
    dangerous_direct_execution_count = 0

    for index, sample in enumerate(samples, start=1):
        context = builder.build(
            ticket={
                "ticketId": f"T-P3-{index:03d}",
                "title": sample["title"],
                "description": sample["description"],
                "status": "PLANNING",
                "version": 0,
            },
            ticket_context=None,
            recent_messages=[{"content": sample["description"]}],
        )
        result = run_workflow(client, context)
        assert "retrieval" in result, f"样本 {index} 未生成 SOP 检索结果"
        assert "plan" in result, f"样本 {index} 未生成 Candidate Plan"

        if result["retrieval"]["selectedSopId"] == sample["expectedSopId"]:
            hit_count += 1

        actions = [step["action"] for step in result["plan"]["steps"]]
        assert actions == sample["expectedActions"]

        validator.validate(result["plan"])
        CandidatePlan.model_validate(result["plan"])
        plan_valid_count += 1

        for step in result["plan"]["steps"]:
            if not is_registered_tool(step["tool"], step["action"]):
                unregistered_tool_count += 1
            if step["riskLevel"] == "HIGH" and step["actionType"] == "WRITE" and step["requiredApproval"] is False:
                dangerous_direct_execution_count += 1

    sample_count = len(samples)
    hit_rate = hit_count / sample_count
    plan_valid_rate = plan_valid_count / sample_count

    assert hit_rate >= 0.8
    assert plan_valid_rate >= 0.9
    assert unregistered_tool_count == 0
    assert dangerous_direct_execution_count == 0


def test_production_admin_request_should_require_approval():
    builder = ContextBuilder()
    client = MockLLMClient()
    context = builder.build(
        ticket={
            "ticketId": "T-P3-HIGH-001",
            "title": "申请生产 ERP 管理员权限",
            "description": "员工编号 U8801，需要申请生产 ERP 管理员权限3天，因为要执行上线变更。",
            "status": "PLANNING",
            "version": 0,
        },
        ticket_context=None,
        recent_messages=[{"content": "员工编号 U8801，需要申请生产 ERP 管理员权限3天，因为要执行上线变更。"}],
    )

    result = run_workflow(client, context)
    assert result["retrieval"]["selectedSopId"] == "SOP-PERM-HIGH-RISK-APPROVAL-001"
    approval_steps = [step for step in result["plan"]["steps"] if step["requiredApproval"]]
    assert approval_steps
    assert approval_steps[-1]["action"] == "grantPermission"


def test_workflow_should_not_generate_plan_when_required_slots_are_missing():
    builder = ContextBuilder()
    client = MockLLMClient()
    context = builder.build(
        ticket={
            "ticketId": "T-P3-MISSING-001",
            "title": "OA 登录失败",
            "description": "今天登录 OA 提示账号已锁定。",
            "status": "TRIAGING",
            "version": 0,
        },
        ticket_context=None,
        recent_messages=[{"content": "今天登录 OA 提示账号已锁定。"}],
    )

    result = run_workflow(client, context)
    assert result["question"]["shouldAskUser"] is True
    assert "plan" not in result
