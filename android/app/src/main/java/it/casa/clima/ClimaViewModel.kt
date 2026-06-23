package it.casa.clima

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.casa.clima.net.Alert
import it.casa.clima.net.AwayState
import it.casa.clima.net.DailyStat
import it.casa.clima.net.DeviceState
import it.casa.clima.net.FanRequest
import it.casa.clima.net.HistoryPoint
import it.casa.clima.net.ModeRequest
import it.casa.clima.net.Network
import it.casa.clima.net.NightModeConfig
import it.casa.clima.net.NotificationConfig
import it.casa.clima.net.OscillationRequest
import it.casa.clima.net.PiholeBlockingRequest
import it.casa.clima.net.PiholeSummary
import it.casa.clima.net.PlugState
import it.casa.clima.net.PlugSwitchRequest
import it.casa.clima.net.PowerRequest
import it.casa.clima.net.SafetyConfig
import it.casa.clima.net.Schedule
import it.casa.clima.net.SetpointRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceHistory(
    val temperature: List<HistoryPoint> = emptyList(),
    val setpoint: List<HistoryPoint> = emptyList(),
)

data class UiState(
    val loading: Boolean = false,
    val devices: List<DeviceState> = emptyList(),
    val history: Map<String, DeviceHistory> = emptyMap(),
    val stats: Map<String, List<DailyStat>> = emptyMap(),
    val nightmode: NightModeConfig? = null,
    val schedules: List<Schedule> = emptyList(),
    val away: AwayState? = null,
    val safety: SafetyConfig? = null,
    val notifConfig: NotificationConfig? = null,
    val alerts: List<Alert> = emptyList(),
    val plugs: List<PlugState> = emptyList(),
    val pihole: PiholeSummary? = null,
    val error: String? = null,
    val toast: String? = null,
) {
    val unread: Int get() = alerts.count { !it.read }
}

class ClimaViewModel : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private val api = Network.api

    private fun update(block: (UiState) -> UiState) { _state.value = block(_state.value) }

    fun refresh() = launchCatching {
        update { it.copy(loading = true, error = null) }
        val devices = api.states()
        val away = runCatching { api.getAway() }.getOrNull()
        val alerts = runCatching { api.getAlerts() }.getOrNull() ?: emptyList()
        val plugs = runCatching { api.plugs() }.getOrNull() ?: emptyList()
        update { it.copy(loading = false, devices = devices, away = away, alerts = alerts, plugs = plugs) }
    }

    fun setPlug(id: String, on: Boolean) = launchCatching {
        api.switchPlug(id, PlugSwitchRequest(on))
        delay(1200)
        val plugs = api.plugs()
        update { it.copy(plugs = plugs, toast = if (on) "Presa accesa" else "Presa spenta") }
    }

    // --- Pi-hole ---
    fun loadPihole() = launchCatching {
        val s = api.pihole()
        update { it.copy(pihole = s) }
    }
    fun setPiholeBlocking(enabled: Boolean, seconds: Int? = null) = launchCatching {
        api.piholeBlocking(PiholeBlockingRequest(enabled, seconds))
        delay(800)
        val s = api.pihole()
        update { it.copy(pihole = s, toast = if (enabled) "Blocco riattivato" else "Blocco disattivato") }
    }

    // --- storico / statistiche ---
    fun loadHistory(id: String, range: String = "24h", window: String = "15m") = launchCatching {
        val temperature = api.history(id, "temperature", range, window)
        val setpoint = api.history(id, "cooling_setpoint", range, window)
        update { it.copy(history = it.history + (id to DeviceHistory(temperature, setpoint))) }
    }

    fun loadStats(id: String, days: Int = 7) = launchCatching {
        val s = api.stats(id, "temperature", days)
        update { it.copy(stats = it.stats + (id to s)) }
    }

    // --- comandi ---
    fun setPower(id: String, on: Boolean) = command(id, if (on) "Acceso" else "Spento") { api.power(id, PowerRequest(if (on) "on" else "off")) }
    fun setSetpoint(id: String, c: Double) = command(id, "Setpoint ${c.toInt()}°C") { api.setpoint(id, SetpointRequest(c)) }
    fun setMode(id: String, m: String) = command(id, "Modalità $m") { api.mode(id, ModeRequest(m)) }
    fun setFan(id: String, m: String) = command(id, "Ventola $m") { api.fan(id, FanRequest(m)) }
    fun setOscillation(id: String, m: String) = command(id, "Oscillazione $m") { api.oscillation(id, OscillationRequest(m)) }

    // --- night mode ---
    fun loadNightmode() = launchCatching {
        val cfg = api.getNightmode()
        update { it.copy(nightmode = cfg) }
    }
    fun saveNightmode(cfg: NightModeConfig) = launchCatching {
        val saved = api.putNightmode(cfg)
        update { it.copy(nightmode = saved, toast = "Modalità notturna salvata") }
    }

    // --- pianificazioni ---
    fun loadSchedules() = launchCatching {
        val list = api.getSchedules()
        update { it.copy(schedules = list) }
    }
    fun upsertSchedule(s: Schedule) = launchCatching {
        api.upsertSchedule(s)
        val list = api.getSchedules()
        update { it.copy(schedules = list, toast = "Pianificazione salvata") }
    }
    fun deleteSchedule(id: String) = launchCatching {
        api.deleteSchedule(id)
        val list = api.getSchedules()
        update { it.copy(schedules = list) }
    }

    // --- away / sicurezza ---
    fun loadAway() = launchCatching {
        val a = api.getAway()
        update { it.copy(away = a) }
    }
    fun setAway(enabled: Boolean) = launchCatching {
        val a = api.setAway(AwayState(enabled))
        update { it.copy(away = a, toast = if (enabled) "Modalità Away attiva" else "Away disattivata") }
        delay(1200); refresh()
    }
    fun loadSafety() = launchCatching {
        val cfg = api.getSafety()
        update { it.copy(safety = cfg) }
    }
    fun saveSafety(cfg: SafetyConfig) = launchCatching {
        val saved = api.putSafety(cfg)
        update { it.copy(safety = saved, toast = "Sicurezza salvata") }
    }

    // --- notifiche ---
    fun loadNotifConfig() = launchCatching {
        val cfg = api.getNotifConfig()
        update { it.copy(notifConfig = cfg) }
    }
    fun saveNotifConfig(cfg: NotificationConfig) = launchCatching {
        val saved = api.putNotifConfig(cfg)
        update { it.copy(notifConfig = saved, toast = "Notifiche salvate") }
    }
    fun loadAlerts() = launchCatching {
        val a = api.getAlerts()
        update { it.copy(alerts = a) }
    }
    fun markAllRead() = launchCatching {
        api.markRead(null)
        val a = api.getAlerts()
        update { it.copy(alerts = a) }
    }

    fun clearToast() = update { it.copy(toast = null) }

    private fun command(id: String, toast: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                delay(1200)
                val fresh = api.state(id)
                update { s -> s.copy(devices = s.devices.map { if (it.deviceId == id) fresh else it }, toast = toast) }
            } catch (e: Exception) {
                update { it.copy(error = e.message ?: "Errore di rete") }
            }
        }
    }

    private fun launchCatching(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (e: Exception) {
                update { it.copy(loading = false, error = e.message ?: "Errore di rete") }
            }
        }
    }
}
