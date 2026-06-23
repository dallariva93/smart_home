# Proseguimento sviluppi — Dashboard sensori Samsung

> Documento operativo: stato attuale, **deploy sul Raspberry Pi** e roadmap verso lo
> sviluppo vero e proprio. Complementare a:
> - [`DOCUMENTAZIONE_SVILUPPO.md`](DOCUMENTAZIONE_SVILUPPO.md) (architettura POC)
> - [`poc/README.md`](poc/README.md) (setup passo-passo della POC)

---

## 1. Stato attuale (✅ POC validata su PC locale)

Catena funzionante end-to-end, finora **tutta eseguita su un PC**:

```
SmartThings API ──(OAuth2)──► poller.py ──► InfluxDB Cloud ──► Grafana Cloud ──► 📱
```

Completato:
- App OAuth SmartThings creata (API_ONLY) con `client_id`/`secret`, scope `r:devices:*`,
  redirect HTTPS pubblico (`https://httpbin.org/get`) per il consenso una-tantum.
- `authorize.py` eseguito sul PC → `token_store.json` generato (access + refresh token).
- `poller.py` testato: scrive il measurement `clima` su InfluxDB Cloud.
- Dashboard Grafana importata (`poc/grafana_dashboard.json`).

**Cosa manca per chiudere la fase POC:** spostare il poller dal PC al **Raspberry**, che
è il suo ruolo definitivo (vedi §2).

---

## 2. ⭐ Deploy sul Raspberry Pi 2 (da PC → a Pi)

Il Raspberry fa **solo da collector**: esegue `poller.py` periodicamente. Niente
database, niente dashboard, niente server: gira tutto in cloud.

### 2.1 Concetto chiave: NON si esegue `authorize.py` sul Pi
`authorize.py` richiede un browser (consenso OAuth una-tantum) e l'hai già fatto sul PC.
Sul Pi si **copia** il `token_store.json` già ottenuto: da lì il poller rinnova i token
da solo, senza interfaccia.

> ⚠️ **Esegui il poller in UN SOLO posto.** Se lo lasci girare sia sul PC sia sul Pi,
> entrambi proveranno a fare il refresh: poiché il refresh token **ruota** a ogni
> rinnovo, uno invaliderebbe l'altro. Dopo aver messo in produzione il Pi, **smetti di
> eseguirlo sul PC** (e disabilita eventuali cron/task lì).

### 2.2 Prerequisiti sul Pi
- Raspberry Pi OS (anche Lite va benissimo), con **SSH abilitato** e rete attiva.
- Python 3 e venv (di norma già presenti):
  ```bash
  python3 --version
  sudo apt update && sudo apt install -y python3-venv python3-pip
  ```
- **Orologio sincronizzato (NTP)**: fondamentale perché i timestamp su InfluxDB siano
  corretti. Su Raspberry Pi OS è attivo di default; verifica con:
  ```bash
  timedatectl status   # deve mostrare "System clock synchronized: yes"
  ```

### 2.3 Trasferire i file sul Pi
Dal PC (PowerShell), copia la cartella `poc` **inclusi `.env` e `token_store.json`**.
Esempio con `scp` (sostituisci IP/utente):
```powershell
scp -r "C:\Users\giovanni.dallariva\Desktop\varie ai\test_clima_samsung\poc" pi@192.168.1.50:/home/pi/clima
```
In alternativa: `git clone` del progetto e poi copia a parte **solo** `.env` e
`token_store.json` (che sono in `.gitignore` e non vanno nel repo).

> File che DEVONO arrivare sul Pi: `oauth.py`, `poller.py`, `explore.py`,
> `requirements.txt`, **`.env`**, **`token_store.json`**.
> `authorize.py` non serve sul Pi.

### 2.4 Ambiente Python sul Pi
```bash
cd /home/pi/clima/poc
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### 2.5 Permessi del token store
Il poller **riscrive** `token_store.json` a ogni refresh: deve essere scrivibile
dall'utente che esegue il cron (di solito `pi`).
```bash
chmod 600 /home/pi/clima/poc/token_store.json   # leggibile/scrivibile solo dall'utente
ls -l /home/pi/clima/poc/token_store.json        # verifica proprietario = pi
```

### 2.6 Test manuale
```bash
cd /home/pi/clima/poc
source .venv/bin/activate
python poller.py
```
Atteso: `Scritti N punti su InfluxDB`. Se OK, procedi con la schedulazione.

### 2.7 Schedulazione con cron
```bash
crontab -e
```
Aggiungi (polling ogni 2 minuti, con log):
```cron
*/2 * * * * cd /home/pi/clima/poc && /home/pi/clima/poc/.venv/bin/python poller.py >> /home/pi/clima/poc/poller.log 2>&1
```
> Usa il path assoluto del Python del venv: il cron non eredita l'ambiente attivato.

### 2.8 (Opzionale, più robusto) systemd timer
Alternativa a cron, con log centralizzati in `journalctl`.

`/etc/systemd/system/clima-poller.service`:
```ini
[Unit]
Description=Poller climatizzatori SmartThings -> InfluxDB
After=network-online.target

[Service]
Type=oneshot
User=pi
WorkingDirectory=/home/pi/clima/poc
ExecStart=/home/pi/clima/poc/.venv/bin/python poller.py
```
`/etc/systemd/system/clima-poller.timer`:
```ini
[Unit]
Description=Esegue il poller ogni 2 minuti

[Timer]
OnBootSec=2min
OnUnitActiveSec=2min
Persistent=true

