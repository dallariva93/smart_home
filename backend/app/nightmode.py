"""Modalità notturna v2 — temperature-aware, pensata per l'uso reale.

Idea: di notte non basta un orario fisso. L'algoritmo punta a una **temperatura
comfort** all'addormentamento, la lascia salire dolcemente nel cuore della notte
(meno consumo, sonno profondo) e la riporta al comfort prima del risveglio. Il
controllo è a **anello chiuso sulla temperatura misurata** della stanza, con isteresi
per non far pendolare il compressore e una soglia di spegnimento per risparmiare quando
la stanza è già fresca.

Curva della temperatura-obiettivo nella finestra [start, end]:
  • dall'inizio: sale linearmente da `target_temp` a `target_temp + night_offset`
    nell'arco di `ramp_minutes`, poi resta costante;
  • negli ultimi `pre_wake_minutes`: torna a `target_temp` (risveglio fresco).

Controllo per ogni clima selezionato (con T = temperatura misurata, D = obiettivo):
  • imposta il setpoint del clima = D (arrotondato), modalità e ventola silenziosa;
  • se T ≥ D − hysteresis  → accende (deve raffreddare);
  • se T ≤ D − power_off_margin → spegne (stanza già fresca: risparmio e silenzio);
  • in mezzo: mantiene lo stato (evita cicli inutili).
Vengono inviati solo i comandi che cambiano qualcosa (anti-spam).
"""
import json
import logging
import os
from datetime import datetime, time as dtime, timedelta

from .models import NightModeConfig
from .smartthings import SmartThingsClient

log = logging.getLogger("nightmode")


def _parse_hhmm(s: str) -> dtime:
    h, m = s.split(":")
    return dtime(int(h), int(m))


def _in_window(now_t: dtime, start: dtime, end: dtime) -> bool:
    if start <= end:
        return start <= now_t < end
    return now_t >= start or now_t < end


def _minutes_since_start(now: datetime, start: dtime) -> float:
    start_dt = datetime.combine(now.date(), start)
    if now < start_dt:
        start_dt -= timedelta(days=1)
    return (now - start_dt).total_seconds() / 60


def _minutes_until_end(now: datetime, end: dtime) -> float:
    end_dt = datetime.combine(now.date(), end)
    if end_dt <= now:
        end_dt += timedelta(days=1)
    return (end_dt - now).total_seconds() / 60


class NightModeManager:
    def __init__(self, path: str, st_client: SmartThingsClient, default_device_ids: list[str] | None = None):
        self.path = path
        self.st = st_client
        self.default_device_ids = default_device_ids or []
        self._last_setpoint: dict[str, int] = {}
        self._last_power: dict[str, str] = {}

    # --- persistenza ---
    def load(self) -> NightModeConfig:
        if os.path.exists(self.path):
            with open(self.path, "r", encoding="utf-8") as f:
                return NightModeConfig(**json.load(f))
        return NightModeConfig()

    def save(self, cfg: NightModeConfig) -> NightModeConfig:
        tmp = self.path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(cfg.model_dump(), f, indent=2)
        os.replace(tmp, self.path)
        self._last_setpoint.clear()
        self._last_power.clear()
        return cfg

    # --- curva obiettivo ---
    def target_temperature(self, cfg: NightModeConfig, now: datetime) -> float | None:
        start, end = _parse_hhmm(cfg.start), _parse_hhmm(cfg.end)
        if not _in_window(now.time(), start, end):
            return None
        if _minutes_until_end(now, end) <= cfg.pre_wake_minutes:
            target = cfg.target_temp
        else:
            ramp = max(cfg.ramp_minutes, 1)
            progress = min(_minutes_since_start(now, start) / ramp, 1.0)
            target = cfg.target_temp + cfg.night_offset * progress
        return max(cfg.min_setpoint, min(cfg.max_setpoint, target))

    # --- applicazione ---
    async def apply(self, states: dict[str, dict]) -> dict:
        cfg = self.load()
        devices = cfg.device_ids or self.default_device_ids
        if not cfg.enabled or not devices:
            return {"applied": False, "reason": "disabled or no devices"}

        now = datetime.now()
        desired = self.target_temperature(cfg, now)
        if desired is None:
            return {"applied": False, "reason": "outside window"}

        setpoint = round(desired)
        applied = []
        for dev in devices:
            st = states.get(dev, {})
            temp = st.get("temperature")
            currently_on = st.get("power_state") == "on"

            # decisione accensione con isteresi
            if isinstance(temp, (int, float)):
                if temp >= desired - cfg.hysteresis:
                    want_on = True
                elif temp <= desired - cfg.power_off_margin:
                    want_on = False
                else:
                    want_on = currently_on  # zona morta: non cambiare
            else:
                want_on = True  # senza misura, lascia regolare il clima

            try:
                if want_on:
                    if not currently_on or self._last_power.get(dev) != "on":
                        await self.st.send_command(dev, "switch", "on")
                        await self.st.send_command(dev, "airConditionerMode", "setAirConditionerMode", [cfg.ac_mode])
                        await self.st.send_command(dev, "airConditionerFanMode", "setFanMode", [cfg.fan_mode])
                    if self._last_setpoint.get(dev) != setpoint:
                        await self.st.send_command(dev, "thermostatCoolingSetpoint", "setCoolingSetpoint", [setpoint])
                        self._last_setpoint[dev] = setpoint
                    self._last_power[dev] = "on"
                else:
                    if currently_on or self._last_power.get(dev) != "off":
                        await self.st.send_command(dev, "switch", "off")
                    self._last_power[dev] = "off"
                applied.append({"device_id": dev, "target": desired, "setpoint": setpoint, "power": self._last_power[dev]})
            except Exception as e:
                log.error("Night mode: errore su %s: %s", dev, e)
        log.info("Night mode: obiettivo %.1f°C -> %s", desired, applied)
        return {"applied": True, "target": desired, "devices": applied}
