"""Modelli di request/response del backend."""
from pydantic import BaseModel, Field


# ---------- comandi ----------
class PowerRequest(BaseModel):
    state: str = Field(pattern="^(on|off)$")


class SetpointRequest(BaseModel):
    celsius: float = Field(ge=16, le=30)


class ModeRequest(BaseModel):
    mode: str  # cool, heat, dry, wind, auto


class FanRequest(BaseModel):
    mode: str  # auto, low, medium, high, turbo


class OscillationRequest(BaseModel):
    mode: str  # fixed, all, vertical, horizontal


class GenericCommand(BaseModel):
    """Comando SmartThings generico, per coprire qualsiasi capability del dispositivo."""
    capability: str
    command: str
    arguments: list = []
    component: str = "main"


class PlugSwitchRequest(BaseModel):
    on: bool


class PiholeBlockingRequest(BaseModel):
    enabled: bool
    seconds: int | None = None


# ---------- modalità notturna (v2, temperature-aware) ----------
class NightModeConfig(BaseModel):
    enabled: bool = False
    device_ids: list[str] = []
    start: str = Field(default="23:00", pattern=r"^\d{2}:\d{2}$")
    end: str = Field(default="07:00", pattern=r"^\d{2}:\d{2}$")
    target_temp: float = Field(default=25, ge=16, le=30)       # comfort all'addormentamento
    night_offset: float = Field(default=2, ge=0, le=6)          # quanto più caldo nel cuore della notte
    ramp_minutes: int = Field(default=120, ge=0, le=480)        # durata salita da comfort a comfort+offset
    pre_wake_minutes: int = Field(default=45, ge=0, le=240)     # ritorno al comfort prima del risveglio
    hysteresis: float = Field(default=0.5, ge=0.1, le=3)        # banda morta anti-pendolamento
    power_off_margin: float = Field(default=1.5, ge=0.5, le=5)  # quanto sotto target spegnere per risparmio
    fan_mode: str = "low"
    ac_mode: str = "cool"
    min_setpoint: float = Field(default=16, ge=16, le=30)
    max_setpoint: float = Field(default=30, ge=16, le=30)


# ---------- pianificazioni ----------
class ScheduleAction(BaseModel):
    # power_on | power_off | setpoint | mode | fan | away_on | away_off
    type: str
    value: str | None = None  # numero (per setpoint) o stringa (mode/fan)


class Schedule(BaseModel):
    id: str | None = None
    enabled: bool = True
    time: str = Field(pattern=r"^\d{2}:\d{2}$")
    days: list[int] = []          # 0=lun..6=dom; vuoto = ogni giorno
    targets: list[str] = []       # device id; vuoto = tutti
    action: ScheduleAction
    label: str = ""


# ---------- away / eco ----------
class AwayState(BaseModel):
    enabled: bool = False


# ---------- sicurezza (auto-spegnimento) ----------
class SafetyConfig(BaseModel):
    enabled: bool = False
    max_runtime_hours: float = Field(default=8, ge=0.5, le=48)


# ---------- notifiche ----------
class NotificationConfig(BaseModel):
    temp_high_enabled: bool = False
    temp_high_threshold: float = 30
    temp_low_enabled: bool = False
    temp_low_threshold: float = 10
    offline_enabled: bool = True
    offline_minutes: int = Field(default=15, ge=3, le=240)
    filter_enabled: bool = True


class Alert(BaseModel):
    id: str
    time: str
    type: str
    message: str
    device_id: str | None = None
    device_name: str | None = None
    read: bool = False
