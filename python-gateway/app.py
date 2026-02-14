#!/usr/bin/env python3
"""PluginBans Python API gateway.

This server exposes a stable HTTP API and proxies requests to PluginBans Forum API.
Use it when you want a separate Python web layer (for panel setup, middleware,
rate-limiting, or custom integration logic) without changing Java plugin code.
"""

from __future__ import annotations

from contextlib import asynccontextmanager
import json
import os
from pathlib import Path
import secrets
from typing import Any, AsyncIterator, Dict

import httpx
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse, Response

API_PREFIX = "/api/v1"
BASE_DIR = Path(__file__).resolve().parent
CONFIG_PATH = Path(os.getenv("GATEWAY_CONFIG", str(BASE_DIR / "gateway-config.json")))


def _load_file_config(path: Path) -> Dict[str, Any]:
    if not path.exists():
        return {}
    with path.open("r", encoding="utf-8") as handle:
        raw = json.load(handle)
    if not isinstance(raw, dict):
        raise RuntimeError(f"Gateway config must be JSON object: {path}")
    return raw


FILE_CONFIG = _load_file_config(CONFIG_PATH)


def _read_setting(env_name: str, config_name: str, default: str = "") -> str:
    env_value = os.getenv(env_name)
    if env_value is not None and env_value.strip():
        return env_value.strip()
    config_value = FILE_CONFIG.get(config_name)
    if config_value is None:
        return default
    return str(config_value).strip()


def _read_int_setting(env_name: str, config_name: str, default: int) -> int:
    value = _read_setting(env_name, config_name, str(default))
    try:
        return int(value)
    except ValueError as exception:
        raise RuntimeError(f"Invalid integer for {env_name}/{config_name}: {value}") from exception


def _read_float_setting(env_name: str, config_name: str, default: float) -> float:
    value = _read_setting(env_name, config_name, str(default))
    try:
        return float(value)
    except ValueError as exception:
        raise RuntimeError(f"Invalid float for {env_name}/{config_name}: {value}") from exception


def _read_bool_setting(env_name: str, config_name: str, default: bool) -> bool:
    value = _read_setting(env_name, config_name, "true" if default else "false").lower()
    return value in {"1", "true", "yes", "on"}


UPSTREAM_BASE_URL = _read_setting("PLUGINBANS_BASE_URL", "upstream_base_url", "http://127.0.0.1:8777/api/v1").rstrip("/")
UPSTREAM_TOKEN = _read_setting("PLUGINBANS_TOKEN", "upstream_token")
GATEWAY_TOKEN = _read_setting("GATEWAY_TOKEN", "gateway_token")
REQUEST_TIMEOUT_SECONDS = _read_float_setting("REQUEST_TIMEOUT_SECONDS", "request_timeout_seconds", 10.0)
LISTEN_HOST = _read_setting("GATEWAY_HOST", "listen_host", "0.0.0.0")
LISTEN_PORT = _read_int_setting("GATEWAY_PORT", "listen_port", 8080)
UPSTREAM_TRUST_ENV = _read_bool_setting("UPSTREAM_TRUST_ENV", "upstream_trust_env", False)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    if not UPSTREAM_TOKEN:
        raise RuntimeError("PLUGINBANS_TOKEN is required")
    if not GATEWAY_TOKEN:
        raise RuntimeError("GATEWAY_TOKEN is required")
    app.state.http = httpx.AsyncClient(timeout=REQUEST_TIMEOUT_SECONDS, trust_env=UPSTREAM_TRUST_ENV)
    try:
        yield
    finally:
        client: httpx.AsyncClient | None = getattr(app.state, "http", None)
        if client is not None:
            await client.aclose()


app = FastAPI(
    title="PluginBans Python Gateway",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
    lifespan=lifespan,
)


def _extract_token(request: Request) -> str:
    header_token = request.headers.get("X-API-Token")
    if header_token:
        return header_token.strip()

    authorization = request.headers.get("Authorization", "")
    if authorization.startswith("Bearer "):
        return authorization[7:].strip()

    return ""


def _ensure_authorized(request: Request) -> None:
    token = _extract_token(request)
    if not token or not secrets.compare_digest(token, GATEWAY_TOKEN):
        raise HTTPException(status_code=401, detail="Unauthorized")


def _upstream_headers() -> Dict[str, str]:
    return {
        "X-API-Token": UPSTREAM_TOKEN,
        "Content-Type": "application/json",
    }


async def _proxy_get(path: str, request: Request) -> Response:
    _ensure_authorized(request)
    client: httpx.AsyncClient = app.state.http
    target_url = f"{UPSTREAM_BASE_URL}{path}"

    try:
        upstream = await client.get(target_url, headers=_upstream_headers(), params=request.query_params)
    except httpx.HTTPError as exc:
        return JSONResponse(status_code=502, content={"ok": False, "error": f"Upstream unavailable: {exc}"})

    return Response(
        content=upstream.content,
        status_code=upstream.status_code,
        media_type=upstream.headers.get("content-type", "application/json"),
    )


async def _proxy_post(path: str, request: Request) -> Response:
    _ensure_authorized(request)
    client: httpx.AsyncClient = app.state.http
    target_url = f"{UPSTREAM_BASE_URL}{path}"

    body = await request.body()
    if not body:
        body = b"{}"

    try:
        upstream = await client.post(
            target_url,
            headers=_upstream_headers(),
            params=request.query_params,
            content=body,
        )
    except httpx.HTTPError as exc:
        return JSONResponse(status_code=502, content={"ok": False, "error": f"Upstream unavailable: {exc}"})

    return Response(
        content=upstream.content,
        status_code=upstream.status_code,
        media_type=upstream.headers.get("content-type", "application/json"),
    )


@app.get("/")
async def root() -> dict[str, str]:
    return {
        "service": "PluginBans Python Gateway",
        "upstream": UPSTREAM_BASE_URL,
        "listen": f"{LISTEN_HOST}:{LISTEN_PORT}",
        "docs": "/docs",
    }


@app.get(f"{API_PREFIX}/health")
async def health(request: Request) -> Response:
    return await _proxy_get("/health", request)


@app.get(f"{API_PREFIX}/meta")
async def meta(request: Request) -> Response:
    return await _proxy_get("/meta", request)


@app.post(f"{API_PREFIX}/punishments")
async def create_punishment(request: Request) -> Response:
    return await _proxy_post("/punishments", request)


@app.get(f"{API_PREFIX}/punishments/{{punishment_id}}")
async def get_punishment(punishment_id: str, request: Request) -> Response:
    return await _proxy_get(f"/punishments/{punishment_id}", request)


@app.post(f"{API_PREFIX}/punishments/{{punishment_id}}/revoke")
async def revoke_punishment(punishment_id: str, request: Request) -> Response:
    return await _proxy_post(f"/punishments/{punishment_id}/revoke", request)


@app.get(f"{API_PREFIX}/players/{{target}}/active")
async def player_active(target: str, request: Request) -> Response:
    return await _proxy_get(f"/players/{target}/active", request)


@app.get(f"{API_PREFIX}/players/{{target}}/history")
async def player_history(target: str, request: Request) -> Response:
    return await _proxy_get(f"/players/{target}/history", request)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host=LISTEN_HOST, port=LISTEN_PORT)
