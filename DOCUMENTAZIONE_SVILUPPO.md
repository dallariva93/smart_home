# Documentazione di sviluppo — Dashboard sensori Samsung (POC)

> **Stato:** POC (Proof of Concept)
> **Obiettivo POC:** raccogliere periodicamente i dati dei climatizzatori Samsung
> tramite l'API SmartThings e visualizzarli come storico con grafici, accessibili
> da telefono/tablet.

---

## 1. Visione e obiettivi

**Obiettivo finale (lungo termine):** un'applicazione con dashboard per monitorare
i sensori di casa (a partire dai climatizzatori Samsung), con storico consultabile
tramite grafici, accessibile da telefono o da un tablet sempre disponibile in casa.
In futuro: integrazione di **altri sensori** e possibilità di **azionare i dispositivi**.

**Obiettivo di questa POC:** validare la catena
"Raspberry preleva i dati → cloud li conserva → li vedo come grafici da telefono",
con il minimo sforzo e a **costo zero**.

### Principi guida
- **Costo €0**: solo servizi con free tier.
- **Il Raspberry Pi 2 è solo un collector**: interroga le API e spedisce i dati.
  Non ospita database, dashboard o web server (1 GB RAM, CPU ARMv7 32-bit).
- **Procediamo per step**: stack proposto → documentazione → POC → sviluppo vero.

---

## 2. ⚠️ Nota sull'evoluzione dello stack

> **Questo stack è scelto per la POC e cambierà in futuro.**
>
> La scelta attuale (InfluxDB Cloud + Grafana Cloud) è ottimizzata per **vedere
> velocemente lo storico con grafici, a costo zero e con pochissimo codice**.
> È però una soluzione **in sola lettura**: non consente di azionare i dispositivi.
>
> Quando il progetto verrà esteso — in particolare:
> - aggiunta di **altri sensori di casa** (non solo Samsung/SmartThings),
> - necessità di **comandare i dispositivi** dall'app (`POST /devices/{id}/commands`),
> - esigenza di **storico oltre i limiti del free tier** (vedi §8),
>
> si prevede la **migrazione verso uno stack applicativo custom**, ad esempio:
> **backend FastAPI + database (Postgres/Supabase) + frontend PWA** installabile su
> telefono/tablet, in cui il Raspberry resta collector ma il cloud gestisce sia la
> lettura (grafici) sia la scrittura dei comandi. Vedi la **Roadmap (§9)**.

---

## 3. Architettura (POC)

```
┌──────────────────────────┐
│   Raspberry Pi 2         │
│   (solo collector)       │
│                          │
│   cron ogni N minuti     │
│   └─ poller.py           │
│        1) GET status     │  ───────► SmartThings API
│           per ogni device│          (api.smartthings.com)
│        2) scrive metriche │
└────────────┬─────────────┘
             │ line protocol / client InfluxDB (HTTPS)
             ▼
┌──────────────────────────┐
│   InfluxDB Cloud          │   storage time-series (free tier)
│   (bucket: casa_sensori)  │
└────────────┬─────────────┘
             │ query (Flux)
             ▼
┌──────────────────────────┐
│   Grafana Cloud           │   dashboard + grafici (free tier)
│   (datasource: InfluxDB)  │
└────────────┬─────────────┘
             │ HTTPS (URL pubblico, login)
             ▼
     📱 Telefono / Tablet   (in casa e fuori, nessun tunnel necessario)
```

### Perché questa architettura
- Il Pi fa **solo** chiamate HTTP in uscita: nessuna porta aperta, nessun servizio
  esposto, consumo di risorse minimo.
- InfluxDB Cloud e Grafana Cloud sono **hostati**: la dashboard è raggiungibile da
  qualsiasi rete senza VPN/tunnel.
- Grafana fornisce grafici, soglie e alert **senza scrivere frontend**.

---

## 4. Componenti

### 4.1 Raspberry Pi 2 — poller
- **Linguaggio:** Python 3.
- **Compito:** ogni N minuti, per ogni device configurato, chiama
  `GET /v1/devices/{deviceId}/status`, estrae le metriche e le scrive su InfluxDB Cloud.
- **Schedulazione:** `cron` (es. ogni 2 minuti). In alternativa un `systemd` timer.
- **Dipendenze:** `requests` (o `aiohttp`) + `influxdb-client`.
- **Configurazione** via variabili d'ambiente / file `.env` (vedi §7):
  - token SmartThings,
  - elenco device ID,
  - URL/org/bucket/token di InfluxDB Cloud,
  - intervallo di polling.

