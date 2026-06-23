"""Automazioni: pianificazioni orarie, modalità Away/Eco, auto-spegnimento di sicurezza.

Tutto persistito su file JSON e applicato dallo scheduler (tick al minuto).
"""
import json
import logging
import os
import time
import uuid
from datetime import datetime

from .models import AwayState, SafetyConfig, Schedule
from .smartthings import SmartThingsClient

log = logging.getLogger("automation")


def _load_json(path, default):
    if os.path.exists(path):
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    return default


def _save_json(path, data):
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
    os.replace(tmp, path)


class AutomationManager:
    def __init__(self, st_client, device_ids, schedules_path, away_path, safety_path):
        self.st: SmartThingsClient = st_client
        self.device_ids = device_ids
        self.schedules_path = schedules_path
        self.away_path = away_path
        self.safety_path = safety_path
        self._last_fired: dict[str, str] = {}   # schedule_id -> "YYYY-MM-DD HH:MM"
        self._on_since: dict[str, float] = {}    # device_id -> epoch di accensione

    # ---------- away / eco ----------
    def away(self) -> AwayState:
        return AwayState(**_load_json(self.away_path, {"enabled": False}))

    def away_enabled(self) -> bool:
        return self.away().enabled

    async def set_away(self, enabled: bool) -> AwayState:
        _save_json(self.away_path, {"enabled": enabled})
        if enabled:  # eco = spegne tutto
            for dev in self.device_ids:
                try:
                    await self.st.send_command(dev, "switch", "off")
                except Exception as e:
                    log.error("Away: errore spegnendo %s: %s", dev, e)
        return AwayState(enabled=enabled)

    # ---------- pianificazioni ----------
    def list_schedules(self) -> list[Schedule]:
        return [Schedule(**s) for s in _load_json(self.schedules_path, [])]

    def save_schedules(self, schedules: list[Schedule]) -> list[Schedule]:
        for s in schedules:
            if not s.id:
                s.id = uuid.uuid4().hex[:8]
        _save_json(self.schedules_path, [s.model_dump() for s in schedules])
        return schedules

    def upsert_schedule(self, schedule: Schedule) -> Schedule:
        if not schedule.id:
            schedule.id = uuid.uuid4().hex[:8]
        items = [s for s in self.list_schedules() if s.id != schedule.id]
        items.append(schedule)
        self.save_schedules(items)
        return schedule

    def delete_schedule(self, schedule_id: str) -> None:
        self.save_schedules([s for s in self.list_schedules() if s.id != schedule_id])

    async def apply_schedules(self, states: dict) -> list[str]:
        now = datetime.now()
        hhmm = now.strftime("%H:%M")
        minute_key = now.strftime("%Y-%m-%d %H:%M")
        fired = []
        for s in self.list_schedules():
            if not s.enabled or s.time != hhmm:
                continue
            if s.days and now.weekday() not in s.days:
                continue
            if self._last_fired.get(s.id) == minute_key:
                continue
            try:
                await self._apply_action(s)
                self._last_fired[s.id] = minute_key
                fired.append(s.id)
                log.info("Schedule '%s' (%s) applicata", s.label or s.id, s.time)
            except Exception as e:
                log.error("Schedule %s errore: %s", s.id, e)
        return fired

    async def _apply_action(self, s: Schedule):
        targets = s.targets or self.device_ids
        a = s.action
        if a.type == "away_on":
            await self.set_away(True)
            return
        if a.type == "away_off":
            await self.set_away(False)
            return
        for dev in targets:
            if a.type == "power_on":
                await self.st.send_command(dev, "switch", "on")
            elif a.type == "power_off":
                await self.st.send_command(dev, "switch", "off")
            elif a.type == "setpoint":
                await self.st.send_command(dev, "thermostatCoolingSetpoint", "setCoolingSetpoint", [float(a.value)])
            elif a.type == "mode":
                await self.st.send_command(dev, "airConditionerMode", "setAirConditionerMode", [a.value])
            elif a.type == "fan":
                await self.st.send_command(dev, "airConditionerFanMode", "setFanMode", [a.value])

    # ---------- sicurezza (auto-off) ----------
    def safety(self) -> SafetyConfig:
        return SafetyConfig(**_load_json(self.safety_path, {}))

    def save_safety(self, cfg: SafetyConfig) -> SafetyConfig:
        _save_json(self.safety_path, cfg.model_dump())
        return cfg

    async def check_safety(self, states: dict) -> list[dict]:
        """Spegne i clima accesi da troppo tempo. Ritorna gli eventi (per le notifiche)."""
        cfg = self.safety()
        if not cfg.enabled:
            self._on_since.clear()
            return []
        now = time.time()
        triggered = []
        for dev, st in states.items():
            if st.get("power_state") == "on":
                since = self._on_since.setdefault(dev, now)
                hours = (now - since) / 3600
                if hours >= cfg.max_runtime_hours:
                    try:
                        await self.st.send_command(dev, "switch", "off")
                        triggered.append({"device_id": dev, "hours": round(hours, 1)})
                        self._on_since.pop(dev, None)
                        log.info("Safety: spento %s dopo %.1fh", dev, hours)
                    except Exception as e:
                        log.error("Safety: errore spegnendo %s: %s", dev, e)
            else:
                self._on_since.pop(dev, None)
        return triggered
