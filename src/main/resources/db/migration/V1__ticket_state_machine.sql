create table if not exists ticket (
    ticket_id varchar(32) primary key,
    title varchar(200) not null,
    description varchar(4000) not null,
    creator_id varchar(64) not null,
    creator_role varchar(32) not null,
    status varchar(32) not null,
    intent varchar(32) not null,
    priority varchar(16) not null,
    risk_level varchar(16) not null,
    assigned_to varchar(64),
    version bigint not null default 0,
    created_at timestamp not null,
    updated_at timestamp not null,
    closed_at timestamp null
);

create table if not exists ticket_status_history (
    id bigint auto_increment primary key,
    ticket_id varchar(32) not null,
    from_status varchar(32),
    to_status varchar(32) not null,
    actor_id varchar(64) not null,
    actor_role varchar(32) not null,
    comment_text varchar(1000),
    created_at timestamp not null,
    constraint fk_ticket_status_history_ticket
        foreign key (ticket_id) references ticket(ticket_id) on delete cascade
);

create table if not exists audit_log (
    id bigint auto_increment primary key,
    ticket_id varchar(32) not null,
    actor_type varchar(32) not null,
    actor_id varchar(64) not null,
    action varchar(64) not null,
    target_type varchar(32) not null,
    target_id varchar(64) not null,
    detail_json varchar(4000) not null,
    created_at timestamp not null,
    constraint fk_audit_log_ticket
        foreign key (ticket_id) references ticket(ticket_id) on delete cascade
);
