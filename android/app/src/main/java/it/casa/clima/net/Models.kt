package it.casa.clima.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Stato live del climatizzatore. */
@Serializable
data class DeviceState(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("power_state") val powerState: String? = null,
    val temperature: Double? = null,
    @SerialName("cooling_setpoint") val coolingSetpoint: Double? = null,
    @SerialName("ac_mode") val acMode: String? = null,
    @SerialName("fan_mode") val fanMode: String? = null,
    @SerialName("oscillation_mode") val oscillationMode: String? = null,
    @SerialName("dust_filter_status") val dustFilterStatus: String? = null,
    val error: String? = null,
) {
    val isOn: Boolean get() = powerState == "on"
}

@Serializable
data class HistoryPoint(val time: String, val value: Double? = null)

@Serializable
data class DailyStat(
    val day: String,
    val min: Double? = null,
    val max: Double? = null,
    val mean: Double? = null,
)

// comandi
@Serializable data class PowerRequest(val state: String)
@Serializable data class SetpointRequest(val celsius: Double)
@Serializable data class ModeRequest(val mode: String)
@Serializable data class FanRequest(val mode: String)
@Serializable data class OscillationRequest(val mode: String)

// modalità notturna v2
@Serializable
data class NightModeConfig(
    val enabled: Boolean = false,
    @SerialName("device_ids") val deviceIds: List<String> = emptyList(),
    val start: String = "23:00",
    val end: String = "07:00",
    @SerialName("target_temp") val targetTemp: Double = 25.0,
    @SerialName("night_offset") val nightOffset: Double = 2.0,
    @SerialName("ramp_minutes") val rampMinutes: Int = 120,
    @SerialName("pre_wake_minutes") val preWakeMinutes: Int = 45,
    val hysteresis: Double = 0.5,
    @SerialName("power_off_margin") val powerOffMargin: Double = 1.5,
    @SerialName("fan_mode") val fanMode: String = "low",
    @SerialName("ac_mode") val acMode: String = "cool",
    @SerialName("min_setpoint") val minSetpoint: Double = 16.0,
    @SerialName("max_setpoint") val maxSetpoint: Double = 30.0,
)

// pianificazioni
@Serializable
data class ScheduleAction(val type: String, val value: String? = null)

@Serializable
data class Schedule(
    val id: String? = null,
    val enabled: Boolean = true,
    val time: String = "22:00",
    val days: List<Int> = emptyList(),
    val targets: List<String> = emptyList(),
    val action: ScheduleAction,
    val label: String = "",
)

// away / sicurezza / notifiche
@Serializable
data class AwayState(val enabled: Boolean = false)

@Serializable
data class SafetyConfig(
    val enabled: Boolean = false,
    @SerialName("max_runtime_hours") val maxRuntimeHours: Double = 8.0,
)

@Serializable
data class NotificationConfig(
    @SerialName("temp_high_enabled") val tempHighEnabled: Boolean = false,
    @SerialName("temp_high_threshold") val tempHighThreshold: Double = 30.0,
    @SerialName("temp_low_enabled") val tempLowEnabled: Boolean = false,
    @SerialName("temp_low_threshold") val tempLowThreshold: Double = 10.0,
    @SerialName("offline_enabled") val offlineEnabled: Boolean = true,
    @SerialName("offline_minutes") val offlineMinutes: Int = 15,
    @SerialName("filter_enabled") val filterEnabled: Boolean = true,
)

@Serializable
data class Alert(
    val id: String,
    val time: String,
    val type: String,
    val message: String,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
    val read: Boolean = false,
)

// prese smart (Tuya / Smart Life)
@Serializable
data class PlugState(
    val id: String,
    val name: String? = null,
    val online: Boolean? = null,
    val on: Boolean? = null,
    @SerialName("switch_code") val switchCode: String? = null,
    @SerialName("power_w") val powerW: Double? = null,
    val voltage: Double? = null,
)

@Serializable
data class PlugSwitchRequest(val on: Boolean)

// Pi-hole
@Serializable
data class PiholeSummary(
    val total: Long? = null,
    val blocked: Long? = null,
    @SerialName("percent_blocked") val percentBlocked: Double? = null,
    @SerialName("unique_domains") val uniqueDomains: Long? = null,
    @SerialName("domains_blocked") val domainsBlocked: Long? = null,
    val blocking: String? = null,
    val timer: Double? = null,
)

@Serializable
data class PiholeBlockingRequest(val enabled: Boolean, val seconds: Int? = null)
