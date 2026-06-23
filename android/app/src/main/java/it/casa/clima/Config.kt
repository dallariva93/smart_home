package it.casa.clima

/**
 * Configurazione dell'app. I valori sono iniettati a build time da `local.properties`
 * (NON tracciato da git) tramite BuildConfig — così nessuna chiave finisce nel sorgente.
 *
 * In `android/local.properties` aggiungi:
 *   CLIMA_API_KEY=<la tua APP_API_KEY>
 *   CLIMA_BASE_URL=https://clima-backend.<tailnet>.ts.net/      # con "/" finale
 */
object Config {
    val BASE_URL: String = BuildConfig.CLIMA_BASE_URL
    val API_KEY: String = BuildConfig.CLIMA_API_KEY
}