### 4.2 InfluxDB Cloud — storage
- Database time-series gestito, free tier.
- **Bucket** dedicato (es. `casa_sensori`).
- **Token di scrittura** usato dal Pi; **token di lettura** usato da Grafana.

### 4.3 Grafana Cloud — visualizzazione
- Istanza Grafana hostata, free tier.
- **Datasource** InfluxDB (verso il bucket cloud).
- **Dashboard** con pannelli per temperatura, umidità, qualità aria, consumo, ecc.
- Accesso da browser su telefono/tablet (Grafana è responsive).

---

## 5. Modello dati

Una scrittura per ciclo di polling, per dispositivo.

- **measurement:** `clima` (estendibile in futuro con altri measurement per nuovi sensori)
- **tag** (indicizzati, identificano la sorgente):
  - `device_id` — es. `d878b679-bfd2-b7a7-0c76-eeccb3bf500f`
  - `device_name` — es. `Condizionatore salotto`
- **field** (i valori misurati):

| Field                  | Tipo    | Capability SmartThings                         |
|------------------------|---------|------------------------------------------------|
| `temperature`          | float   | `temperatureMeasurement`                       |
| `humidity`             | float   | `relativeHumidityMeasurement`                  |
| `cooling_setpoint`     | float   | `thermostatCoolingSetpoint`                    |
| `power_consumption`    | float   | `powerConsumptionReport`                       |
| `air_quality`          | int/str | `airQualitySensor`                             |
| `dust_level`           | float   | `dustSensor`                                   |
| `fine_dust_level`      | float   | `veryFineDustSensor`                           |
| `odor_level`           | int/str | `odorSensor`                                   |
| `power_state`          | string  | `switch` (`on`/`off`)                          |
| `ac_mode`              | string  | `airConditionerMode`                           |
| `fan_mode`             | string  | `airConditionerFanMode`                        |
| `oscillation_mode`     | string  | `fanOscillationMode`                           |
| `dust_filter_status`   | string  | `custom.dustFilter`                            |
| `fine_dust_filter_*`   | string  | `custom.veryFineDustFilter`                    |
| `deodor_filter_status` | string  | `custom.deodorFilter`                          |
| `hepa_filter_status`   | string  | `custom.electricHepaFilter`                    |

> **Robustezza:** non tutti i campi sono presenti su tutti i dispositivi. Il poller
> scrive **solo i field effettivamente disponibili** nella risposta (estrazione
> difensiva con `.get(...)`), evitando errori se una capability manca.
>
> **Timestamp:** usare il timestamp di acquisizione del Pi (UTC). Opzionalmente, se
> presente nei dati SmartThings, registrare anche il timestamp del dato sorgente.

### Esempio (line protocol)
```
clima,device_id=d878b679...,device_name=Condizionatore\ salotto temperature=24.5,humidity=48,cooling_setpoint=22,power_state="on",ac_mode="cool" 1718800000000000000
```

---

## 6. Autenticazione SmartThings — OAuth2

Per la POC si usa **OAuth2 fin da subito** (non il PAT): il PAT scade dopo 24h e
costringerebbe a rigenerarlo a mano ogni giorno su un Pi non presidiato. OAuth2 è
**headless-friendly** e, una volta configurato, è **senza manutenzione**.

### Perché OAuth2 funziona su un Pi senza frontend
Il flusso si divide in due momenti distinti:

1. **Autorizzazione iniziale (una tantum) — richiede un browser, NON un frontend.**
   Si apre un URL di autorizzazione, si fa login col proprio account Samsung e si
   approvano gli scope. Samsung reindirizza al `redirect_uri` con un `code` nell'URL.
   Il `code` si recupera dal **browser del proprio PC** in modo banale (copia-incolla
   dalla barra degli indirizzi **oppure** un mini-listener locale usa-e-getta da ~20
   righe). Non serve costruire alcuna interfaccia.
2. **Funzionamento continuo sul Pi — 100% backend, nessuna UI.**
   Ottenuti `access_token` + `refresh_token`, il Pi lavora da solo:
   - `access_token`: validità ~24h;
   - `refresh_token`: validità ~30 giorni, **ruota a ogni refresh** (ogni rinnovo
     restituisce un nuovo refresh token);
   - quando l'access token scade, il poller fa un `POST` al token endpoint con il
     refresh token e ottiene nuovi token.

   Poiché il polling è continuo, il rinnovo avviene sempre ben dentro la finestra dei
   30 giorni → **in pratica non scade mai**. Unico caveat: se il Pi resta **offline
   > 30 giorni**, il refresh token muore e va rifatto il consenso una tantum.