[Install]
WantedBy=timers.target
```
Attivazione:
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now clima-poller.timer
systemctl list-timers | grep clima        # verifica scheduling
journalctl -u clima-poller.service -n 50  # vedi gli ultimi log
```

### 2.9 Manutenzione e accortezze
- **Log rotation**: se usi cron, evita che `poller.log` cresca all'infinito. Aggiungi
  `/home/pi/clima/poc/poller.log` a un file in `/etc/logrotate.d/` o svuotalo
  periodicamente. (Con systemd non serve: ci pensa journald.)
- **Token a 30 giorni**: il refresh token resta valido finché viene rinnovato almeno una
  volta ogni ~30 giorni. Se il Pi resta spento più a lungo, rifai il consenso sul PC
  (`authorize.py`) e ricopia `token_store.json`.
- **Riavvio Pi**: con cron riparte da solo; con il systemd timer è gestito da
  `OnBootSec`. Nessun intervento manuale.
- **Verifica salute**: controlla ogni tanto che su Grafana arrivino punti recenti; se si
  fermano, guarda `poller.log` / `journalctl`.

### 2.10 Checklist deploy
- [ ] File copiati sul Pi (compresi `.env` e `token_store.json`)
- [ ] venv creato e `requirements.txt` installato
- [ ] `python poller.py` scrive su InfluxDB
- [ ] cron **oppure** systemd timer attivo
- [ ] Poller **disattivato sul PC**
- [ ] Dati recenti visibili su Grafana da telefono/tablet

---

## 3. Roadmap verso lo sviluppo vero

> Promemoria: lo stack attuale (InfluxDB Cloud + Grafana) è **scelto per la POC** ed è in
> sola lettura. Estendendo il progetto **cambierà** (vedi DOCUMENTAZIONE_SVILUPPO §2).

### Fase A — Consolidamento collector (breve termine)
- Deploy sul Pi (questa guida, §2). ✅ obiettivo immediato.
- Robustezza poller: gestione errori/timeout già presente; valutare retry su errori
  transitori e un piccolo alert (es. Grafana alert se non arrivano dati da X minuti).

### Fase B — Storico a lungo termine (quando il free tier non basta)
Il free tier di InfluxDB Cloud ha **retention limitata** (storicamente ~30 giorni): non
adatto a uno storico pluriennale. Decisione da prendere quando serve davvero:
- **Opzione 1**: database self-hosted (es. **InfluxDB OSS** o **Postgres/TimescaleDB**) —
  ma **non sul Pi 2**: meglio su un piccolo server/NAS o VPS economico.
- **Opzione 2**: piano a pagamento minimo del servizio cloud.
- Il Pi resta collector in entrambi i casi: cambia solo la destinazione di scrittura.

### Fase C — App custom + controllo dispositivi (🚧 in sviluppo)
Decisioni prese e in corso di implementazione (dettaglio: [`FASE_C_DESIGN.md`](FASE_C_DESIGN.md)):
- **Backend**: **FastAPI** in `backend/` — letture storiche (InfluxDB), stato live e
  **comandi** SmartThings, **modalità notturna** custom con scheduler interno.
- **Hosting backend**: **Oracle Cloud Always Free** (VM ARM Ampere A1), sempre accesa,
  niente sleep, gratis. Scelto dopo aver scartato i PaaS che dormono (Render/Railway/Fly)
  e i serverless con cold start. uvicorn dietro **Caddy/Cloudflare Tunnel** per l'HTTPS.
- **Frontend**: **app Android nativa** (Kotlin + Jetpack Compose) in `android/` — scelta
  al posto della PWA. Comunica col backend in HTTPS con **API key** condivisa.
- **Credenziali SmartThings dedicate al backend** (seconda app OAuth, scope
  `r:devices:* x:devices:*`), per non condividere il refresh token (che ruota) col Pi.
- **Modalità notturna v1**: alza gradualmente il setpoint durante la notte e lo riporta
  al comfort prima del risveglio (algoritmo da rivedere insieme).

### Fase D — Altri sensori di casa
- Nuovi `measurement`/sorgenti oltre a SmartThings (es. sensori Zigbee/MQTT, altri brand).
- Il modello dati (measurement per tipo di sorgente) è già pensato per estendersi.

### Ordine consigliato
**A → (B quando lo storico stringe) → C → D.**
La Fase C è quella che abilita il controllo dei dispositivi e l'app vera, ed è il momento
in cui rifaremo le scelte tecnologiche con calma.

---

## 4. Riepilogo file del progetto

| File | Ruolo |
|------|-------|
| `GUIDA_DATI_CLIMATIZZATORE_SAMSUNG.md` | Riferimento API SmartThings e device ID. |
| `DOCUMENTAZIONE_SVILUPPO.md` | Architettura e scelte della POC. |
| `PROSEGUIMENTO_SVILUPPI.md` | (questo) Deploy su Pi + roadmap. |
| `FASE_C_DESIGN.md` | Design Fase C: backend, comandi, modalità notturna. |
| `HISTORY.md` | Storico cronologico delle modifiche al progetto. |
| `poc/README.md` | Setup passo-passo della POC. |
| `poc/authorize.py` | Consenso OAuth una-tantum (solo su PC con browser). |
| `poc/oauth.py` | Token store + refresh automatico. |
| `poc/poller.py` | Collector da eseguire sul Pi. |
| `poc/explore.py` | Ispezione capability/attributi reali. |
| `poc/grafana_dashboard.json` | Dashboard Grafana importabile. |
| `backend/` | Backend FastAPI (Fase C): letture, comandi, modalità notturna. |
| `android/` | App Android nativa (Fase C): dashboard + controllo + night mode. |
