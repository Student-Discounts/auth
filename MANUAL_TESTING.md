# Ручная проверка sd-auth

Сценарии для проверки всех реализованных функций через Swagger UI. Автоматически те же сценарии проверяет `./mvnw test`: 27 тестов на PostgreSQL в Testcontainers.

## Подготовка

1. Запустить PostgreSQL (`docker compose up -d` или свой локальный) и сервис:
   ```bash
   SD_AUTH_SENIOR_MODERATORS=senior@example.com ./mvnw spring-boot:run
   ```
2. Зарегистрировать тестовых пользователей. Это можно сделать в Swagger UI через `POST /api/v1/auth/register` или командой:
   ```bash
   for e in senior anna boris; do
     curl -s -X POST localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' \
       -d "{\"email\":\"$e@example.com\",\"password\":\"password123\",\"displayName\":\"$e\"}"; echo
   done
   ```
3. Перезапустить сервис: роль `SENIOR_MODERATOR` пользователь `senior@example.com` получает при запуске, если он уже зарегистрирован.
4. Открыть http://localhost:8080/swagger-ui.html.

**Как действовать от имени пользователя.** Выполнить `POST /api/v1/auth/login`, скопировать `accessToken` из ответа, нажать **Authorize** вверху страницы и вставить токен без слова `Bearer`. Чтобы сменить пользователя, в том же окне нажать **Logout** и вставить другой токен. Токен действует 5 минут; после этого нужно снова войти или вызвать `refresh`.

**Где взять id пользователя.** Под старшим модератором: `GET /api/v1/users?email=anna@example.com`.

Все ошибки приходят в формате `application/problem+json`, ориентироваться нужно на поле `code`.

## 1. Регистрация и вход (FR-001)

| № | Действие | Ожидается |
|---|---|---|
| 1.1 | `POST /api/v1/auth/register` с новым email, паролем от 8 символов и `displayName` | `201`, в `roles` только `STUDENT`, пароля и хеша в ответе нет |
| 1.2 | Повторить регистрацию с тем же email в другом регистре, например `Anna@Example.com` | `409`, `code: email_already_registered` |
| 1.3 | Регистрация с `email: "x"`, `password: "123"`, пустым `displayName` | `400`, `code: validation_failed`, в `errors` ошибки по каждому из трёх полей |
| 1.4 | `POST /api/v1/auth/login` с верным паролем | `200`, `accessToken`, `refreshToken`, `expiresIn: 300` |
| 1.5 | Вход с неверным паролем, затем с несуществующим email | Оба раза одинаковый `401`, `code: invalid_credentials` |
| 1.6 | `GET /api/v1/me` без Authorize | `401`, `code: unauthorized` |
| 1.7 | `GET /api/v1/me` после Authorize | `200`, свой профиль и роли |
| 1.8 | `GET /.well-known/jwks.json` | Один ключ `RSA`, поля `d` (закрытая часть) нет |
| 1.9 | Расшифровать `accessToken`, например на jwt.io (токен тестовый) | Есть `iss`, `aud: ["student-discounts-api"]`, `sub` = id пользователя, `sid`, `roles`; `exp − iat = 300` |

## 2. Обновление токенов, выход и сессии (FR-001)

| № | Действие | Ожидается |
|---|---|---|
| 2.1 | `POST /api/v1/auth/refresh` с `refreshToken` из входа | `200`, новая пара, `refreshToken` отличается от прежнего |
| 2.2 | Ещё раз `refresh` со **старым** `refreshToken` | `401`, `code: invalid_refresh_token` |
| 2.3 | `refresh` с токеном, полученным в 2.1 | Тоже `401`: повторное использование старого токена отозвало всю сессию |
| 2.4 | Войти дважды под одним пользователем, `GET /api/v1/me/sessions` | Две сессии, у одной `current: true` |
| 2.5 | `DELETE /api/v1/me/sessions/{sessionId}` для второй сессии, затем `refresh` её токеном | `204`, затем `401` |
| 2.6 | `POST /api/v1/auth/logout`, затем `refresh` токеном этой сессии | `204`, затем `401`. Access-токен ещё работает до своего `exp`: это ожидаемо |
| 2.7 | `DELETE /api/v1/me/sessions` | `204`, все сессии пользователя завершены |

## 3. Назначение ролей (FR-003, FR-008)

Под `senior@example.com`:

