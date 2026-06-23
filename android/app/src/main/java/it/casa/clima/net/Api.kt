package it.casa.clima.net

import it.casa.clima.Config
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ClimaApi {
    // stato
    @GET("state") suspend fun states(): List<DeviceState>
    @GET("devices/{id}/state") suspend fun state(@Path("id") id: String): DeviceState

    // storico / statistiche
    @GET("devices/{id}/history")
    suspend fun history(
        @Path("id") id: String,
        @Query("field") field: String,
        @Query("range") range: String = "24h",
        @Query("window") window: String = "15m",
    ): List<HistoryPoint>

    @GET("devices/{id}/stats")
    suspend fun stats(
        @Path("id") id: String,
        @Query("field") field: String = "temperature",
        @Query("days") days: Int = 7,
    ): List<DailyStat>

    // comandi
    @POST("devices/{id}/power") suspend fun power(@Path("id") id: String, @Body req: PowerRequest): JsonElement
    @POST("devices/{id}/setpoint") suspend fun setpoint(@Path("id") id: String, @Body req: SetpointRequest): JsonElement
    @POST("devices/{id}/mode") suspend fun mode(@Path("id") id: String, @Body req: ModeRequest): JsonElement
    @POST("devices/{id}/fan") suspend fun fan(@Path("id") id: String, @Body req: FanRequest): JsonElement
    @POST("devices/{id}/oscillation") suspend fun oscillation(@Path("id") id: String, @Body req: OscillationRequest): JsonElement

    // modalità notturna
    @GET("nightmode") suspend fun getNightmode(): NightModeConfig
    @PUT("nightmode") suspend fun putNightmode(@Body cfg: NightModeConfig): NightModeConfig

    // pianificazioni
    @GET("schedules") suspend fun getSchedules(): List<Schedule>
    @POST("schedules") suspend fun upsertSchedule(@Body s: Schedule): Schedule
    @DELETE("schedules/{id}") suspend fun deleteSchedule(@Path("id") id: String): JsonElement

    // away / sicurezza
    @GET("away") suspend fun getAway(): AwayState
    @PUT("away") suspend fun setAway(@Body body: AwayState): AwayState
    @GET("safety") suspend fun getSafety(): SafetyConfig
    @PUT("safety") suspend fun putSafety(@Body cfg: SafetyConfig): SafetyConfig

    // notifiche
    @GET("notifications") suspend fun getAlerts(): List<Alert>
    @GET("notifications/config") suspend fun getNotifConfig(): NotificationConfig
    @PUT("notifications/config") suspend fun putNotifConfig(@Body cfg: NotificationConfig): NotificationConfig
    @POST("notifications/read") suspend fun markRead(@Query("id") id: String? = null): JsonElement

    // prese smart (Tuya)
    @GET("plugs") suspend fun plugs(): List<PlugState>
    @POST("plugs/{id}/switch") suspend fun switchPlug(@Path("id") id: String, @Body req: PlugSwitchRequest): JsonElement

    // Pi-hole
    @GET("pihole/summary") suspend fun pihole(): PiholeSummary
    @POST("pihole/blocking") suspend fun piholeBlocking(@Body req: PiholeBlockingRequest): JsonElement
}

object Network {
    val api: ClimaApi by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().addHeader("X-API-Key", Config.API_KEY).build())
            }
            .build()
        Retrofit.Builder()
            .baseUrl(Config.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ClimaApi::class.java)
    }
}
