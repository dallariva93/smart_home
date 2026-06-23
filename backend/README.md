# Backend Clima Casa (Fase C)

API FastAPI: letture storiche (InfluxDB) + stato live e **comandi** (SmartThings) +
**modalità notturna** custom. Design: [`../FASE_C_DESIGN.md`](../FASE_C_DESIGN.md).

## Struttura
```
backend/
  app/
    config.py        impostazioni da .env
    smartthings.py   client OAuth async (refresh + comandi + stato)
    influx.py        letture storico / ultime letture
    nightmode.py     config + algoritmo + tick scheduler
    models.py        schemi request/response
    security.py      API key (header X-API-Key)
    main.py          app FastAPI + rotte + scheduler
  authorize_backend.py  consenso OAuth una-tantum (sul PC)
  deploy/               unit systemd + Caddyfile
  requirements.txt  .env.example  .gitignore
```

## 1. App OAuth SmartThings dedicata al backend
Crea una **seconda** app OAuth (separata da quella del Pi), con scope
**`r:devices:* x:devices:*`** (la `x` abilita i comandi) e redirect `https://httpbin.org/get`:
```bash
smartthings apps:create        # tipo OAuth-In SmartApp
```
Annota `client_id` / `client_secret`.

## 2. Configurazione e consenso (sul PC)
```bash
cd backend
python -m venv .venv && source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env        # compila ST_*, INFLUX_* (token sola lettura), APP_API_KEY
python authorize_backend.py # login + incolla l'URL di redirect -> crea token_store_backend.json
```

## 3. Test locale
```bash
uvicorn app.main:app --reload --port 8000
# in un altro terminale:
curl -H "X-API-Key: <APP_API_KEY>" http://localhost:8000/devices
```

## 4. Deploy su Oracle Cloud Always Free
1. **Crea la VM**: istanza *Always Free*, preferibilmente **Ampere ARM (A1)**, immagine
   Ubuntu. Annota l'IP pubblico.
2. **Apri la porta** (solo se esponi direttamente; meglio usare Caddy, vedi sotto):
   Oracle ha **due** livelli di firewall →
   - Console: *Security List / NSG* della subnet → consenti la porta;
   - Sull'istanza Ubuntu il firewall è attivo di default:
     `sudo iptables -I INPUT -p tcp --dport 443 -j ACCEPT` (e salva), oppure usa `ufw`.
3. **Copia il backend** sulla VM (esclusi `.venv`, `__pycache__`):
   ```bash
   scp -r backend ubuntu@<IP>:/home/ubuntu/clima
   scp backend/.env backend/token_store_backend.json ubuntu@<IP>:/home/ubuntu/clima/backend/
   ```
4. **Ambiente** sulla VM:
   ```bash
   cd /home/ubuntu/clima/backend
   python3 -m venv .venv && source .venv/bin/activate
   pip install -r requirements.txt
   ```
5. **Servizio systemd** (sempre attivo, riavvio automatico):
   ```bash
   sudo cp deploy/clima-backend.service /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable --now clima-backend
   journalctl -u clima-backend -f
   ```
6. **HTTPS** (necessario per l'app): l'app deve parlare in HTTPS. Due strade gratis:
   - **Caddy** (consigliato) con un dominio che punta all'IP: `deploy/Caddyfile`,
     HTTPS automatico via Let's Encrypt. uvicorn resta su `127.0.0.1:8000`.
   - **Cloudflare Tunnel**: nessuna porta aperta, espone `127.0.0.1:8000` su un URL
     HTTPS Cloudflare (gratis, non serve dominio proprio).

> Il servizio gira su `127.0.0.1:8000` (vedi unit): l'accesso pubblico passa **solo** da
> Caddy/Cloudflare con TLS. Non esporre uvicorn in chiaro su Internet.

## Deploy rapido (script)
Dopo il primo setup, usa lo script invece dei comandi a mano. Apri `deploy.ps1`,
compila in cima `IP_PUBBLICO_VM` (una volta), poi dalla cartella `backend`:
```powershell
.\deploy.ps1          # codice + restart (impacchetta solo app/, no .venv/segreti)
.\deploy.ps1 -Deps    # anche pip install (quando cambia requirements.txt)
.\deploy.ps1 -Env     # anche invio del .env
```
Lo script impacchetta solo il codice, lo estrae sulla VM, riavvia il servizio e verifica
che sia `active`. I segreti e i file di stato sulla VM non vengono toccati.
> Se PowerShell blocca lo script: `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`.

## Prese Tuya / Smart Life (opzionale)
Per vedere e comandare le prese Smart Life dall'app:
1. Crea un account su **iot.tuya.com** (Tuya IoT Platform).
2. **Cloud → Development → Create Cloud Project**: metodo *Smart Home*, **data center
   "Central Europe"** (deve combaciare con la region del tuo account Smart Life). Annota
   **Access ID** e **Access Secret**.
3. **Link dell'account Smart Life**: nel progetto → *Devices → Link App Account → Add App
   Account* → con l'app Smart Life (Io → icona scansione in alto a destra) inquadra il QR.
   Da quel momento le tue prese sono visibili al progetto.
4. Verifica che il servizio **IoT Core** sia attivo (trial: rinnovabile gratis).
5. Compila nel `.env`:
   ```
   TUYA_ENDPOINT=https://openapi.tuyaeu.com
   TUYA_ACCESS_ID=<access id>
   TUYA_ACCESS_SECRET=<access secret>
   ```
6. Reinstalla le dipendenze (`pip install -r requirements.txt`, aggiunge `tuya-connector-python`)
   e riavvia il servizio. Test: `curl -H "X-API-Key: ..." http://127.0.0.1:8000/plugs`.

> Se i campi `TUYA_*` restano vuoti, l'integrazione è disattivata e `/plugs` ritorna `[]`
> (l'app semplicemente non mostra prese). La scala dei consumi (`power_w`) può variare per
> modello: si tara dopo il primo test reale.

## Note
- Il token SmartThings del backend si rinnova da solo; se la VM resta spenta > 30 giorni
  rifai `authorize_backend.py` e ricopia il file token.
- `APP_API_KEY` è il segreto condiviso con l'app Android: tienilo lungo e privato.
- La modalità notturna gira nello scheduler interno (ogni 10 min); test immediato con
  `POST /nightmode/run-now`.
