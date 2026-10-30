# sd-auth

Сервис аутентификации платформы студенческих скидок. Сейчас реализованы регистрация и вход; выход, обновление токенов и завершение сессий (остальная часть FR-001) ещё в работе.

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

## API

| Метод и путь | Доступ | Назначение |
|---|---|---|
| `POST /api/v1/auth/register` | публично | Регистрация `{email, password, displayName}`; пользователь получает роль `STUDENT` |
| `POST /api/v1/auth/login` | публично | Вход `{email, password}`, возвращает пару токенов |
| `GET /api/v1/me` | Bearer | Профиль и роли |
| `GET /.well-known/jwks.json` | публично | Открытый ключ для проверки подписи токенов |

Ответ на вход:

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
| `invalid_credentials` | 401 | Неверный email или пароль; ответ одинаковый, чтобы не раскрывать, зарегистрирован ли адрес |
| `account_blocked` | 403 | Аккаунт заблокирован |
| `email_already_registered` | 409 | Email уже занят; регистр букв не важен |

## Токены и сессии

- **Access-токен** — JWT RS256, живёт 5 минут. Содержит `iss`, `aud`, `sub` (id пользователя), `sid` (id сессии), `roles`, `iat`, `exp`.
- **Refresh-токен** — случайная строка, сессия живёт 30 дней с момента входа. В БД хранится только SHA-256 токена. Обменять его на новую пару токенов пока нельзя: эндпоинт `refresh` ещё не реализован.
- **Пароли** хешируются Argon2id; в БД хранится строка вида `{argon2}...`.

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
| `SD_AUTH_JWT_PRIVATEKEY` / `SD_AUTH_JWT_PUBLICKEY` | не заданы | PEM-файлы ключа подписи, например `file:/run/secrets/jwt-private.pem` |

Без ключей сервис при каждом старте генерирует временную пару. После перезапуска все выданные токены становятся недействительными; для локальной разработки этого достаточно. Для общего стенда ключи генерируются один раз:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl pkey -in jwt-private.pem -pubout -out jwt-public.pem
```

Файлы `*.pem` исключены из git.

## Что пока не сделано

- Обновление токенов (`refresh`) с ротацией refresh-токена и отзывом сессии при повторном предъявлении старого токена.
- Выход (`logout`), просмотр своих сессий и завершение одной или всех сессий.
- Ограничение частоты попыток входа (FR-053).
- Блокировка аккаунта через API: проверка статуса при входе уже есть, а способа заблокировать пока нет.
- Назначение ролей и история их изменения (FR-008), представители организаций (FR-043–FR-047).
- Подтверждение email и восстановление пароля.
- Периодическая очистка истёкших и отозванных сессий.
