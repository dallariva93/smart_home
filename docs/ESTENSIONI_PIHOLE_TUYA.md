# Estensioni: Pi-hole e Smart Life (Tuya)

Analisi e stato delle due estensioni richieste. Idea di fondo: il **backend diventa un
hub multi-provider**; l'app parla solo col backend, ogni nuovo servizio è un modulo.

---

## 1. Smart Life / Tuya (prese smart) — ✅ implementato (serve setup credenziali)

**Decisione**: integrazione via **Tuya Cloud API ufficiale** dal backend (non controllo
locale), perché funziona da remoto come SmartThings e si incastra con l'architettura.

- Backend: modulo [`backend/app/tuya.py`](../backend/app/tuya.py) (libreria ufficiale
  `tuya-connector-python`), endpoint `GET /plugs` e `POST /plugs/{id}/switch`.
- App: sezione **"Prese"** nella dashboard con nome, potenza (W) e interruttore.
- Prese previste: Power Bear AWP07L, Bakibo TP22Y (entrambe con misura consumi).

**Da fare (utente)**: creare progetto su iot.tuya.com, **linkare l'account Smart Life**,
mettere `TUYA_ACCESS_ID/SECRET` nel `.env` del backend (vedi backend/README.md).

**In sospeso / da tarare**: scala reale di `power_w` per modello; eventuale storicizzazione
dei consumi prese su InfluxDB; energia cumulata (kWh).

---

## 2. Console Pi-hole — ✅ implementato (B2) (serve Pi su tailnet + password)

**Realizzato**: schermata nativa nell'app su **API Pi-hole v6**, tramite il backend che
raggiunge il Pi **sul tailnet**.
- Backend: [`backend/app/pihole.py`](../backend/app/pihole.py) (auth v6 → SID, summary,
  blocking), endpoint `GET /pihole/summary` e `POST /pihole/blocking`. Config
  `PIHOLE_URL`/`PIHOLE_PASSWORD` (se vuote → endpoint 503, l'app mostra "non configurato").
- App: schermata **Pi-hole** (Impostazioni → Pi-hole): query oggi, bloccate (%), domini
  unici, domini in blocklist, stato blocco e **pausa 5/30 min / riattiva**.

**Da fare (utente)**:
1. Installare **Tailscale sul Raspberry** (192.168.1.18) e unirlo al tailnet
   (`curl -fsSL https://tailscale.com/install.sh | sh` poi `sudo tailscale up`).
2. Recuperare la **password** dell'admin Pi-hole (`pihole setpassword` se serve).
3. Nel `.env` del backend: `PIHOLE_URL=http://<ip-tailscale-del-pi>` e `PIHOLE_PASSWORD=…`,
   poi redeploy + restart.

**Note**: i nomi esatti dei campi dell'API v6 (`/api/stats/summary`, `/api/dns/blocking`)
e il meccanismo di sessione (`X-FTL-SID`) sono gestiti in modo difensivo; eventuali
differenze della tua versione v6 si tarano dopo il primo test reale.

### (storico) approccio precedente — pianificato

**Vincolo**: la console sta su un IP di LAN → una WebView all'IP funziona solo a casa.
L'utente la vuole **anche da fuori**, quindi serve far passare l'accesso dal backend.

**Approccio scelto (remoto)**: il **Raspberry entra in Tailscale** (solo il Pi, non il
telefono), e il **backend** (già sul tailnet/Funnel) fa da ponte. Due varianti:
- **B1 — reverse-proxy della console** sotto il backend e WebView nell'app (più rapido,
  ma il proxy dell'intera UI admin ha attriti di path/cookie).
- **B2 — schermata nativa sulle API di Pi-hole v6** (consigliata): il backend chiama
  l'API v6 (autenticata con app-password → sessione/token) e l'app mostra una schermata
  propria: query del giorno, % bloccate, top domini, e **toggle disattiva blocco N min**.
  Più "app-like" e sicura.

**Sicurezza**: mai esporre la console admin direttamente su Internet.

**Prerequisiti da fare**: installare Tailscale sul Pi 2 e unirlo al tailnet; recuperare
l'**app-password** dell'API Pi-hole v6.

**Stato**: non ancora implementato; prossimo blocco dopo la verifica di Tuya.
