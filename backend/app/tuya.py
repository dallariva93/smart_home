"""Client Tuya / Smart Life via Cloud API ufficiale (prese smart: lettura + comando).

Usa la libreria ufficiale `tuya-connector-python` (gestisce token e firma HMAC).
Se la libreria non è installata o le credenziali non sono configurate, il client è
"non disponibile" e gli endpoint restituiscono lista vuota (l'app semplicemente non
mostra prese).

Setup credenziali: vedi backend/README.md (account Tuya IoT + linking Smart Life).
"""
import logging

log = logging.getLogger("tuya")

try:
    from tuya_connector import TuyaOpenAPI
except Exception:  # libreria non installata
    TuyaOpenAPI = None


class TuyaClient:
    def __init__(self, endpoint: str, access_id: str, access_secret: str):
        self.available = bool(TuyaOpenAPI and access_id and access_secret)
        self._api = TuyaOpenAPI(endpoint, access_id, access_secret) if self.available else None
        self._connected = False

    def _ensure(self):
        if not self._connected:
            self._api.connect()  # ottiene/rinnova il token
            self._connected = True

    def devices(self) -> list[dict]:
        """Prese collegate al cloud project, in forma normalizzata."""
        self._ensure()
        res = self._api.get("/v1.0/iot-01/associated-users/devices")
        out = []
        for d in res.get("result", {}).get("devices", []):
            status = {s["code"]: s["value"] for s in d.get("status", [])}
            on, switch_code = None, None
            for code in ("switch_1", "switch"):
                if code in status:
                    on = bool(status[code])
                    switch_code = code
                    break
            power = status.get("cur_power")
            voltage = status.get("cur_voltage")
            out.append({
                "id": d.get("id"),
                "name": d.get("name"),
                "online": d.get("online"),
                "on": on,
                "switch_code": switch_code,
                # NB: scala dei consumi dipendente dal modello (spesso deciwatt/decivolt)
                "power_w": (power / 10) if isinstance(power, (int, float)) else None,
                "voltage": (voltage / 10) if isinstance(voltage, (int, float)) else None,
            })
        return out

    def _switch_code(self, device_id: str) -> str:
        self._ensure()
        res = self._api.get(f"/v1.0/devices/{device_id}/status")
        for s in res.get("result", []):
            if s.get("code") in ("switch_1", "switch"):
                return s["code"]
        return "switch_1"

    def switch(self, device_id: str, on: bool) -> dict:
        self._ensure()
        code = self._switch_code(device_id)
        return self._api.post(
            f"/v1.0/devices/{device_id}/commands",
            {"commands": [{"code": code, "value": on}]},
        )
