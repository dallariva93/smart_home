"""Backend FastAPI: letture, comandi, modalità notturna, automazioni e notifiche.

Avvio locale:  uvicorn app.main:app --host 0.0.0.0 --port 8000
"""
import logging
import re
from contextlib import asynccontextmanager

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from fastapi import Depends, FastAPI, HTTPException
from fastapi.concurrency import run_in_threadpool

from .automation import AutomationManager
from .config import get_settings
from .influx import InfluxReader
from .models import (
    AwayState,
    FanRequest,
    GenericCommand,
    ModeRequest,
    NightModeConfig,
    NotificationConfig,
    OscillationRequest,
    PiholeBlockingRequest,
    PlugSwitchRequest,
    PowerRequest,
    SafetyConfig,
    Schedule,
    SetpointRequest,
)
from .nightmode import NightModeManager
from .notifications import NotificationManager
from .pihole import PiholeClient
from .security import require_api_key
from .smartthings import SmartThingsClient
from .tuya import TuyaClient

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("backend")

settings = get_settings()
st = SmartThingsClient(settings.st_client_id, settings.st_client_secret, settings.st_token_store)
influx = InfluxReader(settings.influx_url, settings.influx_token, settings.influx_org, settings.influx_bucket)
night = NightModeManager(settings.nightmode_config, st, settings.device_id_list)
automation = AutomationManager(
    st, settings.device_id_list, settings.schedules_store, settings.away_state, settings.safety_config
)
notifications = NotificationManager(settings.notifications_config, settings.alerts_store)
tuya = TuyaClient(settings.tuya_endpoint, settings.tuya_access_id, settings.tuya_access_secret)
pihole = PiholeClient(settings.pihole_url, settings.pihole_password)
scheduler = AsyncIOScheduler()

DEVID_RE = re.compile(r"^[A-Fa-f0-9\-]{8,}$")
FIELD_RE = re.compile(r"^[a-z_]+$")
RANGE_RE = re.compile(r"^\d+[smhdw]$")

_name_cache: dict[str, str] = {}


async def _device_name(device_id: str) -> str:
    if device_id not in _name_cache:
        try:
            d = await st.device(device_id)
            _name_cache[device_id] = d.get("label") or d.get("name") or device_id
        except Exception:
            return device_id
    return _name_cache[device_id]


async def _snapshot() -> dict[str, dict]:
    """Stato corrente di tutti i device + nome + età ultimo dato (per l'engine)."""
    states: dict[str, dict] = {}
    for did in settings.device_id_list:
        try:
            ns = await st.normalized_state(did)
        except Exception as e:
            ns = {"error": str(e)}
        ns["device_id"] = did
        ns["device_name"] = await _device_name(did)
        try:
            ns["age_min"] = influx.last_age_minutes(did)
        except Exception:
            ns["age_min"] = None
        states[did] = ns
    return states


async def engine_tick():
    """Tick al minuto: applica night mode, pianificazioni, sicurezza e valuta le notifiche."""
    try:
        states = await _snapshot()
        if not automation.away_enabled():
            await night.apply(states)
        await automation.apply_schedules(states)
        for ev in await automation.check_safety(states):
            name = states.get(ev["device_id"], {}).get("device_name")
            notifications.add_alert(
                "safety", f"{name}: spento dopo {ev['hours']}h (sicurezza)",
                ev["device_id"], name, cooldown=False,
            )
        notifications.evaluate(states)
    except Exception as e:
        log.error("engine_tick error: %s", e)


@asynccontextmanager
async def lifespan(app: FastAPI):
    scheduler.add_job(engine_tick, "interval", minutes=1, id="engine", max_instances=1)
    scheduler.start()
    log.info("Engine avviato (tick ogni minuto).")
    yield
    scheduler.shutdown(wait=False)


app = FastAPI(title="Clima Casa Backend", version="2.0", lifespan=lifespan)


def _check_device(device_id: str):
    if not DEVID_RE.match(device_id) or device_id not in settings.device_id_list:
        raise HTTPException(status_code=404, detail="Unknown device")


# ---------- liveness ----------
@app.get("/health")
def health():
    return {"ok": True}


# ---------- letture ----------
@app.get("/devices", dependencies=[Depends(require_api_key)])
def list_devices():
    return influx.latest()


@app.get("/state", dependencies=[Depends(require_api_key)])
async def all_state():
    out = []
    for did in settings.device_id_list:
        try:
            s = await st.normalized_state(did)
        except Exception as e:
            s = {"error": str(e)}
        s["device_id"] = did
        s["device_name"] = await _device_name(did)
        out.append(s)
    return out


@app.get("/devices/{device_id}/state", dependencies=[Depends(require_api_key)])
async def device_state(device_id: str):
    _check_device(device_id)
    s = await st.normalized_state(device_id)
    s["device_id"] = device_id
    s["device_name"] = await _device_name(device_id)
    return s


@app.get("/devices/{device_id}/status", dependencies=[Depends(require_api_key)])
async def device_status(device_id: str):
    _check_device(device_id)
    return await st.status(device_id)


@app.get("/devices/{device_id}/capabilities", dependencies=[Depends(require_api_key)])
async def device_capabilities(device_id: str):
    _check_device(device_id)
    return await st.capabilities(device_id)


@app.get("/devices/{device_id}/history", dependencies=[Depends(require_api_key)])
def device_history(device_id: str, field: str, range: str = "24h", window: str = "15m"):
    _check_device(device_id)
    if not FIELD_RE.match(field) or not RANGE_RE.match(range) or not RANGE_RE.match(window):
        raise HTTPException(status_code=400, detail="Invalid field/range/window")
    return influx.history(device_id, field, range, window)


