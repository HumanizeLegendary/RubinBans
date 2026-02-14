# PluginBans Python Gateway

FastAPI gateway for PluginBans Forum API.

## Why this exists

This service gives you a separate Python web layer while PluginBans Java API stays local.
Useful for Pterodactyl setups where you want one public endpoint with your own middleware.

## Architecture

- PluginBans API: local/private (usually `127.0.0.1:8777`)
- Python Gateway: public (for example `0.0.0.0:8080`)
- Client/Forum -> Python Gateway -> PluginBans API

## Requirements

- Python 3.10+
- PluginBans API enabled in plugin `config.yml`

## Plugin config example

```yaml
api:
  enabled: true
  bind: "127.0.0.1"
  port: 8777
  token: "PLUGINBANS_LONG_TOKEN_16+"
```

## Install

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## Environment variables

- `PLUGINBANS_BASE_URL` (default: `http://127.0.0.1:8777/api/v1`)
- `PLUGINBANS_TOKEN` (required) token used for upstream PluginBans API
- `GATEWAY_TOKEN` (required) token required from clients of Python gateway
- `REQUEST_TIMEOUT_SECONDS` (optional, default: `10`)

## Run

```bash
export PLUGINBANS_BASE_URL="http://127.0.0.1:8777/api/v1"
export PLUGINBANS_TOKEN="PLUGINBANS_LONG_TOKEN_16+"
export GATEWAY_TOKEN="GATEWAY_LONG_TOKEN_16+"

uvicorn app:app --host 0.0.0.0 --port 8080
```

## Endpoints

Gateway keeps the same API paths:

- `GET /api/v1/health`
- `GET /api/v1/meta`
- `POST /api/v1/punishments`
- `GET /api/v1/punishments/{id}`
- `POST /api/v1/punishments/{id}/revoke`
- `GET /api/v1/players/{target}/active`
- `GET /api/v1/players/{target}/history`

## Client auth

Use either:

- `X-API-Token: <GATEWAY_TOKEN>`
- `Authorization: Bearer <GATEWAY_TOKEN>`

## Quick test

```bash
curl -H "X-API-Token: GATEWAY_LONG_TOKEN_16+" \
  http://127.0.0.1:8080/api/v1/health
```

## Security notes

- Do not expose PluginBans token to clients.
- Keep PluginBans API on localhost if possible.
- Expose only gateway port and apply firewall allow-list for your forum/backend IPs.
