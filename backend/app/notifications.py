"""Notifiche in-app: configurazione + generazione e storico degli alert.

Gli alert vengono generati dallo scheduler (valutando soglie temperatura, collector
offline, stato filtro) e conservati in un file JSON (ultimi N). L'app li legge via
polling. Ogni tipo di alert per device ha un cooldown per non ripetersi di continuo.
"""
import json
import logging
import os
import time
import uuid
from datetime import datetime

from .models import Alert, NotificationConfig

log = logging.getLogger("notifications")

MAX_ALERTS = 100
COOLDOWN_S = 3600  # non ripetere lo stesso alert (device+tipo) entro 1h


class NotificationManager:
    def __init__(self, config_path: str, alerts_path: str):
        self.config_path = config_path
        self.alerts_path = alerts_path
        self._cooldown: dict[str, float] = {}

    # --- config ---
    def config(self) -> NotificationConfig:
        if os.path.exists(self.config_path):
            with open(self.config_path, "r", encoding="utf-8") as f:
                return NotificationConfig(**json.load(f))
        return NotificationConfig()

    def save_config(self, cfg: NotificationConfig) -> NotificationConfig:
        _save_json(self.config_path, cfg.model_dump())
        return cfg

    # --- alert store ---
    def list_alerts(self) -> list[dict]:
        if os.path.exists(self.alerts_path):
            with open(self.alerts_path, "r", encoding="utf-8") as f:
                return json.load(f)
        return []

    def _write(self, alerts: list[dict]):
        _save_json(self.alerts_path, alerts[-MAX_ALERTS:])

    def add_alert(self, type_: str, message: str, device_id=None, device_name=None, cooldown=True) -> bool:
        if cooldown:
            key = f"{device_id}:{type_}"
            now = time.time()
            if now - self._cooldown.get(key, 0) < COOLDOWN_S:
                return False
            self._cooldown[key] = now
        alerts = self.list_alerts()
        alerts.append(Alert(
            id=uuid.uuid4().hex[:10],
            time=datetime.now().isoformat(timespec="seconds"),
            type=type_, message=message,
            device_id=device_id, device_name=device_name, read=False,
        ).model_dump())
        self._write(alerts)
        log.info("Alert: %s — %s", type_, message)
        return True

    def mark_read(self, alert_id: str | None = None):
        alerts = self.list_alerts()
        for a in alerts:
            if alert_id is None or a.get("id") == alert_id:
                a["read"] = True
        self._write(alerts)

    def unread_count(self) -> int:
        return sum(1 for a in self.list_alerts() if not a.get("read"))

    # --- valutazione (chiamata dallo scheduler) ---
    def evaluate(self, states: dict):
        """states: device_id -> {device_name, temperature, dust_filter_status, age_min}."""
        cfg = self.config()
        for dev, st in states.items():
            name = st.get("device_name") or dev
            temp = st.get("temperature")
            age = st.get("age_min")
            filt = st.get("dust_filter_status")

            if cfg.temp_high_enabled and isinstance(temp, (int, float)) and temp >= cfg.temp_high_threshold:
                self.add_alert("temp_high", f"{name}: temperatura alta ({temp}°C)", dev, name)
            if cfg.temp_low_enabled and isinstance(temp, (int, float)) and temp <= cfg.temp_low_threshold:
                self.add_alert("temp_low", f"{name}: temperatura bassa ({temp}°C)", dev, name)
            if cfg.offline_enabled and (age is None or age >= cfg.offline_minutes):
                self.add_alert("offline", f"{name}: nessun dato recente dal collector", dev, name)
            if cfg.filter_enabled and filt not in (None, "normal"):
                self.add_alert("filter", f"{name}: filtro da controllare ({filt})", dev, name)


def _save_json(path, data):
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
    os.replace(tmp, path)
