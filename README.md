# PluginBans

Современное ядро наказаний для Paper 1.21–1.21.10 (Java 21) с поддержкой Velocity, SQLite/MySQL, REST-эндпоинтом и Discord-синхронизацией (логика).

## Команды

| Команда | Описание |
| --- | --- |
| `/ban <игрок|uuid> <длительность> <причина>` | Блокировка игрока. |
| `/tempban <игрок|uuid> <длительность> <причина>` | Временная блокировка игрока. |
| `/ipban <игрок|uuid> <длительность> <причина>` | IP-бан. |
| `/mute <игрок|uuid> <длительность> <причина>` | Блокировка чата. |
| `/warn <игрок|uuid> <причина>` | Предупреждение (длительность по конфигу). |
| `/idban <игрок|uuid> <длительность> <причина>` | ID-бан. |
| `/punish <uuid> <тип> <длительность> <причина>` | Кастомное наказание напрямую в БД. |
| `/checkpunish <id>` | Проверка наказания по ID. |

Длительность: `1d2h30m`, `15m`, `perm`/`permanent`/`навсегда` для бессрочных.

## Права

* `ban.ban`
* `ban.ipban`
* `ban.mute`
* `ban.warn`
* `ban.idban`
* `ban.punish`
* `ban.check`
* `ban.fullaccess` — доступ ко всем операциям, включая NNR.

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

Каждое наказание получает ID вида `PBRB-<TYPE>-<RANDOM5>`.

* `TM` — временное наказание.
* `NV` — постоянное наказание.
* `NNR` — неснимаемое наказание персонала.

## NNR

NNR-наказания настраиваются через список `punish.nnr-types`. Их особенности:

* Не подлежат апелляции и снятию без `ban.fullaccess`.
* Причина скрывается публично (показывается `punish.nnr-hidden-reason`).
* Внутри помечаются как `NNR` в `/checkpunish`.

## Предупреждения

`/warn` использует длительность из `punish.warn-duration-seconds` (по умолчанию 14 дней).
После 3 активных предупреждений автоматически выдаётся перманентный IP-бан.

## Velocity-синхронизация

* При выдаче наказания Paper отправляет плагин-сообщение в Velocity.
* Velocity проверяет наказания при входе и мгновенно отключает игрока при синхронизации.
* Обход через лобби не допускается.

Конфигурация Velocity: `plugins/PluginBans/velocity-config.json`.

## REST API

`GET /punishment/<id>` — возвращает JSON:
```json
{
  "uuid": "UUID",
  "type": "BAN",
  "reason": "Причина",
  "duration": 3600,
  "issuedBy": "Админ",
  "timestamp": 1710000000000
}
```

Настройки REST доступны в `config.yml` (`rest.enabled`, `rest.bind`, `rest.port`).

## DiscordBridge

Сервис `DiscordBridge` формирует JSON-пакет для синхронизации:
```json
{
  "uuid": "UUID",
  "type": "BAN",
  "reason": "Причина",
  "duration": 3600,
  "id": "PBRB-TM-ABCDE"
}
```

Логику отправки можно подключить отдельно.
