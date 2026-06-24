from agent_runtime.runtime_cli import run_analysis


def test_runtime_cli_should_return_plan_for_complete_account_case():
    payload = {
        "ticket_facts": {
            "ticketId": "T-CLI-001",
            "title": "OA 登录失败",
            "description": "我的 OA 账号被锁定了，工号是 E10086",
            "creatorId": "U1001",
            "creatorRole": "EMPLOYEE",
            "status": "NEW",
        },
        "current_state": {"status": "NEW", "version": 0},
        "known_slots": {"targetSystem": "OA"},
        "missing_slots": [],
        "recent_messages": [
            {"role": "USER", "content": "我的 OA 账号被锁定了，工号是 E10086", "messageType": "TICKET_DESCRIPTION"}
        ],
        "conversation_summary": "",
        "matched_sops": [],
        "tool_evidence": [],
        "approval_context": {},
        "risk_policy": {},
        "current_node": "INIT",
    }

    result = run_analysis(payload)

    assert result["workflowMode"] in {"sequential_fallback", "langgraph"}
    assert result["intent"]["intent"] == "ACCOUNT_LOGIN_ISSUE"
    assert result["plan"]["ticketId"] == "T-CLI-001"
    assert result["retrieval"]["selectedSopId"] == "SOP-ACC-LOCKED-001"
