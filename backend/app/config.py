"""Configurazione del backend, caricata da variabili d'ambiente / file .env."""
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # SmartThings (credenziali DEDICATE al backend)
    st_client_id: str
    st_client_secret: str
    st_redirect_uri: str = "https://httpbin.org/get"
    st_scopes: str = "r:devices:* x:devices:*"
    st_token_store: str = "token_store_backend.json"
    device_ids: str = ""

    # InfluxDB (sola lettura)
    influx_url: str
    influx_org: str
    influx_bucket: str
    influx_token: str

    # Sicurezza app -> backend
    app_api_key: str

    # Tuya / Smart Life (prese smart) — opzionale
    tuya_endpoint: str = "https://openapi.tuyaeu.com"
    tuya_access_id: str = ""
    tuya_access_secret: str = ""

    # Pi-hole (via tailnet) — opzionale
    pihole_url: str = ""        # es. http://<ip-tailscale-del-pi>
    pihole_password: str = ""

    # File di stato (persistenza locale)
    nightmode_config: str = "nightmode_config.json"
    schedules_store: str = "schedules.json"
    safety_config: str = "safety_config.json"
    away_state: str = "away_state.json"
    notifications_config: str = "notifications_config.json"
    alerts_store: str = "alerts.json"

    @property
    def device_id_list(self) -> list[str]:
        return [d.strip() for d in self.device_ids.split(",") if d.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
