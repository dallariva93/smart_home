# App Android — Clima Casa (Fase C)

App nativa **Kotlin + Jetpack Compose** che parla con il backend FastAPI.
Funzioni: dashboard live, controllo condizionatori (on/off, setpoint, modalità, ventola,
oscillazione), grafici/statistiche, modalità notturna, pianificazioni, sicurezza,
notifiche, prese Tuya/Smart Life e pannello Pi-hole.

**Identità visiva:** tema **"scuro elettrico"** dark-first con accenti neon ciano/lime,
gauge circolare del termostato, card con bordo luminoso e grafici a gradiente — pensata
per reggere il confronto con SmartThings & co.

## Struttura
```
android/
  settings.gradle.kts, build.gradle.kts, gradle.properties
  app/
    build.gradle.kts            build (release con R8/minify, cleartext per build type)
    proguard-rules.pro          regole R8 (serialization / Retrofit / OkHttp)
    src/main/AndroidManifest.xml
    src/main/res/                icona adattiva, tema, stringhe, colori
    src/main/java/it/casa/clima/
      Config.kt                 BASE_URL + API_KEY (iniettati da local.properties)
      Theme.kt                  design system: palette neon, tipografia, color scheme
      Components.kt             componenti riutilizzabili (GlowCard, gauge, chip, stati…)
      Charts.kt                 grafici Canvas multi-serie con riempimento a gradiente
      Screens.kt               tutte le schermate ridisegnate
      MainActivity.kt           Activity edge-to-edge + NavHost
      ClimaViewModel.kt         stato + chiamate al backend
      net/Models.kt             DTO allineati al backend
      net/Api.kt                interfaccia Retrofit + header X-API-Key
```

## Configurazione (segreti fuori dal sorgente)
I valori sensibili stanno in `android/local.properties` (NON tracciato da git) e vengono
iniettati a build-time in `BuildConfig`:
```
CLIMA_API_KEY=<lo stesso APP_API_KEY del .env del backend>
CLIMA_BASE_URL=https://clima-backend.<tailnet>.ts.net/      # con "/" finale
```

## Build
1. Installa **Android Studio**. *File → Open* → cartella `android/`, sincronizza Gradle.
2. Crea/compila `android/local.properties` come sopra.
3. **Debug** (test in LAN): *Run* su telefono/emulatore. Nella build *debug* il traffico in
   chiaro è permesso, quindi puoi puntare `CLIMA_BASE_URL` a `http://192.168.1.18:8000/`.
4. **Release** (produzione): *Build → Generate Signed Bundle / APK*. La build release
   abilita **R8/minify + shrink delle risorse** e **vieta il traffico in chiaro** (il
   backend è solo HTTPS via tailnet/Caddy).

> ⚠️ **Firma APK non configurata** (per scelta): non c'è `signingConfig`. Per pubblicare/
> installare una release firmata, genera un keystore (`keytool`) e aggiungi un
> `signingConfigs { create("release") { … } }` agganciato a `buildTypes.release`. Custodisci
> il keystore fuori dal repo.

## Note
- L'app non gestisce l'OAuth di SmartThings: tutto passa dal backend, l'app conosce solo
  l'`API_KEY`.
- Modalità notturna, pianificazioni, sicurezza e notifiche girano nel **backend** (sempre
  acceso): l'app serve a configurarle e a visualizzarne lo stato.
- Tema sempre scuro per coerenza di brand; `enableEdgeToEdge()` gestisce le system bar.
- `minSdk = 26` (icona adattiva senza fallback PNG), `targetSdk = 35`, versione **2.0**.
- Possibili evoluzioni: push FCM (richiede Firebase + endpoint backend), widget home,
  blocco con PIN/biometria.
