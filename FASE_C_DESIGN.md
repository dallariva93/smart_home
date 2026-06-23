# Fase C — App custom + controllo dispositivi (design)

> Stato: **in sviluppo**. Backend in `backend/`, app Android in `android/`.
> Prerequisito deciso: hosting backend su **Oracle Cloud Always Free**, frontend
> **app Android nativa** (Kotlin + Jetpack Compose).

---

## 1. Obiettivi della fase

- **Tutte le letture attuali**: i dati che oggi vedi su Grafana (temperatura, umidità,
  setpoint, qualità aria/polveri, potenza/energia, stati e filtri) disponibili nell'app.
- **Controllo dei condizionatori**: accensione/spegnimento, modalità, setpoint, ventola.
- **Modalità notturna custom**: un algoritmo lato server che regola automaticamente i
  condizionatori durante la notte (prima versione inclusa, da rivedere insieme).

---

## 2. Architettura

```
                         ┌───────────────────────────────────────────┐
                         │        Oracle Cloud Always Free (VM)        │
                         │                                             │
   Raspberry Pi 2 ──────►│   InfluxDB  ◄── (storico, scritto dal Pi)   │
   (collector, invariato)│      ▲                                      │
                         │      │ lettura                              │
   📱 App Android ──────►│   FastAPI backend ──► SmartThings API       │
        (HTTPS + API key)│      │  - letture (storico da InfluxDB)     │  (comandi:
                         │      │  - stato live (da SmartThings)       │   POST /commands)
                         │      │  - comandi (POST /commands)          │
                         │      │  - scheduler modalità notturna       │
                         │      └──────────────────────────────────────┘
                         └───────────────────────────────────────────┘
```

### Punti chiave
- **Il Pi resta il collector** (Fase A invariata): continua a scrivere lo storico.
- **Il backend è il cervello**: legge lo storico, interroga lo stato live e **invia
  comandi**. Gira sempre acceso sulla VM Oracle (niente sleep).
- **Lo scheduler della modalità notturna gira nel backend**, non nell'app: così funziona
  anche con il telefono spento/app chiusa.
- **InfluxDB**: in questa fase può ancora essere il Cloud (free, ~30gg) oppure si coglie
  l'occasione per spostarlo **sulla stessa VM Oracle** (InfluxDB OSS) → risolve anche la
  Fase B (storico lungo). Il backend legge da dove punti con le env `INFLUX_*`.

### ⚠️ Credenziali SmartThings dedicate al backend
Il backend usa una **seconda app OAuth SmartThings**, distinta da quella del Pi:
- Motivo: il refresh token **ruota** a ogni rinnovo; due client che condividono lo stesso
  token si invaliderebbero a vicenda.
- Scope richiesti: **`r:devices:*`** (lettura) **e `x:devices:*`** (esecuzione comandi).
- Flusso di consenso identico al Pi (vedi `poc/authorize.py`): si fa una volta e si copia
  `token_store_backend.json` sulla VM.

### Autenticazione app → backend
App personale: **API key statica** (header `X-API-Key`) condivisa tra app e backend, su
HTTPS. Semplice e sufficiente; in futuro si può passare a utenti/JWT.

---

## 3. Contratto API (backend)

Tutte le rotte (tranne `/health`) richiedono header `X-API-Key`.

