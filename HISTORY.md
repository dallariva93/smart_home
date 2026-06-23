# Storico modifiche — Dashboard sensori Samsung

> Registro cronologico delle modifiche al progetto. **Va aggiornato dopo ogni
> modifica significativa** (codice, configurazione, deploy, infrastruttura).
> Voci in ordine cronologico **inverso** (più recente in alto).
> Formato voce: `## YYYY-MM-DD — Titolo breve` + elenco puntato di cosa è stato fatto.

---

## 2026-06-23 — Deploy backend con script (`deploy.ps1`)

- Aggiunto `backend/deploy.ps1`: impacchetta solo il codice (app/, requirements.txt,
  deploy/) escludendo `.venv`/`__pycache__`/segreti, trasferisce un tarball, estrae sulla
  VM, riavvia il servizio e verifica `is-active`. Flag `-Deps` (pip install) e `-Env`
  (invio `.env`). I file di stato/segreti sulla VM non vengono toccati.
- Sostituisce i comandi `scp`+`ssh` a mano (fonte dell'errore del `.` di troppo).
- Documentato in `backend/README.md` ("Deploy rapido").
- Possibili upgrade futuri annotati: CI/CD con GitHub Actions + `tailscale/github-action`
  (push-to-deploy), oppure Docker Compose. Non implementati per ora.

## 2026-06-23 — Webcam Wansview: analisi parcheggiata

- Analisi salvata in `docs/ESTENSIONI_WEBCAM.md` (RTSP/ONVIF, niente cloud Wansview
  pubblico; Tier A solo-LAN vs Tier B remoto con gateway go2rtc + tailnet; gateway su
  dispositivo di casa, non su Oracle). **Non in lavorazione** per ora, su richiesta.

## 2026-06-23 — Pi-hole da remoto (schermata nativa su API v6)

- Backend: nuovo `app/pihole.py` (client v6: auth → SID `X-FTL-SID`, summary, blocking),
  endpoint `GET /pihole/summary` e `POST /pihole/blocking`. Config `PIHOLE_URL`/
  `PIHOLE_PASSWORD` (se vuote → 503, app mostra "non configurato"). py_compile OK.
- Il backend raggiunge il Pi-hole **sul tailnet**: il Raspberry (192.168.1.18) va messo in
  Tailscale; `PIHOLE_URL` = IP/nome tailnet del Pi.
- App: schermata **Pi-hole** (Impostazioni → Pi-hole): query/bloccate/%/domini, stato
  blocco e **pausa 5/30 min / illimitata / riattiva**. Modelli + API + ViewModel estesi.
- In sospeso (utente): Tailscale sul Pi, password admin Pi-hole, `PIHOLE_*` nel `.env`.
  Taratura nomi campi API v6 dopo primo test.
- Nota deploy Tuya: il restart era fallito per un `.` di troppo nel comando ssh
  (`clima-backend".`); va rilanciato pulito `sudo systemctl restart clima-backend`.

## 2026-06-23 — Estensione multi-provider: prese Tuya/Smart Life

- Scelta architetturale: il backend diventa **hub multi-provider** (SmartThings + Tuya…).
- Backend: nuovo modulo `app/tuya.py` (Tuya Cloud API ufficiale via `tuya-connector-python`),
  endpoint `GET /plugs` e `POST /plugs/{id}/switch`. Config `TUYA_*` in `config.py`/`.env`
  (se vuota, integrazione disattivata e `/plugs` → `[]`). Chiamate Tuya (sincrone) eseguite
  via `run_in_threadpool`. Verificato con py_compile.
- App: modello `PlugState`, API plugs, `setPlug` nel ViewModel, sezione **"Prese"** in
  dashboard (nome, potenza W, interruttore on/off).
- Doc: `docs/ESTENSIONI_PIHOLE_TUYA.md` (analisi + stato di Tuya e Pi-hole); guida setup
  credenziali Tuya in `backend/README.md`.
- Prese previste: Power Bear AWP07L, Bakibo TP22Y (con consumi).
- In sospeso: setup credenziali Tuya (account iot.tuya.com + linking Smart Life), taratura
  scala `power_w`, storicizzazione consumi. **Pi-hole**: pianificato (Pi v6 su 192.168.1.18,
  accesso remoto via Pi su Tailscale + backend proxy / schermata nativa API v6).

## 2026-06-23 — Fase C ampliata: night mode v2, automazioni, notifiche, UI rinnovata

Backend (verificato con py_compile):
- **Night mode v2 temperature-aware** (`nightmode.py` riscritto): curva obiettivo con
  rampa + ritorno comfort prima del risveglio, anello chiuso sulla temperatura misurata
  con isteresi e soglia di spegnimento per risparmio. Solo comandi che cambiano stato.
- **`automation.py`**: pianificazioni orarie generiche (CRUD + applicazione al minuto),
  **Away/Eco** (spegne tutto + mette in pausa la night mode), **auto-spegnimento di
  sicurezza** (max ore di funzionamento, tracciate in memoria).
- **`notifications.py`**: notifiche in-app configurabili (soglie temp alta/bassa,
  collector offline via età ultimo dato Influx, filtro) con storico e cooldown.
- Comandi: aggiunti **oscillazione** e **comando generico** + endpoint **capabilities**.
- Statistiche giornaliere (`/devices/{id}/stats`) per i riepiloghi. Engine unico al minuto.
- Nuovi file di stato: schedules/safety/away/notifications/alerts (in `.gitignore`).

App Android:
- Tema Material3 **chiaro/scuro dinamico** (`Theme.kt`).
- Dashboard con card **Away/Eco**, badge notifiche non lette, hub Impostazioni.
- Controllo: aggiunta **oscillazione**; chip che evidenziano la selezione attiva.
- **Grafici potenziati**: `MultiSeriesChart` (assi/unità/griglia/etichette orario),
  **confronto stanze** (temperatura multi-device) e **riepilogo giornaliero**.
- Nuove schermate: night mode v2, **pianificazioni**, **sicurezza**, **config notifiche**,
  **lista notifiche**. ViewModel e API estese a tutti gli endpoint.
- Documentati in FASE_C_DESIGN §6 i punti **in sospeso**: widget home, blocco
  PIN/biometria, pull-to-refresh, push FCM, InfluxDB su Oracle, energia (limite hardware).

## 2026-06-23 — Grafici potenziati su pagina dedicata

- Spostati i grafici dalla schermata di controllo a una **pagina dedicata**
  (`DeviceChartScreen`, rotta `chart/{id}`), raggiunta da un pulsante "Grafici".
- Nuovo `TimeSeriesChart` (Canvas) con **assi e unità**: asse Y in gradi (°C) con
  griglia, asse X con etichette orario (HH:mm, o gg/MM per intervalli lunghi),
  **temperatura e setpoint sovrapposti** (setpoint tratteggiato) con legenda, e
  selettore intervallo **24h / 48h / 7g**. Asse X posizionato per timestamp reale.
- `loadHistory` parametrizzato per range/window; statistiche min/max/media temperatura.

## 2026-06-23 — App Android: build OK + migliorie (stato live, grafici, night mode)

- Build app risolta: Gradle JDK impostato a 17 (errore `--jvm-vendor`); dipendenza del
  convertitore JSON cambiata da `com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0`
  a quella ufficiale `com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0`
  (stesso package, import invariati). Aggiunto `gradle/wrapper/gradle-wrapper.properties` (Gradle 8.9).
- **Bug stato non aggiornato risolto**: la home leggeva da InfluxDB (lag ~2 min del Pi).
  Aggiunti al backend endpoint di **stato live** da SmartThings: `GET /state` (tutti) e
  `GET /devices/{id}/state` (singolo), con cache dei nomi device. L'app ora usa lo stato
  live e, dopo ogni comando, ricarica lo stato del device (con ~1.2s di attesa).
- **Grafici**: nella schermata di controllo, due grafici (Temperatura e Setpoint, ultime
  24h) per ogni clima, da `/history` (InfluxDB), disegnati con un `LineChart` Canvas
  (nessuna libreria esterna).
- **Modalità notturna**: scelta dei climatizzatori su cui applicarla (checkbox per device;
  default = quelli con "camera" nel nome) + box descrittivo di come funziona l'algoritmo
  (da aggiornare se cambia).
- App refactor: modello `DeviceState` (stato live) al posto di `DeviceLatest`; `ClimaViewModel`
  con refresh per-device dopo i comandi e caricamento storico per i grafici.

## 2026-06-23 — Deploy backend Fase C su Oracle Always Free + Tailscale

- Creata app OAuth SmartThings dedicata al backend ("Clima Backend", API_ONLY,
  scope `r:devices:* x:devices:*`, redirect `https://httpbin.org/get`); consenso con
  `authorize_backend.py` → `token_store_backend.json`.
- Risolti due intoppi di setup: valori finiti in `.env.example` invece di `.env`
  (copiato in `.env`); redirect non validato perché in `apps:create` era stato impostato
  il *Target URL* e non il *Redirect URI* → aggiunto con `apps:oauth:update`.
- Backend testato in locale (uvicorn + `/devices`), poi deployato su VM **Oracle Cloud
  Always Free**, shape **VM.Standard.E2.1.Micro** (Ubuntu); l'ARM A1.Flex risultava
  "out of capacity". IP pubblico assegnato per l'SSH.
- Copiato il backend in `~/clima/backend` (app/, deploy/, requirements.txt, `.env`,
  `token_store_backend.json`); creato venv e installato `requirements.txt`.
- Servizio **systemd `clima-backend`** attivo (active/running), bind su `127.0.0.1:8000`.
- **Tailscale** installato sulla VM. Inizialmente esposto in privato sul tailnet con
  `tailscale serve`, poi — per evitare la VPN sul telefono (Android consente una sola
  VPN attiva) — si è passati a **`tailscale funnel --bg 8000`**: il backend è ora
  **pubblico** in HTTPS su `https://clima-backend.tailebda1d.ts.net`, raggiungibile da
  qualsiasi dispositivo **senza Tailscale sul telefono**, senza aprire porte su Oracle e
  senza dominio. Guardia alla porta pubblica: l'**API key**.
- Valutate e scartate le alternative: Caddy+DuckDNS (più setup), mTLS (più sicuro ma
  richiede Caddy + certificato sul telefono + codice app), Cloudflare Tunnel+Access
  (richiede dominio a pagamento), VPN tailnet sul telefono (la seccatura da evitare).
  mTLS resta un possibile upgrade futuro per blindare la porta pubblica.
- Aggiornato `android/.../Config.kt` con `BASE_URL` (URL ts.net) e `API_KEY`.

## 2026-06-22 — Fase C: backend FastAPI + app Android (scaffold)

- Decisi: **hosting backend su Oracle Cloud Always Free** (VM ARM, sempre accesa,
  no sleep, gratis) e **frontend app Android nativa** (Kotlin + Compose), al posto
  della PWA.
- Creato `FASE_C_DESIGN.md`: architettura, contratto API, spec modalità notturna,
  separazione credenziali SmartThings backend/Pi, deploy.
- Creato `backend/` (FastAPI):
  - `app/smartthings.py` — client OAuth async (refresh + stato + comandi);
  - `app/influx.py` — letture storico / ultime letture;
  - `app/nightmode.py` — config + algoritmo v1 + tick scheduler (fallback su DEVICE_IDS);
  - `app/models.py`, `app/security.py` (API key X-API-Key), `app/config.py`;
  - `app/main.py` — rotte letture/comandi/nightmode + APScheduler (ogni 10 min);
  - `authorize_backend.py` (consenso OAuth dedicato), `deploy/clima-backend.service`,
    `deploy/Caddyfile`, `requirements.txt`, `.env.example`, `.gitignore`, `README.md`.
  - Sintassi verificata con `py_compile`.
- Creato `android/` (scaffold Kotlin + Jetpack Compose): progetto Gradle,
  `Config.kt` (BASE_URL/API_KEY), `net/Models.kt` + `net/Api.kt` (Retrofit + header
  API key), `ClimaViewModel.kt`, `MainActivity.kt` (dashboard, controllo on/off/
  setpoint/modalità/ventola, schermata modalità notturna), `README.md`.
- ⚠️ Nota architetturale: il backend usa una **seconda app OAuth SmartThings** con
  scope `r:devices:* x:devices:*` (la `x` abilita i comandi), separata da quella del Pi
  per non far collidere i refresh token che ruotano.
- Da completare: rifinitura UI Android + build APK in Android Studio; eventuale
  migrazione di InfluxDB sulla VM Oracle (Fase B).
- Aggiornati `PROSEGUIMENTO_SVILUPPI.md` (Fase C + tabella file) e `FASE_C_DESIGN.md`.

## 2026-06-22 — Aggiunta tracciamento storico

- Creato `HISTORY.md` (questo file) con voci retrospettive del lavoro
  svolto oggi e convenzioni per le voci future.
- Aggiunto `HISTORY.md` alla tabella file in `PROSEGUIMENTO_SVILUPPI.md` §4.

## 2026-06-22 — Deploy POC sul Raspberry Pi

- Generata chiave SSH ed25519 sul PC (`~/.ssh/id_ed25519`) e installata su
  `pi@192.168.1.18` per accesso senza password.
- Creata directory `/home/pi/clima/poc/` sul Pi.
- Copiati sul Pi: `oauth.py`, `poller.py`, `explore.py`, `requirements.txt`,
  `.env`, `token_store.json` (esclusi `authorize.py`, `.venv`, `__pycache__`,
  `grafana_dashboard.json`, `README.md`).
- Permessi 600 su `.env` e `token_store.json`.
- Creato venv `/home/pi/clima/poc/.venv` e installati `requests`,
  `python-dotenv`, `influxdb-client` da `requirements.txt`.
- Verificato test manuale: `poller.py` scrive 2 punti su InfluxDB
  (bucket `casa_sensori`).
- Aggiunta entry cron `*/2 * * * *` per l'utente `pi` con redirect log su
  `/home/pi/clima/poc/poller.log`. Verificata esecuzione automatica.
- Aggiunto logrotate `/etc/logrotate.d/clima-poller`: rotazione giornaliera,
  `rotate 90` (mantiene 90 giorni ~ 3 mesi), `compress`, `delaycompress`,
  `create 644 pi pi`, `su pi pi`. Testato con `logrotate -f`.
- Permessi directory `/home/pi/clima/poc` corretti a `755` (richiesto da
  logrotate per evitare errore "insecure permissions").

## 2026-06-22 — Analisi dati raccolti

- Eseguito `explore.py` sul Pi per dump completo delle capability SmartThings.
- Confermato che `temperature` (da `temperatureMeasurement.temperature`) e
  `cooling_setpoint` (da `thermostatCoolingSetpoint.coolingSetpoint`) sono
  due field distinti in Influx; eventuali ambiguità in Grafana sono di
  configurazione legenda.
- Rilevato che entrambi i device (modello ARTIK051_PRAC_20K) hanno in
  `custom.disabledCapabilities.disabledCapabilities`:
  `relativeHumidityMeasurement`, `airQualitySensor`, `dustSensor`,
  `veryFineDustSensor`, `odorSensor`, `powerConsumptionReport`,
  filtri custom. Tutti i relativi field arrivano `None` e vengono saltati
  dal poller.
- Anomalia individuata: per il device "Condizionatore salotto", la
  `powerConsumptionReport.powerConsumption` restituisce uno snapshot
  **congelato** (`end: 2026-04-07`, `power: 0`, `energy: 573843`).
  I field `power_w` ed `energy_wh` scritti su Influx per questo device
  sono quindi dati stale. Da valutare: filtrare in `poller.py` ignorando
  snapshot più vecchi di N ore. Non ancora implementato.

## 2026-06-22 — Fix autorizzazione OAuth, dashboard Grafana e doc proseguimento

- Risolto il **403 in fase di authorize OAuth**: causa finale, SmartThings non
  ammette `http://localhost` come redirect URI. Eliminata anche l'app OAuth
  duplicata (priva di redirect) e riportato lo scope a `r:devices:*` (senza
  virgole). Redirect impostato su `https://httpbin.org/get` e consenso
  completato con `python authorize.py --manual`.
- Creato `poc/grafana_dashboard.json`: dashboard importabile con 6 pannelli
  (temperatura+setpoint, umidità, qualità aria/polveri, potenza, energia,
  tabella stato), query Flux, datasource via input `DS_INFLUXDB`.
- Creato `PROSEGUIMENTO_SVILUPPI.md`: roadmap (fasi A–D) e guida di deploy
  sul Raspberry.

## 2026-06-19 — Creazione POC (scaffolding iniziale)

- Aggiornata `DOCUMENTAZIONE_SVILUPPO.md`: auth della POC impostata su
  **OAuth2** al posto del PAT (flusso a 2 fasi, rotazione e persistenza del
  refresh token); aggiornati config (§7), roadmap e prossimi passi.
- Creata cartella `poc/`:
  - `oauth.py` — token store con scrittura atomica + refresh automatico;
  - `authorize.py` — consenso OAuth una-tantum, con fallback `--manual` per
    incollare il `code` quando il redirect non è su localhost;
  - `poller.py` — collector SmartThings → InfluxDB con estrazione difensiva
    dei field e gestione del dict di `powerConsumptionReport`;
  - `explore.py` — ispezione delle capability/attributi reali;
  - `requirements.txt`, `.env.example`, `.gitignore`, `README.md`.

---

## Convenzioni

- Ogni voce indica **cosa è cambiato**, non il "perché ovvio".
- Per modifiche a file di codice, citare i file toccati.
- Per modifiche infrastrutturali (cron, systemd, logrotate, permessi),
  riportare path assoluti e parametri chiave.
- Anomalie e debiti tecnici noti vanno annotati anche se non risolti,
  così non si perdono.
