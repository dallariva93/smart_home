"""Client Pi-hole v6 (REST API): statistiche + abilita/disabilita blocco.

Il backend (su Oracle) raggiunge il Pi-hole **sul tailnet** (il Raspberry deve essere in
Tailscale): `PIHOLE_URL` punta all'IP/nome Tailscale del Pi (http, cifrato da WireGuard).
Auth v6: POST /api/auth con la password → SID di sessione, usato come header X-FTL-SID.
"""
import logging
import time

import httpx

log = logging.getLogger("pihole")


class PiholeClient:
    def __init__(self, base_url: str, password: str):
        base = (base_url or "").strip().rstrip("/")
        if base and not base.startswith(("http://", "https://")):
            base = "http://" + base  # tollera PIHOLE_URL senza schema
        self.base = base
        self.password = password
        self.available = bool(self.base and password)
        self._sid: str | None = None
        self._sid_exp: float = 0

    async def _auth(self, client: httpx.AsyncClient) -> str:
        r = await client.post(f"{self.base}/api/auth", json={"password": self.password})
        r.raise_for_status()
        sess = r.json().get("session", {})
        sid = sess.get("sid")
        if not sid:
            raise RuntimeError("Pi-hole: autenticazione fallita")
        self._sid = sid
        self._sid_exp = time.time() + int(sess.get("validity", 1800)) - 60
        return sid

    async def _sid_ok(self, client: httpx.AsyncClient) -> str:
        if self._sid and time.time() < self._sid_exp:
            return self._sid
        return await self._auth(client)

    async def _request(self, method: str, path: str, json: dict | None = None) -> dict:
        async with httpx.AsyncClient(timeout=15) as client:
            sid = await self._sid_ok(client)
            r = await client.request(method, f"{self.base}{path}", headers={"X-FTL-SID": sid}, json=json)
            if r.status_code == 401:  # sessione scaduta -> riautentica una volta
                sid = await self._auth(client)
                r = await client.request(method, f"{self.base}{path}", headers={"X-FTL-SID": sid}, json=json)
            r.raise_for_status()
            return r.json() if r.content else {}

    async def summary(self) -> dict:
        data = await self._request("GET", "/api/stats/summary")
        blocking = await self._request("GET", "/api/dns/blocking")
        q = data.get("queries", {})
        return {
            "total": q.get("total"),
            "blocked": q.get("blocked"),
            "percent_blocked": q.get("percent_blocked"),
            "unique_domains": q.get("unique_domains"),
            "domains_blocked": data.get("gravity", {}).get("domains_being_blocked"),
            "blocking": blocking.get("blocking"),  # "enabled" | "disabled"
            "timer": blocking.get("timer"),
        }

    async def set_blocking(self, enabled: bool, seconds: int | None = None) -> dict:
        return await self._request("POST", "/api/dns/blocking", json={"blocking": enabled, "timer": seconds})
