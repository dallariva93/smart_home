# App Android — Clima Casa (Fase C)

App nativa **Kotlin + Jetpack Compose** che parla con il backend FastAPI.
Funzioni: dashboard letture, controllo condizionatori (on/off, setpoint, modalità,
ventola), configurazione modalità notturna.

> ⚠️ Questo è uno **scaffold funzionante ma da rifinire**: contiene il codice sorgente
> e la configurazione Gradle, ma va aperto in **Android Studio** che genera il Gradle
> wrapper, scarica le dipendenze e costruisce l'APK.

## Struttura
```
android/
  settings.gradle.kts, build.gradle.kts, gradle.properties
  app/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/java/it/casa/clima/
      Config.kt           <-- DA MODIFICARE: BASE_URL + API_KEY
      ClimaViewModel.kt   stato + chiamate al backend
      MainActivity.kt     UI Compose (dashboard, controllo, modalità notturna)
      net/Models.kt       DTO (allineati alle risposte del backend)
      net/Api.kt          interfaccia Retrofit + client con header X-API-Key
```

## Come aprirlo e costruire l'APK (gratis)
1. Installa **Android Studio** (gratuito).
2. *File → Open* → seleziona la cartella `android/`. Lascia che sincronizzi Gradle
   (scarica AGP/Kotlin/dipendenze; serve connessione).
3. Apri `app/src/main/java/it/casa/clima/Config.kt` e imposta:
   - `BASE_URL` = URL del backend con `/` finale (es. `https://clima.tuodominio.it/`
     in produzione, oppure `http://192.168.1.18:8000/` per test in LAN);
   - `API_KEY` = lo stesso `APP_API_KEY` del `.env` del backend.
4. Collega il telefono (debug USB) o un emulatore e premi **Run**, oppure
   *Build → Build APK(s)* per ottenere l'APK da installare manualmente (sideload).

## Note
- Per il test in LAN su `http://` è abilitato `usesCleartextTraffic`; in produzione il
  backend è in HTTPS e puoi rimuoverlo dal manifest.
- L'app non gestisce l'OAuth di SmartThings: tutto passa dal backend, l'app conosce solo
  l'`API_KEY`.
- La modalità notturna viene eseguita dal backend (sempre acceso), non dall'app: l'app
  serve solo a configurarla.
- Estensioni naturali non ancora incluse: grafici storici (endpoint `/history` già
  pronto lato backend e lato `ClimaApi`), pull-to-refresh, tema scuro.
