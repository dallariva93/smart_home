"""Client SmartThings asincrono: OAuth (refresh + persistenza), stato e comandi.

Usa credenziali OAuth DEDICATE al backend (vedi FASE_C_DESIGN §2). Il refresh token
ruota a ogni rinnovo: viene risalvato su disco con scrittura atomica.
"""
import asyncio
import base64
import json
import os
import time

import httpx

TOKEN_URL = "https://auth-global.api.smartthings.com/oauth/token"
API = "https://api.smartthings.com/v1"
EXPIRY_MARGIN_S = 120


class SmartThingsClient:
    def __init__(self, client_id: str, client_secret: str, token_store_path: str):
        self.client_id = client_id
        self.client_secret = client_secret
        self.token_store_path = token_store_path
        self._tok: dict | None = None
        self._lock = asyncio.Lock()

    # --- token store ---
    def _load(self) -> dict:
        if self._tok is None:
            if not os.path.exists(self.token_store_path):
                raise RuntimeError(
                    f"Token store '{self.token_store_path}' mancante: esegui "
                    "authorize_backend.py per il consenso iniziale del backend."
                )
            with open(self.token_store_path, "r", encoding="utf-8") as f:
                self._tok = json.load(f)
        return self._tok

    def _save(self, tok: dict) -> None:
        tmp = self.token_store_path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(tok, f, indent=2)
        os.replace(tmp, self.token_store_path)
        self._tok = tok

    def _basic(self) -> str:
        raw = f"{self.client_id}:{self.client_secret}".encode()
        return "Basic " + base64.b64encode(raw).decode()

    async def _refresh(self, refresh_token: str) -> dict:
        async with httpx.AsyncClient(timeout=30) as c:
            r = await c.post(
                TOKEN_URL,
                headers={
                    "Authorization": self._basic(),
                    "Content-Type": "application/x-www-form-urlencoded",
                },
                data={
                    "grant_type": "refresh_token",
                    "client_id": self.client_id,
                    "refresh_token": refresh_token,
                },
            )
            r.raise_for_status()
            tok = r.json()
        tok["expires_at"] = time.time() + int(tok.get("expires_in", 0)) - EXPIRY_MARGIN_S
        self._save(tok)
        return tok

    async def _access_token(self) -> str:
        async with self._lock:  # evita doppi refresh concorrenti
            tok = self._load()
            if time.time() < tok.get("expires_at", 0):
                return tok["access_token"]
            tok = await self._refresh(tok["refresh_token"])
            return tok["access_token"]

    # --- API ---
    async def _get(self, path: str) -> dict:
        token = await self._access_token()
        async with httpx.AsyncClient(timeout=30) as c:
            r = await c.get(API + path, headers={"Authorization": f"Bearer {token}"})
            r.raise_for_status()
            return r.json()

    async def status(self, device_id: str) -> dict:
        return await self._get(f"/devices/{device_id}/status")

    async def device(self, device_id: str) -> dict:
        return await self._get(f"/devices/{device_id}")

    async def normalized_state(self, device_id: str) -> dict:
        """Stato sintetico e tipizzato del climatizzatore, dalle capability principali."""
        status = await self.status(device_id)
        main = status.get("components", {}).get("main", {})

        def val(cap, attr):
            node = main.get(cap, {}).get(attr, {})
            return node.get("value") if isinstance(node, dict) else None

        return {
            "power_state": val("switch", "switch"),
            "temperature": val("temperatureMeasurement", "temperature"),
            "cooling_setpoint": val("thermostatCoolingSetpoint", "coolingSetpoint"),
            "ac_mode": val("airConditionerMode", "airConditionerMode"),
            "fan_mode": val("airConditionerFanMode", "fanMode"),
            "oscillation_mode": val("fanOscillationMode", "fanOscillationMode"),
            "dust_filter_status": val("custom.dustFilter", "dustFilterStatus"),
        }

    async def capabilities(self, device_id: str) -> list[str]:
        """Elenco delle capability del componente main (per scoprire i comandi disponibili)."""
        try:
            d = await self.device(device_id)
            comps = d.get("components", [])
            for c in comps:
                if c.get("id") == "main":
                    return [cap.get("id") for cap in c.get("capabilities", [])]
        except Exception:
            pass
        return []

    async def send_command(
        self,
        device_id: str,
        capability: str,
        command: str,
        arguments: list | None = None,
        component: str = "main",
    ) -> dict:
        token = await self._access_token()
        body = {
            "commands": [
                {
                    "component": component,
                    "capability": capability,
                    "command": command,
                    "arguments": arguments or [],
                }
            ]
        }
        async with httpx.AsyncClient(timeout=30) as c:
            r = await c.post(
                f"{API}/devices/{device_id}/commands",
                headers={
                    "Authorization": f"Bearer {token}",
                    "Content-Type": "application/json",
                },
                json=body,
            )
            r.raise_for_status()
            return r.json()
