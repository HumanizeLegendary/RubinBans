# PluginBans

Современное ядро наказаний для Paper 1.21–1.21.10 (Java 21) с поддержкой Velocity, SQLite/MySQL, REST-эндпоинтом и Discord-синхронизацией (логика).

## Размер кодовой базы

Общее количество строк Java-кода: **9584**  
Подсчёт: `rg --files -g '*.java' | xargs wc -l`

## Тесты сборки

При `mvn package` выполняются тесты модуля `core`, включая проверки БД для банов и мутов:
`core/src/test/java/com/pluginbans/core/JdbcPunishmentRepositoryTest.java`.

## Команды

| Команда | Описание |
| --- | --- |
| `/ban <игрок|uuid> <длительность> <причина>` | Блокировка игрока. |
| `/tempban <игрок|uuid> <длительность> <причина>` | Временная блокировка игрока. |
| `/ipban <игрок|uuid> <длительность> <причина>` | IP-бан. |
| `/mute <игрок|uuid> <длительность> <причина>` | Блокировка чата. |
| `/warn <игрок|uuid> <причина(1|2)>` | Предупреждение (длительность по конфигу, только разрешенные причины). |
| `/punish <игрок|uuid>` | GUI-меню наказаний из `config.yml`. |
| `/punish <uuid> <тип> <длительность> <причина>` | Ручная выдача наказания. |
| `/checkpunish <id>` | Проверка наказания по ID. |
| `/playercheckpunish <игрок|uuid>` | Показать все наказания игрока (активные + история). |
| `/unpunish <id> [причина]` | Снятие наказания по ID. |

`/checkpunish` показывает кнопку `РАЗБАНИТЬ` для активного наказания.

Длительность: `1d2h30m`, `15m`, `perm`/`permanent`/`навсегда` для бессрочных.

## Права

* `bans.ban`
* `bans.tempban`
* `bans.ipban`
* `bans.mute`
* `bans.warn`
* `bans.punish`
* `bans.check`
* `bans.unpunish`
* `bans.fullaccess` — доступ ко всем операциям.

## База данных

Поддерживаются SQLite (по умолчанию) и MySQL через HikariCP.

`config.yml`:
```yaml
database:
  type: SQLITE
  pool-size: 10
  sqlite:
    file: pluginbans.db
  mysql:
    host: localhost
    port: 3306
    database: pluginbans
    user: root
    password: ""
```

## Принцип настройки

1. Выберите хранилище:
`database.type: SQLITE` для одного сервера или `MYSQL` для сети/кластеров.
2. Настройте правила наказаний:
`punish.warn-duration-seconds`, `warn.allowed-reasons`, `check.*`.
3. Проверьте синхронизацию:
`sync.poll-seconds` (Paper) и `sync-poll-seconds` (Velocity).
4. Включайте API только с безопасным токеном:
`api.token` должен быть не дефолтный и длиной минимум 16 символов.
5. Кастомизируйте UX:
`messages.yml` отвечает за оформление выдачи наказаний и бан-табличку кика.

## Система ID

Каждое наказание получает короткий ID из 6 символов (например: `A1B2C3`).

ID используется для апелляций и проверки через `/checkpunish <id>`.

## NNR

NNR-наказания настраиваются через список `punish.nnr-types`. Их особенности:

* Не подлежат апелляции и снятию без `ban.fullaccess`.
* Причина скрывается публично (показывается `punish.nnr-hidden-reason`).
* Внутри помечаются как `NNR` в `/checkpunish`.

## Предупреждения

`/warn` использует длительность из `punish.warn-duration-seconds` (по умолчанию 14 дней) и блокирует вход как бан.
Разрешены только причины из `warn.allowed-reasons` (по умолчанию 2 причины).
После 3 активных предупреждений автоматически выдаётся перманентный бан.

## Velocity-синхронизация

* Синхронизация выполняется через общую базу данных.
* Частота синхронизации настраивается через `sync.poll-seconds` (Paper) и `sync-poll-seconds` (Velocity).
* Velocity проверяет наказания при входе и мгновенно отключает игрока.
* Обход через лобби не допускается.

Конфигурация Velocity: `plugins/pluginbans/config.toml`.

## Forum API

Встроенный API для интеграции с форумом включается в `plugins/PluginBans/config.yml`:

```yaml
api:
  enabled: true
  bind: "127.0.0.1"
  port: 8777
  token: "PASTE_LONG_RANDOM_TOKEN"
```

Авторизация:
* `X-API-Token: <token>`
* или `Authorization: Bearer <token>`
* API не стартует с дефолтным/слабым токеном.

Базовый путь: `/api/v1`

Основные endpoint'ы:
* `GET /api/v1/health` — проверка доступности API
* `GET /api/v1/meta` — мета API (доступные endpoint'ы и базовые ограничения)
* `GET /api/v1/punishments/{id}` — получить наказание по ID
* `POST /api/v1/punishments` — выдать наказание
* `POST /api/v1/punishments/{id}/revoke` — снять наказание
* `GET /api/v1/players/{target}/active` — активные наказания игрока
* `GET /api/v1/players/{target}/history` — история наказаний игрока

Обновления API:
* `TEMPBAN` через API требует срок `> 0`.
* Повторный revoke неактивного наказания возвращает `409`.
* Невалидный JSON возвращает `400`.

Пример выдачи наказания:
```json
{
  "target": "playerNameOrUuid",
  "type": "BAN",
  "reason": "Причина",
  "duration": "1d2h",
  "actor": "Forum",
  "silent": false,
  "nnr": false
}
```

Пример снятия наказания:
```json
{
  "actor": "ForumModerator",
  "reason": "Апелляция одобрена"
}
```

## Python Gateway (опционально)

Если нужен отдельный внешний веб-сервер (например, для Pterodactyl), используйте
`python-gateway/`:

* `python-gateway/app.py` — FastAPI gateway
* `python-gateway/README.md` — установка и запуск
* `python-gateway/gateway-config.example.json` — шаблон простой настройки

Схема: внешний клиент -> Python gateway -> локальный PluginBans API (`127.0.0.1:8777`).

## DiscordBridge

Сервис `DiscordBridge` формирует JSON-пакет для синхронизации:
```json
{
  "uuid": "UUID",
  "type": "BAN",
  "reason": "Причина",
  "duration": 3600,
  "id": "A1B2C3"
}
```

Логику отправки можно подключить отдельно.
