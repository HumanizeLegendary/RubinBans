# PluginBans

Современное ядро наказаний для Paper 1.21–1.21.10 (Java 21) с поддержкой Velocity, SQLite/MySQL, REST-эндпоинтом и Discord-синхронизацией (логика).

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

Конфигурация Velocity: `plugins/PluginBans/velocity-config.json`.

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

Базовый путь: `/api/v1`

Основные endpoint'ы:
* `GET /api/v1/health` — проверка доступности API
* `GET /api/v1/punishments/{id}` — получить наказание по ID
* `POST /api/v1/punishments` — выдать наказание
* `POST /api/v1/punishments/{id}/revoke` — снять наказание
* `GET /api/v1/players/{target}/active` — активные наказания игрока
* `GET /api/v1/players/{target}/history` — история наказаний игрока

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
