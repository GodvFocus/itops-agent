create table if not exists idempotency_record (
    id bigint auto_increment primary key,
    idem_key varchar(255) not null,
    ticket_id varchar(32) not null,
    plan_id varchar(64) not null,
    step_no int not null,
    tool_name varchar(64) not null,
    action_name varchar(64) not null,
    status varchar(32) not null,
    result_json varchar(4000),
    error_message varchar(1000),
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_idempotency_record_idem_key unique (idem_key)
);

create table if not exists tool_call_log (
    id bigint auto_increment primary key,
    ticket_id varchar(32) not null,
    plan_id varchar(64) not null,
    step_no int not null,
    tool_name varchar(64) not null,
    action_name varchar(64) not null,
    action_type varchar(16) not null,
    idem_key varchar(255),
    status varchar(32) not null,
    decision varchar(32) not null,
    request_json varchar(4000),
    response_json varchar(4000),
    error_message varchar(1000),
    attempt_no int not null default 1,
    created_at timestamp not null,
    updated_at timestamp not null,
    index idx_tool_call_log_ticket_created (ticket_id, created_at)
);
