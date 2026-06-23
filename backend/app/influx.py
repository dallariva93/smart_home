"""Lettura serie storiche e ultime letture da InfluxDB (measurement 'clima')."""
from influxdb_client import InfluxDBClient


class InfluxReader:
    def __init__(self, url: str, token: str, org: str, bucket: str):
        self.bucket = bucket
        self.client = InfluxDBClient(url=url, token=token, org=org)
        self.query_api = self.client.query_api()

    def latest(self) -> list[dict]:
        """Ultimo valore di ogni field per ciascun device (finestra 2h)."""
        flux = f'''
from(bucket: "{self.bucket}")
  |> range(start: -2h)
  |> filter(fn: (r) => r._measurement == "clima")
  |> last()
'''
        result: dict[str, dict] = {}
        for table in self.query_api.query(flux):
            for rec in table.records:
                key = rec.values.get("device_id", "?")
                entry = result.setdefault(
                    key,
                    {
                        "device_id": rec.values.get("device_id"),
                        "device_name": rec.values.get("device_name"),
                        "time": rec.get_time().isoformat(),
                        "fields": {},
                    },
                )
                entry["fields"][rec.get_field()] = rec.get_value()
        return list(result.values())

    def history(self, device_id: str, field: str, range_str: str, window: str) -> list[dict]:
        """Serie temporale di un field per un device, con media per finestra."""
        flux = f'''
from(bucket: "{self.bucket}")
  |> range(start: -{range_str})
  |> filter(fn: (r) => r._measurement == "clima")
  |> filter(fn: (r) => r.device_id == "{device_id}")
  |> filter(fn: (r) => r._field == "{field}")
  |> aggregateWindow(every: {window}, fn: mean, createEmpty: false)
'''
        points: list[dict] = []
        for table in self.query_api.query(flux):
            for rec in table.records:
                points.append({"time": rec.get_time().isoformat(), "value": rec.get_value()})
        return points

    def daily_stats(self, device_id: str, field: str, days: int) -> list[dict]:
        """Min/max/media per giorno di un field (riepiloghi giornalieri/settimanali)."""
        out: dict[str, dict] = {}
        for fn in ("min", "max", "mean"):
            flux = f'''
from(bucket: "{self.bucket}")
  |> range(start: -{days}d)
  |> filter(fn: (r) => r._measurement == "clima")
  |> filter(fn: (r) => r.device_id == "{device_id}")
  |> filter(fn: (r) => r._field == "{field}")
  |> aggregateWindow(every: 1d, fn: {fn}, createEmpty: false)
'''
            for table in self.query_api.query(flux):
                for rec in table.records:
                    day = rec.get_time().date().isoformat()
                    out.setdefault(day, {"day": day})[fn] = rec.get_value()
        return [out[d] for d in sorted(out)]

    def last_age_minutes(self, device_id: str) -> float | None:
        """Minuti trascorsi dall'ultimo punto scritto dal Pi (per rilevare il collector offline)."""
        flux = f'''
from(bucket: "{self.bucket}")
  |> range(start: -1d)
  |> filter(fn: (r) => r._measurement == "clima")
  |> filter(fn: (r) => r.device_id == "{device_id}")
  |> last()
'''
        from datetime import datetime, timezone
        latest = None
        for table in self.query_api.query(flux):
            for rec in table.records:
                t = rec.get_time()
                if latest is None or t > latest:
                    latest = t
        if latest is None:
            return None
        return (datetime.now(timezone.utc) - latest).total_seconds() / 60