| Metodo | Path | Descrizione |
|---|---|---|
| GET | `/health` | liveness check (no auth) |
| GET | `/devices` | elenco device con **ultima lettura** di ogni field (da InfluxDB) |
| GET | `/state` | **stato live normalizzato** di tutti i device (power/temp/setpoint/mode/fan da SmartThings) |
| GET | `/devices/{id}/state` | stato live normalizzato di un device (usato dall'app dopo i comandi) |
| GET | `/devices/{id}/status` | stato grezzo completo da SmartThings |
| GET | `/devices/{id}/history?field=temperature&range=24h&window=15m` | serie storica (grafici) |
| POST | `/devices/{id}/power` `{ "state": "on"\|"off" }` | accende/spegne |
| POST | `/devices/{id}/setpoint` `{ "celsius": 22 }` | setpoint raffrescamento |
| POST | `/devices/{id}/mode` `{ "mode": "cool" }` | modalità AC |
| POST | `/devices/{id}/fan` `{ "mode": "auto" }` | modalità ventola |
| GET | `/nightmode` | configurazione modalità notturna |
| PUT | `/nightmode` | aggiorna configurazione |
| POST | `/nightmode/run-now` | applica subito (debug/test) |

Comandi SmartThings sottostanti (capability → command):
- `switch` → `on` / `off`
- `airConditionerMode` → `setAirConditionerMode` [mode]
- `thermostatCoolingSetpoint` → `setCoolingSetpoint` [°C]
- `airConditionerFanMode` → `setFanMode` [mode]

---

## 4. Modalità notturna custom (v1)

Versione **v2 temperature-aware** (anello chiuso sulla temperatura misurata). Gira nel
backend, applicata dallo scheduler ogni minuto.

### Parametri (configurabili da app)
| Campo | Default | Significato |
|---|---|---|
| `enabled` | false | attiva/disattiva |
| `device_ids` | [] | climatizzatori coinvolti (default app: quelli "camera") |
| `start` / `end` | `23:00` / `07:00` | finestra notturna (gestisce la mezzanotte) |
| `target_temp` | 25 | temperatura comfort all'addormentamento |
| `night_offset` | 2 | quanto più caldo nel cuore della notte |
| `ramp_minutes` | 120 | durata della salita da comfort a comfort+offset |
| `pre_wake_minutes` | 45 | quanto prima della fine tornare al comfort |
| `hysteresis` | 0.5 | banda morta anti-pendolamento |
| `power_off_margin` | 1.5 | quanto sotto obiettivo spegnere (risparmio) |
| `fan_mode` / `ac_mode` | `low` / `cool` | ventola silenziosa / modalità |
| `min_setpoint` / `max_setpoint` | 16 / 30 | guardrail |

### Algoritmo (v2, per ogni tick dentro la finestra)
1. **Curva obiettivo `D`**: parte da `target_temp`, sale linearmente fino a
   `target_temp + night_offset` in `ramp_minutes`, poi resta costante; negli ultimi
   `pre_wake_minutes` torna a `target_temp`. Clamp in `[min,max]_setpoint`.
2. **Anello chiuso** sulla temperatura misurata `T`: imposta il setpoint del clima a `D`,
   modalità e ventola silenziosa; se `T ≥ D − hysteresis` accende, se
   `T ≤ D − power_off_margin` spegne (risparmio/silenzio), in mezzo mantiene lo stato.
3. Invia **solo i comandi che cambiano** (anti-spam).

---

## 5. API v2 aggiuntive

| Metodo | Path | Descrizione |
|---|---|---|
| POST | `/devices/{id}/oscillation` | oscillazione ventola |
| POST | `/devices/{id}/command` | comando SmartThings **generico** (capability/command/args) |
| GET | `/devices/{id}/capabilities` | capability del device (scoprire comandi disponibili) |
| GET | `/devices/{id}/stats?field=&days=` | riepiloghi giornalieri (min/max/media) |
| GET/POST/DELETE | `/schedules` … | pianificazioni orarie |
| GET/PUT | `/away` | modalità Away/Eco |
| GET/PUT | `/safety` | auto-spegnimento di sicurezza |
| GET | `/notifications` · GET/PUT `/notifications/config` · POST `/notifications/read` | notifiche in-app |

---

## 6. Stato implementazione (fatto / in sospeso)

### ✅ Fatto
- Backend completo e in produzione su Oracle (systemd) + accesso via Tailscale Funnel.
- Letture (storico + stato live), comandi (power/setpoint/mode/fan/oscillation/generico).
- **Night mode v2** temperature-aware (anello chiuso, isteresi, rampa, risveglio).
- **Pianificazioni** orarie generiche; **Away/Eco**; **auto-spegnimento** di sicurezza.
- **Notifiche in-app configurabili** (soglie temperatura, collector offline, filtro).
- App Android: tema chiaro/scuro dinamico, dashboard con Away, controllo completo,
  **grafici potenziati** (assi/unità, confronto stanze, riepiloghi giornalieri),
  schermate night mode/pianificazioni/sicurezza/notifiche, hub impostazioni.

### ⏳ In sospeso
- **Widget home screen** (Android): non implementato (boilerplate elevato, rinviato).
- **Blocco app con PIN/biometria**: non implementato.
- **Pull-to-refresh**: per ora c'è il pulsante "Aggiorna" (API gesture rinviata).
- **Notifiche push** (a telefono spento): ora sono in-app via polling; il push (FCM)
  richiede setup dedicato.
- **Migrazione InfluxDB su Oracle** (Fase B / opzione E): rinviata su richiesta.
- **Energia/consumi affidabili**: il dato `powerConsumptionReport` del salotto è stale
  (limite hardware), quindi niente feature energia finché non si aggiunge un misuratore.
- **Comandi "speciali" Samsung** (windFree, notte tropicale, auto-clean): coperti dal
  comando **generico**; per esporli come pulsanti dedicati va verificato con
  `/devices/{id}/capabilities` cosa accetta davvero il modello.