### ⚠️ Dettaglio implementativo critico — persistenza del refresh token
Poiché il refresh token **cambia a ogni rinnovo**, il Pi deve **salvarlo su disco**
(file di stato, es. `token_store.json`) **dopo ogni refresh**. Se al riavvio usasse il
refresh token vecchio (già ruotato), otterrebbe un errore e si dovrebbe rifare il
consenso. Il file di stato è quindi parte essenziale del poller.

### Setup OAuth (una tantum)
- Creare un'app OAuth su SmartThings via **SmartThings CLI** (`smartthings apps:create`,
  tipo *OAuth-In*) o dal **Developer Workspace**.
- Si ottengono `client_id` e `client_secret`; si definiscono `redirect_uri` e gli
  scope (`r:devices:*`, `r:locations:*`).
- Endpoint principali:
  - Authorize: `https://api.smartthings.com/oauth/authorize`
  - Token:     `https://auth-global.api.smartthings.com/oauth/token`

### Sicurezza
- `client_secret`, token e refresh token **mai** committati nel repo: solo in
  `.env` / file di stato locale (entrambi in `.gitignore`).
- Token di scrittura InfluxDB sul Pi; token di sola lettura per Grafana.

---

## 7. Configurazione (esempio `.env`)

```env
# SmartThings OAuth2
ST_CLIENT_ID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
ST_CLIENT_SECRET=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
ST_REDIRECT_URI=http://localhost:8080/callback
ST_SCOPES=r:devices:*
# File dove vengono salvati/aggiornati access e refresh token (refresh token ruota!)
ST_TOKEN_STORE=token_store.json
DEVICE_IDS=d878b679-bfd2-b7a7-0c76-eeccb3bf500f,dae77b84-a355-3757-3273-b21855e63db2

# InfluxDB Cloud
INFLUX_URL=https://<region>.aws.cloud2.influxdata.com
INFLUX_ORG=la-tua-org
INFLUX_BUCKET=casa_sensori
INFLUX_TOKEN=token-di-scrittura

# Polling
POLL_INTERVAL_MIN=2
```

### Esempio crontab sul Pi (ogni 2 minuti)
```cron
*/2 * * * * cd /home/pi/clima && /usr/bin/python3 poller.py >> /home/pi/clima/poller.log 2>&1
```

---

## 8. Vincoli e limiti

### Rate limit SmartThings
- `GET device status`: **350 richieste/minuto** → con 2 device ogni 2 min siamo
  a ~2 richieste/2 min: ampio margine.
- `List devices`: 1000 richieste/15 min (usato solo in fase di discovery).

### Free tier (da verificare al momento dell'attivazione)
- **InfluxDB Cloud (Free):** retention dati limitata (storicamente ~30 giorni) e
  limiti di scrittura/lettura. **Implicazione:** lo storico "lungo" non è garantito
  sul free tier → motivo aggiuntivo per la futura migrazione (§9).
- **Grafana Cloud (Free):** limiti su utenti, serie e retention metriche.

### Raspberry Pi 2
- 1 GB RAM, ARMv7 32-bit: nessun problema perché esegue **solo** il poller Python.

---

## 9. Roadmap (post-POC)

1. **Storico a lungo termine** → quando il free tier non basta, migrare lo storage
   (es. Postgres/Supabase self-managed o piano a pagamento minimo).
2. **Altri sensori di casa** → nuovi `measurement`/sorgenti oltre a SmartThings.
3. **App custom + controllo dispositivi** → backend FastAPI + DB + **PWA** installabile,
   con endpoint per `POST /devices/{id}/commands`. **Lo stack cambierà** rispetto a
   questa POC (vedi §2).

---

## 10. Prossimi passi (sviluppo POC)

1. Creare l'app OAuth su SmartThings (CLI o Developer Workspace) → `client_id`/`secret`/scope.
2. Eseguire una tantum l'autorizzazione (`authorize.py`) per ottenere il primo refresh token.
3. Creare account InfluxDB Cloud → org + bucket + token di scrittura.
4. Eseguire `poller.py` in locale (estrazione difensiva + refresh automatico + scrittura su InfluxDB).
5. Schedulare `poller.py` via cron sul Pi.
6. Collegare Grafana Cloud a InfluxDB e creare la dashboard.
7. Aprire la dashboard da telefono/tablet e validare lo storico.

---

## Riferimenti
- [SmartThings API Documentation](https://developer.smartthings.com/docs/api/public/)
- [InfluxDB Cloud](https://www.influxdata.com/products/influxdb-cloud/)
- [Grafana Cloud](https://grafana.com/products/cloud/)
- Guida dati dispositivo: `GUIDA_DATI_CLIMATIZZATORE_SAMSUNG.md`
