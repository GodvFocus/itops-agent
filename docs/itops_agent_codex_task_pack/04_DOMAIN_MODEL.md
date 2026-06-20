# Domain Model

## Ticket Status

```text
NEW
TRIAGING
NEED_MORE_INFO
PLANNING
PLAN_VALIDATING
WAITING_APPROVAL
EXECUTING
WAITING_USER_CONFIRM
RESOLVED
ESCALATED
FAILED
MANUAL_TAKEOVER
CLOSED
```

## Allowed State Transitions

```text
NEW -> TRIAGING
TRIAGING -> NEED_MORE_INFO
TRIAGING -> PLANNING
NEED_MORE_INFO -> TRIAGING
PLANNING -> PLAN_VALIDATING
PLAN_VALIDATING -> EXECUTING
PLAN_VALIDATING -> WAITING_APPROVAL
PLAN_VALIDATING -> ESCALATED
WAITING_APPROVAL -> EXECUTING
WAITING_APPROVAL -> ESCALATED
EXECUTING -> WAITING_USER_CONFIRM
EXECUTING -> FAILED
EXECUTING -> ESCALATED
WAITING_USER_CONFIRM -> RESOLVED
WAITING_USER_CONFIRM -> TRIAGING
RESOLVED -> CLOSED
FAILED -> ESCALATED
ANY_ACTIVE_STATE -> MANUAL_TAKEOVER
```

ANY_ACTIVE_STATE 不包括 CLOSED。

## Intents

```text
ACCOUNT_LOGIN_ISSUE
VPN_CONNECTION_ISSUE
PERMISSION_REQUEST
UNKNOWN
```

## Risk Level

```text
LOW
MEDIUM
HIGH
FORBIDDEN
```

## Action Type

```text
READ
WRITE
APPROVAL_REQUIRED
FORBIDDEN
```

## Harness Decision

```text
APPROVED
NEED_APPROVAL
REJECTED
ESCALATE
```

## Suggested MySQL Tables

### ticket

- id
- title
- description
- creator_id
- status
- intent
- priority
- risk_level
- assigned_to
- created_at
- updated_at
- closed_at

### ticket_context

- id
- ticket_id
- intent
- slots_json
- missing_slots_json
- matched_sop_ids_json
- current_plan_json
- risk_level
- last_agent_step
- updated_at

### conversation_message

- id
- ticket_id
- role
- content
- message_type
- created_at

### conversation_summary

- id
- ticket_id
- summary_type
- summary_content
- confirmed_facts_json
- open_questions_json
- covered_message_start_id
- covered_message_end_id
- created_at

### agent_step_log

- id
- ticket_id
- node_name
- input_context_hash
- output_json
- status
- error_message
- created_at

### tool_call_log

- id
- ticket_id
- plan_id
- step_no
- tool_name
- action_name
- action_type
- request_json
- response_json
- status
- risk_level
- idem_key
- created_at
- updated_at

### approval_task

- id
- ticket_id
- plan_id
- requested_action_json
- approval_type
- status
- approver_id
- reason
- created_at
- decided_at

### idempotency_record

- id
- idem_key
- business_type
- business_id
- request_hash
- status
- response_snapshot_json
- created_at
- updated_at

### audit_log

- id
- ticket_id
- actor_type
- actor_id
- action
- target_type
- target_id
- detail_json
- created_at

### sop_metadata

- id
- sop_id
- name
- intent
- risk_level
- required_slots_json
- allowed_tools_json
- auto_executable_steps_json
- approval_required_steps_json
- escalation_rules_json
- version
- enabled
- created_at
- updated_at