@app.get("/devices/{device_id}/stats", dependencies=[Depends(require_api_key)])
def device_stats(device_id: str, field: str = "temperature", days: int = 7):
    _check_device(device_id)
    if not FIELD_RE.match(field) or not (1 <= days <= 90):
        raise HTTPException(status_code=400, detail="Invalid field/days")
    return influx.daily_stats(device_id, field, days)


# ---------- comandi ----------
@app.post("/devices/{device_id}/power", dependencies=[Depends(require_api_key)])
async def set_power(device_id: str, req: PowerRequest):
    _check_device(device_id)
    return await st.send_command(device_id, "switch", req.state)


@app.post("/devices/{device_id}/setpoint", dependencies=[Depends(require_api_key)])
async def set_setpoint(device_id: str, req: SetpointRequest):
    _check_device(device_id)
    return await st.send_command(device_id, "thermostatCoolingSetpoint", "setCoolingSetpoint", [req.celsius])


@app.post("/devices/{device_id}/mode", dependencies=[Depends(require_api_key)])
async def set_mode(device_id: str, req: ModeRequest):
    _check_device(device_id)
    return await st.send_command(device_id, "airConditionerMode", "setAirConditionerMode", [req.mode])


@app.post("/devices/{device_id}/fan", dependencies=[Depends(require_api_key)])
async def set_fan(device_id: str, req: FanRequest):
    _check_device(device_id)
    return await st.send_command(device_id, "airConditionerFanMode", "setFanMode", [req.mode])


@app.post("/devices/{device_id}/oscillation", dependencies=[Depends(require_api_key)])
async def set_oscillation(device_id: str, req: OscillationRequest):
    _check_device(device_id)
    return await st.send_command(device_id, "fanOscillationMode", "setFanOscillationMode", [req.mode])


@app.post("/devices/{device_id}/command", dependencies=[Depends(require_api_key)])
async def generic_command(device_id: str, req: GenericCommand):
    """Comando SmartThings generico: copre qualsiasi capability del dispositivo."""
    _check_device(device_id)
    return await st.send_command(device_id, req.capability, req.command, req.arguments, req.component)


# ---------- modalità notturna ----------
@app.get("/nightmode", dependencies=[Depends(require_api_key)])
def get_nightmode() -> NightModeConfig:
    return night.load()


@app.put("/nightmode", dependencies=[Depends(require_api_key)])
def put_nightmode(cfg: NightModeConfig) -> NightModeConfig:
    return night.save(cfg)


@app.post("/nightmode/run-now", dependencies=[Depends(require_api_key)])
async def run_nightmode_now():
    return await night.apply(await _snapshot())


# ---------- pianificazioni ----------
@app.get("/schedules", dependencies=[Depends(require_api_key)])
def get_schedules() -> list[Schedule]:
    return automation.list_schedules()


@app.post("/schedules", dependencies=[Depends(require_api_key)])
def upsert_schedule(s: Schedule) -> Schedule:
    return automation.upsert_schedule(s)


@app.delete("/schedules/{schedule_id}", dependencies=[Depends(require_api_key)])
def delete_schedule(schedule_id: str):
    automation.delete_schedule(schedule_id)
    return {"deleted": schedule_id}


# ---------- away / eco ----------
@app.get("/away", dependencies=[Depends(require_api_key)])
def get_away() -> AwayState:
    return automation.away()


@app.put("/away", dependencies=[Depends(require_api_key)])
async def set_away(body: AwayState) -> AwayState:
    return await automation.set_away(body.enabled)


# ---------- sicurezza ----------
@app.get("/safety", dependencies=[Depends(require_api_key)])
def get_safety() -> SafetyConfig:
    return automation.safety()


@app.put("/safety", dependencies=[Depends(require_api_key)])
def put_safety(cfg: SafetyConfig) -> SafetyConfig:
    return automation.save_safety(cfg)


# ---------- notifiche ----------
@app.get("/notifications", dependencies=[Depends(require_api_key)])
def get_alerts():
    return notifications.list_alerts()


@app.get("/notifications/config", dependencies=[Depends(require_api_key)])
def get_notif_config() -> NotificationConfig:
    return notifications.config()


@app.put("/notifications/config", dependencies=[Depends(require_api_key)])
def put_notif_config(cfg: NotificationConfig) -> NotificationConfig:
    return notifications.save_config(cfg)


@app.post("/notifications/read", dependencies=[Depends(require_api_key)])
def mark_read(id: str | None = None):
    notifications.mark_read(id)
    return {"ok": True}


# ---------- prese smart (Tuya / Smart Life) ----------
@app.get("/plugs", dependencies=[Depends(require_api_key)])
async def list_plugs():
    if not tuya.available:
        return []
    try:
        return await run_in_threadpool(tuya.devices)
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"Tuya error: {e}")


@app.post("/plugs/{plug_id}/switch", dependencies=[Depends(require_api_key)])
async def switch_plug(plug_id: str, req: PlugSwitchRequest):
    if not tuya.available:
        raise HTTPException(status_code=503, detail="Tuya non configurato")
    return await run_in_threadpool(tuya.switch, plug_id, req.on)


# ---------- Pi-hole ----------
@app.get("/pihole/summary", dependencies=[Depends(require_api_key)])
async def pihole_summary():
    if not pihole.available:
        raise HTTPException(status_code=503, detail="Pi-hole non configurato")
    try:
        return await pihole.summary()
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"Pi-hole error: {e}")


@app.post("/pihole/blocking", dependencies=[Depends(require_api_key)])
async def pihole_blocking(req: PiholeBlockingRequest):
    if not pihole.available:
        raise HTTPException(status_code=503, detail="Pi-hole non configurato")
    return await pihole.set_blocking(req.enabled, req.seconds)
