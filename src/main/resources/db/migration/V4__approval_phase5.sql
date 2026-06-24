create table if not exists approval_task (
    id bigint auto_increment primary key,
    approval_id varchar(64) not null,
    ticket_id varchar(32) not null,
    plan_id varchar(64) not null,
    status varchar(32) not null,
    approval_type varchar(32) not null,
    requested_by varchar(64) not null,
    requested_reason varchar(1000) not null,
    plan_json varchar(4000) not null,
    context_json varchar(2000),
    approver_id varchar(64),
    approver_comment varchar(1000),
    created_at timestamp not null,
    decided_at timestamp null,
    updated_at timestamp not null,
    constraint uk_approval_task_approval_id unique (approval_id),
    constraint fk_approval_task_ticket
        foreign key (ticket_id) references ticket(ticket_id) on delete cascade
);