| № | Действие | Ожидается |
|---|---|---|
| 3.1 | `PUT /api/v1/users/{id Анны}/roles/MODERATOR` | `204` |
| 3.2 | Под Анной `refresh`, расшифровать новый `accessToken` | В `roles` есть `MODERATOR` |
| 3.3 | Повторить 3.1 | `204`, в истории Анны новое событие не появилось |
| 3.4 | `PUT /api/v1/users/{id Анны}/roles/SENIOR_MODERATOR` | `403`, `code: role_not_manageable` |
| 3.5 | `PUT /api/v1/users/{свой id}/roles/MODERATOR` | `403`, `code: self_management_forbidden` |
| 3.6 | Под Анной-модератором `PUT /api/v1/users/{id Бориса}/roles/MODERATOR` | `403`, `code: access_denied`: модератор не назначает модераторов |
| 3.7 | Под Борисом-студентом `GET /api/v1/users?email=anna@example.com` | `403`, `code: access_denied` |
| 3.8 | Под senior `DELETE /api/v1/users/{id Анны}/roles/MODERATOR`, затем под Анной `refresh` | `204`; в новом токене снова только `STUDENT` |

## 4. Блокировка (FR-007)

| № | Действие | Ожидается |
|---|---|---|
| 4.1 | Анна входит; сохранить её `refreshToken` | `200` |
| 4.2 | Под senior `POST /api/v1/users/{id Анны}/block` с `{"reason": "Спам в заявках"}` | `204` |
| 4.3 | `refresh` сохранённым токеном Анны | `401`: все сессии завершены при блокировке |
| 4.4 | Вход Анны | `403`, `code: account_blocked` |
| 4.5 | Под senior `GET /api/v1/users/{id Анны}` | `status: BLOCKED`, `blockedReason`, `blockedBy` = id senior |
| 4.6 | `block` с `{"reason": " "}` | `400`, `code: validation_failed` |
| 4.7 | `block` своего id | `403`, `code: self_management_forbidden` |
| 4.8 | `POST /api/v1/users/{id Анны}/unblock`, затем вход Анны | `204`, затем `200` |

## 5. Завершение чужих сессий

| № | Действие | Ожидается |
|---|---|---|
| 5.1 | Борис входит дважды; под senior `DELETE /api/v1/users/{id Бориса}/sessions` | `204` |
| 5.2 | `refresh` обоими токенами Бориса | Оба `401` |
| 5.3 | Вход Бориса | `200`: аккаунт не заблокирован |

## 6. Журнал аудита (FR-008, FR-054, NFR-020)

| № | Действие | Ожидается |
|---|---|---|
| 6.1 | Под senior `GET /api/v1/users/{id Анны}/identity-events` | События новыми первыми: `ACCOUNT_UNBLOCKED`, `ACCOUNT_BLOCKED` с причиной и `payload.revokedSessions`, `ROLE_REVOKED`, `ROLE_GRANTED`; у каждого `actorId` = id senior |
| 6.2 | `GET /api/v1/users/{id senior}/identity-events` | `ROLE_GRANTED` с `SENIOR_MODERATOR`, `actorId: null`: назначен при запуске |
| 6.3 | Корреляционный идентификатор (в Swagger свой заголовок не задать, поэтому через curl): `curl -i -X PUT localhost:8080/api/v1/users/{id}/roles/MODERATOR -H "Authorization: Bearer …" -H "X-Correlation-ID: check-6-3"` | В ответе заголовок `X-Correlation-ID: check-6-3`, в истории у события `correlationId: check-6-3` |
| 6.4 | `psql -h localhost -U sd_auth_user -d sd-auth -c "update identity_audit_events set reason = 'x'"` и то же с `delete from identity_audit_events` | `ERROR: identity_audit_events is append-only` |

## 7. Отзыв прав действует сразу (условие C-7)

| № | Действие | Ожидается |
|---|---|---|
| 7.1 | Под senior получить токен и нажать Authorize | — |
| 7.2 | Отозвать роль в БД: `psql … -c "delete from user_roles where role = 'SENIOR_MODERATOR'"` | — |
| 7.3 | С тем же ещё действующим токеном `PUT /api/v1/users/{id Анны}/roles/MODERATOR` | `403`, `code: access_denied`: права перепроверены по БД, ждать истечения токена не нужно |
| 7.4 | Вернуть роль: перезапустить сервис с `SD_AUTH_SENIOR_MODERATORS=senior@example.com` | При запуске роль назначается снова |
