# POC — Raccolta dati climatizzatori Samsung → InfluxDB Cloud → Grafana

Proof of Concept dello stack descritto in
[`../DOCUMENTAZIONE_SVILUPPO.md`](../DOCUMENTAZIONE_SVILUPPO.md).

Il Raspberry Pi 2 fa **solo da collector**: uno script Python interroga periodicamente
l'API SmartThings (auth **OAuth2**, headless) e scrive le metriche su **InfluxDB Cloud**;
i grafici si vedono da telefono/tablet via **Grafana Cloud**.

```
Pi (cron) → poller.py → InfluxDB Cloud → Grafana Cloud → 📱
```

## File
| File | Ruolo |
|------|-------|
| `authorize.py` | Consenso OAuth2 **una tantum** (sul PC, con browser). Salva i token. |
| `oauth.py`     | Token store su file + refresh automatico (gestisce la rotazione del refresh token). |
| `poller.py`    | Interroga i device e scrive su InfluxDB. **Da schedulare in cron sul Pi.** |
| `explore.py`   | Stampa lo stato grezzo per verificare capability/attributi reali del tuo modello. |

## Setup

### 1. Dipendenze
```bash
python -m venv .venv
# Windows:  .venv\Scripts\activate     |  Linux/Pi:  source .venv/bin/activate
pip install -r requirements.txt
```

### 2. App OAuth su SmartThings (una tantum)
Crea un'app OAuth (tipo *OAuth-In*) tramite SmartThings CLI o Developer Workspace:
```bash
smartthings apps:create
```
Annota `client_id` e `client_secret`, imposta lo scope `r:devices:*` e il redirect URI
`http://localhost:8080/callback` (deve combaciare con `ST_REDIRECT_URI`).

### 3. Configurazione
```bash
cp .env.example .env   # poi compila i valori
```
`.env` e `token_store.json` sono in `.gitignore`: non vanno mai committati.

### 4. Autorizzazione iniziale (sul tuo PC, una volta sola)
```bash
python authorize.py
```
Si apre il browser → login Samsung → consenso → i token vengono salvati in
`token_store.json`. Questo passaggio richiede un browser **ma nessun frontend**.

### 5. Verifica capability (consigliato)
```bash
python explore.py
```
Controlla i nomi reali di capability/attributi (specie i `custom.*` dei filtri) e, se
servono aggiustamenti, modifica le mappe `NUMERIC_FIELDS` / `STRING_FIELDS` in `poller.py`.

### 6. Test del poller
```bash
python poller.py
```
Dovresti vedere "Scritti N punti su InfluxDB".

## Esecuzione periodica sul Pi (cron)
```cron
*/2 * * * * cd /home/pi/clima/poc && /home/pi/clima/poc/.venv/bin/python poller.py >> poller.log 2>&1
```
Da quel momento i token si rinnovano da soli: nessun intervento manuale finché il Pi
resta acceso almeno una volta ogni ~30 giorni (validità del refresh token).

## Grafana Cloud
1. Crea un account Grafana Cloud (free).
2. Aggiungi un datasource **InfluxDB** (Flux) verso il bucket `casa_sensori`.
3. Crea i pannelli (temperatura, umidità, qualità aria, `power_w`, ecc.) e apri la
   dashboard da telefono/tablet.

## Note
- **PAT vs OAuth2:** qui si usa OAuth2 perché il PAT scade in 24h (vedi documentazione).
- **Refresh token:** ruota a ogni rinnovo → `poller.py` risalva sempre `token_store.json`.
- **Limiti free tier:** la retention di InfluxDB Cloud Free è limitata; per lo storico
  a lungo termine è prevista una futura migrazione (vedi documentazione, §9).
