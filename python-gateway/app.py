#!/usr/bin/env python3
"""PluginBans Python API gateway.

This server exposes a stable HTTP API and proxies requests to PluginBans Forum API.
Use it when you want a separate Python web layer (for panel setup, middleware,
rate-limiting, or custom integration logic) without changing Java plugin code.
"""

from __future__ import annotations

from contextlib import asynccontextmanager
import os
import secrets
from typing import AsyncIterator, Dict

import httpx
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse, Response

API_PREFIX = "/api/v1"
UPSTREAM_BASE_URL = os.getenv("PLUGINBANS_BASE_URL", "http://127.0.0.1:8777/api/v1").rstrip("/")
UPSTREAM_TOKEN = os.getenv("PLUGINBANS_TOKEN", "").strip()
GATEWAY_TOKEN = os.getenv("GATEWAY_TOKEN", "").strip()
REQUEST_TIMEOUT_SECONDS = float(os.getenv("REQUEST_TIMEOUT_SECONDS", "10"))


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    if not UPSTREAM_TOKEN:
        raise RuntimeError("PLUGINBANS_TOKEN is required")
    if not GATEWAY_TOKEN:
        raise RuntimeError("GATEWAY_TOKEN is required")
    app.state.http = httpx.AsyncClient(timeout=REQUEST_TIMEOUT_SECONDS)
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
