alter table users
    add column blocked_at     timestamptz,
    add column blocked_reason varchar(500),
    add column blocked_by     uuid references users (id);

-- История назначения ролей, блокировок и отзыва сессий (FR-008, FR-054).
create table identity_audit_events
(
    id              uuid primary key,
    subject_user_id uuid        not null references users (id),
    action          varchar(40) not null check (action in ('ROLE_GRANTED', 'ROLE_REVOKED', 'ACCOUNT_BLOCKED',
                                                           'ACCOUNT_UNBLOCKED', 'SESSIONS_REVOKED')),
    -- null, если действие выполнила система, например при начальном назначении старшего модератора.
    actor_id        uuid references users (id),
    reason          varchar(500),
    payload         jsonb       not null default '{}',
    correlation_id  varchar(64),
    created_at      timestamptz not null
);

create index identity_audit_events_subject_idx on identity_audit_events (subject_user_id, created_at desc);

-- Журнал только дополняется (NFR-020): изменить или удалить запись нельзя даже владельцу таблицы.
create function identity_audit_events_append_only() returns trigger
    language plpgsql as
$$
begin
    raise exception 'identity_audit_events is append-only';
end;
$$;

create trigger identity_audit_events_append_only
    before update or delete
    on identity_audit_events
    for each row
execute function identity_audit_events_append_only();
