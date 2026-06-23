"""Poller SmartThings -> InfluxDB Cloud.

Da eseguire periodicamente (cron / systemd timer) sul Raspberry Pi. A ogni run:
 1. ottiene un access token valido (refresh automatico se scaduto);
 2. per ogni device interroga GET /devices/{id}/status;
 3. estrae in modo difensivo i field disponibili;
 4. scrive un punto 'clima' per device su InfluxDB Cloud.
"""
import logging
import os
import sys

import requests
from dotenv import load_dotenv
from influxdb_client import InfluxDBClient, Point
from influxdb_client.client.write_api import SYNCHRONOUS

import oauth

load_dotenv()
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("poller")

CLIENT_ID = os.environ["ST_CLIENT_ID"]
CLIENT_SECRET = os.environ["ST_CLIENT_SECRET"]
STORE = oauth.TokenStore(os.environ.get("ST_TOKEN_STORE", "token_store.json"))
DEVICE_IDS = [d.strip() for d in os.environ.get("DEVICE_IDS", "").split(",") if d.strip()]

INFLUX_URL = os.environ["INFLUX_URL"]
INFLUX_ORG = os.environ["INFLUX_ORG"]
INFLUX_BUCKET = os.environ["INFLUX_BUCKET"]
INFLUX_TOKEN = os.environ["INFLUX_TOKEN"]

STATUS_URL = "https://api.smartthings.com/v1/devices/{device_id}/status"
DEVICE_URL = "https://api.smartthings.com/v1/devices/{device_id}"

# field numerico -> (capability, attributo)
NUMERIC_FIELDS = {
    "temperature": ("temperatureMeasurement", "temperature"),
    "humidity": ("relativeHumidityMeasurement", "humidity"),
    "cooling_setpoint": ("thermostatCoolingSetpoint", "coolingSetpoint"),
    "air_quality": ("airQualitySensor", "airQuality"),
    "dust_level": ("dustSensor", "dustLevel"),
    "fine_dust_level": ("veryFineDustSensor", "fineDustLevel"),
    "odor_level": ("odorSensor", "odorLevel"),
}
# field stringa (stati / modalità) -> (capability, attributo)
# NB: i nomi attributo dei custom.* filtri possono variare per modello: verifica con explore.py
STRING_FIELDS = {
    "power_state": ("switch", "switch"),
    "ac_mode": ("airConditionerMode", "airConditionerMode"),
    "fan_mode": ("airConditionerFanMode", "fanMode"),
    "oscillation_mode": ("fanOscillationMode", "fanOscillationMode"),
    "dust_filter_status": ("custom.dustFilter", "dustFilterStatus"),
    "fine_dust_filter_status": ("custom.veryFineDustFilter", "veryFineDustFilterStatus"),
    "deodor_filter_status": ("custom.deodorFilter", "deodorFilterStatus"),
    "hepa_filter_status": ("custom.electricHepaFilter", "electricHepaFilterStatus"),
}


def _attr_value(main, capability, attribute):
    cap = main.get(capability)
    if not cap:
        return None
    attr = cap.get(attribute)
    if not attr:
        return None
    return attr.get("value")


def fetch_status(device_id, access_token):
    resp = requests.get(
        STATUS_URL.format(device_id=device_id),
        headers={"Authorization": f"Bearer {access_token}"},
        timeout=30,
    )
    resp.raise_for_status()
    return resp.json()


def fetch_name(device_id, access_token):
    """Etichetta leggibile del device; in caso di errore ripiega sul device_id."""
    try:
        resp = requests.get(
            DEVICE_URL.format(device_id=device_id),
            headers={"Authorization": f"Bearer {access_token}"},
            timeout=30,
        )
        resp.raise_for_status()
        d = resp.json()
        return d.get("label") or d.get("name") or device_id
    except requests.RequestException:
        return device_id


def build_point(device_id, device_name, status):
    main = status.get("components", {}).get("main", {})
    point = Point("clima").tag("device_id", device_id).tag("device_name", device_name)
    n_fields = 0

    for field, (cap, attr) in NUMERIC_FIELDS.items():
        val = _attr_value(main, cap, attr)
        if val is None:
            continue
        try:
            point.field(field, float(val))
            n_fields += 1
        except (TypeError, ValueError):
            log.warning("Campo %s non numerico (%r), saltato", field, val)

    for field, (cap, attr) in STRING_FIELDS.items():
        val = _attr_value(main, cap, attr)
        if val is None:
            continue
        point.field(field, str(val))
        n_fields += 1

    # consumo energetico: powerConsumption ha come valore un dict (power, energy, ...)
    pc = _attr_value(main, "powerConsumptionReport", "powerConsumption")
    if isinstance(pc, dict):
        if pc.get("power") is not None:
            point.field("power_w", float(pc["power"]))
            n_fields += 1
        if pc.get("energy") is not None:
            point.field("energy_wh", float(pc["energy"]))
            n_fields += 1

    return point, n_fields


def main():
    if not DEVICE_IDS:
        log.error("Nessun DEVICE_IDS configurato in .env")
        sys.exit(1)

    access_token = oauth.get_access_token(CLIENT_ID, CLIENT_SECRET, STORE)

    points = []
    for device_id in DEVICE_IDS:
        try:
            status = fetch_status(device_id, access_token)
        except requests.HTTPError as e:
            log.error("Errore status %s: %s", device_id, e)
            continue
        name = fetch_name(device_id, access_token)
        point, n = build_point(device_id, name, status)
        if n == 0:
            log.warning("Nessun field estratto per %s (%s)", name, device_id)
            continue
        points.append(point)
        log.info("Raccolti %d field da '%s' (%s)", n, name, device_id)

    if not points:
        log.warning("Nessun dato da scrivere.")
        return

    with InfluxDBClient(url=INFLUX_URL, token=INFLUX_TOKEN, org=INFLUX_ORG) as client:
        write_api = client.write_api(write_options=SYNCHRONOUS)
        write_api.write(bucket=INFLUX_BUCKET, record=points)
    log.info("Scritti %d punti su InfluxDB (bucket '%s').", len(points), INFLUX_BUCKET)


if __name__ == "__main__":
    main()
