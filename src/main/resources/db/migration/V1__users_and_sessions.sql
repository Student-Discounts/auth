create table users
(
    id            uuid primary key,
    email         varchar(254) not null,
    password_hash varchar(255) not null,
    display_name  varchar(100) not null,
    status        varchar(20)  not null default 'ACTIVE' check (status in ('ACTIVE', 'BLOCKED')),
    created_at    timestamptz  not null
);

-- Приложение приводит email к нижнему регистру; индекс по lower() гарантирует уникальность и без этого.
create unique index users_email_uq on users (lower(email));

create table user_roles
(
    user_id    uuid        not null references users (id) on delete cascade,
    role       varchar(32) not null check (role in ('STUDENT', 'ORG_REPRESENTATIVE', 'MODERATOR', 'SENIOR_MODERATOR')),
    granted_at timestamptz not null default now(),
    granted_by uuid references users (id),
    primary key (user_id, role)
);

-- Сессия = одно устройство или вход. Хранится только SHA-256 refresh-токена, сам токен знает лишь клиент.
create table user_sessions
(
    id                          uuid primary key,
    user_id                     uuid        not null references users (id) on delete cascade,
    refresh_token_hash          varchar(64) not null,
    previous_refresh_token_hash varchar(64),
    user_agent                  varchar(255),
    created_at                  timestamptz not null,
    last_used_at                timestamptz not null,
    expires_at                  timestamptz not null,
    revoked_at                  timestamptz,
    revoke_reason               varchar(32)
);

create unique index user_sessions_refresh_token_hash_uq on user_sessions (refresh_token_hash);
create index user_sessions_previous_refresh_token_hash_idx on user_sessions (previous_refresh_token_hash)
    where previous_refresh_token_hash is not null;
create index user_sessions_active_by_user_idx on user_sessions (user_id) where revoked_at is null;
