create table if not exists conversation_message (
    id bigint auto_increment primary key,
    ticket_id varchar(32) not null,
    role varchar(32) not null,
    content varchar(4000) not null,
    message_type varchar(32) not null,
    created_at timestamp not null,
    constraint fk_conversation_message_ticket
        foreign key (ticket_id) references ticket(ticket_id) on delete cascade
);

create table if not exists ticket_context (
    id bigint auto_increment primary key,
    ticket_id varchar(32) not null,
    intent varchar(32) not null,
    slots_json varchar(4000) not null,
    missing_slots_json varchar(2000) not null,
    matched_sop_ids_json varchar(2000) not null,
    current_plan_json varchar(4000) not null,
    risk_level varchar(16) not null,
    last_agent_step varchar(64) not null,
    updated_at timestamp not null,
    constraint uk_ticket_context_ticket_id unique (ticket_id),
    constraint fk_ticket_context_ticket
        foreign key (ticket_id) references ticket(ticket_id) on delete cascade
);

create table if not exists agent_step_log (
    id bigint auto_increment primary key,
    ticket_id varchar(32) not null,
    node_name varchar(64) not null,
    input_context_hash varchar(64) not null,
    output_json varchar(4000) not null,
    status varchar(32) not null,
    error_message varchar(1000),
    created_at timestamp not null,
    constraint fk_agent_step_log_ticket
        foreign key (ticket_id) references ticket(ticket_id) on delete cascade
);
