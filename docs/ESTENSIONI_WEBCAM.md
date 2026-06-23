# Estensione (parcheggiata): webcam Wansview

> **Stato: NON in lavorazione.** Analisi conservata per il futuro, su richiesta.
> Decisione attuale: lasciata da parte.

## Contesto
Webcam **Wansview** (WiFi IP-cam). Obiettivo eventuale: vederle (ed eventualmente
muoverle/registrarle) dall'app, anche da remoto.

## Come si integrano
- **Cloud Wansview = proprietario, senza API pubblica** → niente via cloud ufficiale
  (a differenza di Tuya/SmartThings).
- Si passa dagli standard locali:
  - **RTSP**: stream locale `rtsp://utente:pass@<ip>:554/…` (chiave per il video).
  - **ONVIF**: standard IP-cam (URL RTSP, PTZ se la camera si muove).
- ⚠️ **Prerequisito**: verificare che il modello supporti RTSP/ONVIF (dipende da
  modello/firmware). Test rapido: aprire l'URL RTSP con **VLC** in LAN.

## Vincolo chiave: remoto
RTSP è **solo LAN** e **non** si proxa bene col backend Oracle (è video continuo, non
HTTP). Due tier:

- **Tier A — solo a casa**: l'app riproduce l'RTSP direttamente (LibVLC robusto ma
  dipendenza pesante; oppure Media3/ExoPlayer RTSP più leggero). Poco lavoro, niente
  remoto.
- **Tier B — anche da remoto** (consigliato): un **gateway** (`go2rtc`/MediaMTX) su un
  dispositivo di casa prende l'RTSP e lo ri-pubblica in **WebRTC/HLS** (passthrough,
  leggero); lo si espone via **Tailscale/Funnel** come il resto; l'app mostra lo stream
  (WebView su WebRTC, o Media3 su HLS).
  - Gateway **non** su Oracle (relay video da cloud = no). Su dispositivo di casa: il
    **Pi 2** regge 1–2 stream in passthrough per uso occasionale; per più camere o
    fluidità meglio Pi 4 / mini-PC.

## Cosa servirebbe (Tier B)
1. Confermare RTSP/ONVIF + credenziali/URL del modello.
2. `go2rtc` su un dispositivo di casa con gli stream configurati.
3. Esporre go2rtc via tailnet/Funnel.
4. App: schermata "Telecamere" (WebView WebRTC o player HLS).
5. Opzionali fuori scope: **PTZ** (ONVIF), **registrazione/motion** (es. Frigate, richiede
   hardware dedicato).

## Sicurezza
Mai esporre RTSP/camera direttamente su Internet; sempre dietro tailnet + gateway;
credenziali RTSP forti.

## Domande aperte (da riprendere quando si vorrà fare)
1. Modello esatto Wansview (per RTSP/ONVIF)?
2. Solo a casa o anche da fuori?
3. Solo live, o anche PTZ / registrazione?
