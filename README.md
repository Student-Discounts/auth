# sd-auth

Сервис аутентификации платформы студенческих скидок: регистрация, вход, выход, обновление токенов и завершение сессий (FR-001), назначение модераторов, блокировка аккаунтов и журнал аудита (FR-003, FR-007, FR-008).

Сервис выпускает JWT, а остальные сервисы проверяют их сами по открытому ключу и в sd-auth на каждый запрос не ходят. Роли, матрица прав и контракт токена описаны в [docs/design/RBAC.md](https://github.com/Student-Discounts/docs/blob/dev/design/RBAC.md).

## Запуск

Нужны Java 25 и PostgreSQL. Для тестов дополнительно нужен Docker.

`JAVA_HOME` менять не нужно: `maven-toolchains-plugin` сам находит установленную JDK 25 в стандартных каталогах (`~/Library/Java/JavaVirtualMachines`, `/Library/Java/JavaVirtualMachines`, SDKMAN и других) и использует её для компиляции, тестов и `spring-boot:run`. Если JDK 25 нигде не установлена, сборка остановится на шаге `select-jdk-toolchain`.

```bash
docker compose up -d        # PostgreSQL на localhost:5432 (можно взять и свой локальный)
./mvnw spring-boot:run      # http://localhost:8080
./mvnw test                 # интеграционные тесты; PostgreSQL поднимается в Docker через Testcontainers
```

Схему БД создаёт Flyway из `src/main/resources/db/migration`. Hibernate схему не меняет, а только сверяет её с сущностями.

Swagger UI: http://localhost:8080/swagger-ui.html, спецификация OpenAPI: http://localhost:8080/v3/api-docs. В продакшене их отключает `SD_AUTH_API_DOCS_ENABLED=false`. Пошаговые сценарии ручной проверки всех функций — в [MANUAL_TESTING.md](MANUAL_TESTING.md).

## API

| Метод и путь | Доступ | Назначение |
|---|---|---|
| `POST /api/v1/auth/register` | публично | Регистрация `{email, password, displayName}`; пользователь получает роль `STUDENT` |
| `POST /api/v1/auth/login` | публично | Вход `{email, password}`, возвращает пару токенов |
| `POST /api/v1/auth/refresh` | публично | Обмен `{refreshToken}` на новую пару токенов |
| `POST /api/v1/auth/logout` | Bearer | Завершить текущую сессию |
| `GET /api/v1/me` | Bearer | Профиль и роли |
| `GET /api/v1/me/sessions` | Bearer | Активные сессии; текущая помечена `current: true` |
| `DELETE /api/v1/me/sessions/{id}` | Bearer | Завершить одну свою сессию |
| `DELETE /api/v1/me/sessions` | Bearer | Завершить все свои сессии, включая текущую |
| `GET /.well-known/jwks.json` | публично | Открытый ключ для проверки подписи токенов |
| `GET /api/v1/users?email=…`, `GET /api/v1/users/{id}` | `USER_READ_ANY` | Найти пользователя; ответ со статусом и причиной блокировки |
| `PUT /api/v1/users/{id}/roles/MODERATOR` | `ROLE_MODERATOR_MANAGE` | Назначить модератора |
| `DELETE /api/v1/users/{id}/roles/MODERATOR` | `ROLE_MODERATOR_MANAGE` | Снять роль модератора |
| `POST /api/v1/users/{id}/block` | `ACCOUNT_BLOCK` | Заблокировать `{reason}`; все сессии пользователя сразу завершаются |
| `POST /api/v1/users/{id}/unblock` | `ACCOUNT_BLOCK` | Разблокировать |
| `DELETE /api/v1/users/{id}/sessions` | `SESSION_REVOKE_ANY` | Завершить все сессии пользователя |
| `GET /api/v1/users/{id}/identity-events?limit=50` | `IDENTITY_AUDIT_READ` | История ролей, блокировок и отзыва сессий, новые первыми |

Разрешения из колонки «Доступ» сейчас есть только у старшего модератора. Повторное назначение роли, блокировка уже заблокированного и разблокировка активного пользователя ничего не меняют и отвечают `204`.

Ответ на вход и обновление токенов:

```json
{
  "accessToken": "eyJraWQiOi...",
  "tokenType": "Bearer",
  "expiresIn": 300,
  "refreshToken": "DbhwUxsP9wlo...",
  "refreshExpiresIn": 2592000
}
```

Ошибки возвращаются в формате `application/problem+json` (RFC 9457). Клиенту нужно смотреть на поле `code`:

| `code` | HTTP | Когда |
|---|---|---|
| `validation_failed` | 400 | Неверные поля; подробности по каждому полю в `errors` |
| `unauthorized` | 401 | Нет access-токена, он недействителен или истёк |
| `invalid_credentials` | 401 | Неверный email или пароль; ответ одинаковый, чтобы не раскрывать, зарегистрирован ли адрес |
| `invalid_refresh_token` | 401 | Refresh-токен неизвестен, истёк, сессия завершена или токен предъявлен повторно |
| `account_blocked` | 403 | Аккаунт заблокирован |
| `access_denied` | 403 | Нет разрешения на действие. Для изменений права перепроверяются по БД, поэтому ответ будет и при ещё действующем токене, если роль уже отозвана |
| `self_management_forbidden` | 403 | Попытка изменить свои роли, заблокировать себя или завершить свои сессии через `/api/v1/users` |
| `role_not_manageable` | 403 | Через API назначается и снимается только `MODERATOR` |
| `target_not_manageable` | 403 | Старшего модератора нельзя заблокировать или завершить его сессии |
| `email_already_registered` | 409 | Email уже занят; регистр букв не важен |
| `session_not_found` | 404 | Сессия не существует или принадлежит другому пользователю |
| `user_not_found` | 404 | Пользователь не найден |

## Токены и сессии

- **Access-токен** — JWT RS256, живёт 5 минут. Содержит `iss`, `aud`, `sub` (id пользователя), `sid` (id сессии), `roles`, `iat`, `exp`.
- **Refresh-токен** — случайная строка, сессия живёт 30 дней с момента входа. Каждое обновление выдаёт новый refresh-токен, а старый перестаёт действовать. В БД хранится только SHA-256 токена.
- **Повторное использование.** Если старый refresh-токен предъявлен ещё раз, это считается кражей: сессия отзывается целиком. Поэтому клиент не должен отправлять параллельные `refresh` с одним и тем же токеном.
- **Запаздывание после выхода.** После выхода, завершения сессии или блокировки уже выданный access-токен действует до своего `exp` (не дольше 5 минут), но обновить его нельзя.
- **Пароли** хешируются Argon2id; в БД хранится строка вида `{argon2}...`.

## Роли и разрешения

Endpoint защищается разрешением, а не ролью: `@PreAuthorize("hasAuthority('ACCOUNT_BLOCK')")`. Роли из claim `roles` превращаются в разрешения в `RolePermissions` по матрице прав из RBAC.md; старший модератор получает все права модератора.

- **Первый старший модератор.** Через API роль `SENIOR_MODERATOR` выдать нельзя. Пользователь регистрируется обычным способом, затем его email указывается в `SD_AUTH_SENIOR_MODERATORS`, и роль назначается при запуске сервиса. Назначение попадает в журнал аудита без автора.
- **Когда изменения доходят до токена.** Новая или снятая роль появляется в access-токене при следующем обновлении, то есть не позже чем через 5 минут. Сам `sd-auth` при изменении ролей и блокировках перепроверяет права актора по БД и не ждёт истечения токена.
- **Журнал аудита.** `identity_audit_events` только дополняется: изменить или удалить запись не даёт триггер в БД. Изменение и запись о нём сохраняются в одной транзакции. В запись попадает `X-Correlation-ID` запроса.

## Корреляционный идентификатор

Сервис берёт `X-Correlation-ID` из запроса (до 64 символов `A-Za-z0-9._-`) или создаёт новый, возвращает его в ответе и пишет в логи и в журнал аудита. Заголовок тот же, что в submission-service.

## Как другим сервисам проверять токены

Подключите `spring-boot-starter-security-oauth2-resource-server` и добавьте в настройки:

```properties
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8080/.well-known/jwks.json
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080
spring.security.oauth2.resourceserver.jwt.audiences=student-discounts-api
spring.security.oauth2.resourceserver.jwt.authorities-claim-name=roles
spring.security.oauth2.resourceserver.jwt.authority-prefix=ROLE_
```

После этого в коде доступны `hasRole('MODERATOR')` и `@AuthenticationPrincipal Jwt jwt`, а id пользователя лежит в `jwt.getSubject()`.

## Конфигурация

| Переменная окружения | По умолчанию | Назначение |
|---|---|---|
| `SD_AUTH_DB_URL` | `jdbc:postgresql://localhost:5432/sd-auth` | Адрес БД |
| `SD_AUTH_DB_USER` / `SD_AUTH_DB_PASSWORD` | `sd_auth_user` / `admin` | Учётные данные БД |
| `SD_AUTH_ISSUER` | `http://localhost:8080` | Значение `iss` в токенах |
| `SD_AUTH_CORS_ORIGINS` | `http://localhost:5173` | Адреса фронтенда через запятую |
| `SD_AUTH_API_DOCS_ENABLED` | `true` | Swagger UI и `/v3/api-docs` |
| `SD_AUTH_SENIOR_MODERATORS` | не задано | Email зарегистрированных пользователей через запятую, которым при запуске назначается `SENIOR_MODERATOR` |
| `SD_AUTH_JWT_PRIVATEKEY` / `SD_AUTH_JWT_PUBLICKEY` | не заданы | PEM-файлы ключа подписи, например `file:/run/secrets/jwt-private.pem` |

Без ключей сервис при каждом старте генерирует временную пару. После перезапуска все выданные токены становятся недействительными; для локальной разработки этого достаточно. Для общего стенда ключи генерируются один раз:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl pkey -in jwt-private.pem -pubout -out jwt-public.pem
```

Файлы `*.pem` исключены из git.

## Что пока не сделано

- Ограничение частоты попыток входа (FR-053): планируется на уровне API Gateway.
- Представители организаций: запрос связи, тестовая верификация и `org_ids` в токене (FR-005, FR-043–FR-047).
- `GET /internal/v1/users/{id}/authz` для проверки актуальных прав из других сервисов (условие C-7): сначала нужно договориться, как сервисы аутентифицируются друг перед другом.
- Подтверждение email и восстановление пароля.
- Периодическая очистка истёкших и отозванных сессий.
