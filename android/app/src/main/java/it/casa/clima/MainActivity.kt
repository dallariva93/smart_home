package it.casa.clima

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import it.casa.clima.net.Alert
import it.casa.clima.net.HistoryPoint
import it.casa.clima.net.NightModeConfig
import it.casa.clima.net.NotificationConfig
import it.casa.clima.net.PiholeSummary
import it.casa.clima.net.PlugState
import it.casa.clima.net.SafetyConfig
import it.casa.clima.net.Schedule
import it.casa.clima.net.ScheduleAction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ClimaTheme { ClimaApp() } }
    }
}

@Composable
fun ClimaApp(vm: ClimaViewModel = viewModel()) {
    val nav = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(state.toast) {
        state.toast?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); vm.clearToast() }
    }

    NavHost(navController = nav, startDestination = "dashboard") {
        composable("dashboard") { DashboardScreen(state, vm, nav) }
        composable("control/{id}") { ControlScreen(it.arguments?.getString("id"), state, vm, nav) }
        composable("chart/{id}") { DeviceChartScreen(it.arguments?.getString("id"), state, vm, nav) }
        composable("settings") { SettingsHub(nav) }
        composable("nightmode") { NightModeScreen(state, vm, nav) }
        composable("schedules") { SchedulesScreen(state, vm, nav) }
        composable("safety") { SafetyScreen(state, vm, nav) }
        composable("notifconfig") { NotifConfigScreen(state, vm, nav) }
        composable("notifications") { NotificationsScreen(state, vm, nav) }
        composable("pihole") { PiholeScreen(state, vm, nav) }
    }
}

// ---------------- Dashboard ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(state: UiState, vm: ClimaViewModel, nav: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clima Casa") },
                actions = {
                    IconButton(onClick = { nav.navigate("notifications") }) {
                        BadgedBox(badge = { if (state.unread > 0) Badge { Text("${state.unread}") } }) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Notifiche")
                        }
                    }
                    IconButton(onClick = { nav.navigate("settings") }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Impostazioni")
                    }
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Aggiorna")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let {
                Text("Errore: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    val away = state.away?.enabled == true
                    Card(colors = CardDefaults.cardColors(
                        containerColor = if (away) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Modalità Away / Eco", fontWeight = FontWeight.Bold)
                                Text(
                                    if (away) "Tutto spento, automazioni notturne in pausa"
                                    else "Spegne tutto con un tocco",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(checked = away, onCheckedChange = { vm.setAway(it) })
                        }
                    }
                }
                items(state.devices) { dev -> DeviceCard(dev) { nav.navigate("control/${dev.deviceId}") } }
                if (state.plugs.isNotEmpty()) {
                    item { Text("Prese", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                    items(state.plugs) { plug -> PlugCard(plug) { on -> vm.setPlug(plug.id, on) } }
                }
            }
        }
    }
}

@Composable
fun PlugCard(plug: PlugState, onToggle: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(plug.name ?: plug.id, fontWeight = FontWeight.Bold)
                val info = buildString {
                    plug.powerW?.let { append("${it} W") }
                    if (plug.online == false) { if (isNotEmpty()) append(" · "); append("offline") }
                }
                if (info.isNotEmpty()) Text(info, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = plug.on == true, onCheckedChange = onToggle)
        }
    }
}

@Composable
fun DeviceCard(dev: it.casa.clima.net.DeviceState, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dev.deviceName ?: dev.deviceId,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = onClick,
                    label = { Text(if (dev.isOn) "ON" else "OFF") }
                )
            }
            Spacer(Modifier.height(8.dp))
            dev.temperature?.let { Text("🌡  Temperatura: $it °C", style = MaterialTheme.typography.bodyLarge) }
            dev.coolingSetpoint?.let { Text("🎯  Setpoint: ${it.toInt()} °C") }
            dev.acMode?.let { Text("❄  Modalità: $it") }
        }
    }
}

// ---------------- Controllo dispositivo ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(id: String?, state: UiState, vm: ClimaViewModel, nav: NavHostController) {
    val device = state.devices.firstOrNull { it.deviceId == id }
    if (device == null) { Text("Dispositivo non trovato", Modifier.padding(16.dp)); return }
    var setpoint by remember(device.coolingSetpoint) { mutableStateOf(device.coolingSetpoint ?: 24.0) }

    BackTopBar(device.deviceName ?: "Controllo", nav) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Accensione", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = device.isOn, onCheckedChange = { vm.setPower(device.deviceId, it) })
            }
            HorizontalDivider()
            Text("Setpoint: ${setpoint.roundToInt()} °C", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = setpoint.toFloat(),
                onValueChange = { setpoint = it.toDouble() },
                onValueChangeFinished = { vm.setSetpoint(device.deviceId, setpoint.roundToInt().toDouble()) },
                valueRange = 16f..30f, steps = 13
            )
            HorizontalDivider()
            Text("Modalità", style = MaterialTheme.typography.titleMedium)
            ChipRow(listOf("cool", "heat", "dry", "wind", "auto"), device.acMode) { vm.setMode(device.deviceId, it) }
            HorizontalDivider()
            Text("Ventola", style = MaterialTheme.typography.titleMedium)
            ChipRow(listOf("auto", "low", "medium", "high", "turbo"), device.fanMode) { vm.setFan(device.deviceId, it) }
            HorizontalDivider()
            Text("Oscillazione", style = MaterialTheme.typography.titleMedium)
            ChipRow(listOf("fixed", "all", "vertical", "horizontal"), device.oscillationMode) { vm.setOscillation(device.deviceId, it) }
            HorizontalDivider()
            OutlinedButton(onClick = { nav.navigate("chart/${device.deviceId}") }, modifier = Modifier.fillMaxWidth()) {
                Text("📈 Grafici e statistiche")
            }
        }
    }
}

// ---------------- Grafici ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceChartScreen(id: String?, state: UiState, vm: ClimaViewModel, nav: NavHostController) {
    val device = state.devices.firstOrNull { it.deviceId == id }
    var range by remember { mutableStateOf("24h") }
    var compare by remember { mutableStateOf(false) }
    val days = if (range == "7d") 7 else 2
    LaunchedEffect(range) {
        state.devices.forEach { vm.loadHistory(it.deviceId, range, if (range == "7d") "2h" else if (range == "48h") "30m" else "15m") }
        id?.let { vm.loadStats(it, days) }
    }
    val hist = state.history[id]
    val temps = (hist?.temperature ?: emptyList()).mapNotNull { it.value }
    val palette = listOf(
        MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.error
    )

    BackTopBar("Grafici · ${device?.deviceName ?: ""}", nav) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("24h" to "24h", "48h" to "48h", "7g" to "7d").forEach { (label, value) ->
                    FilterChip(selected = range == value, onClick = { range = value }, label = { Text(label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Confronto stanze (temperatura)", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = compare, onCheckedChange = { compare = it })
            }

            if (compare) {
                Column { state.devices.forEachIndexed { i, d -> LegendDot(palette[i % palette.size], d.deviceName ?: d.deviceId) } }
                val series = state.devices.mapIndexed { i, d ->
                    Series(d.deviceName ?: d.deviceId, palette[i % palette.size], false,
                        state.history[d.deviceId]?.temperature ?: emptyList())
                }
                MultiSeriesChart(series, Modifier.fillMaxWidth().height(280.dp))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(MaterialTheme.colorScheme.primary, "Temperatura (°C)")
                    LegendDot(MaterialTheme.colorScheme.tertiary, "Setpoint (°C)")
                }
                MultiSeriesChart(
                    listOf(
                        Series("Temperatura", MaterialTheme.colorScheme.primary, false, hist?.temperature ?: emptyList()),
                        Series("Setpoint", MaterialTheme.colorScheme.tertiary, true, hist?.setpoint ?: emptyList()),
                    ),
                    Modifier.fillMaxWidth().height(280.dp)
                )
                if (temps.isNotEmpty()) {
                    Text(
                        "Temperatura — min ${temps.min()}°C · max ${temps.max()}°C · media ${"%.1f".format(temps.average())}°C",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            HorizontalDivider()
            Text("Riepilogo giornaliero (temperatura)", fontWeight = FontWeight.Bold)
            (state.stats[id] ?: emptyList()).forEach { d ->
                Text(
                    "${d.day}:  min ${d.min?.let { "%.1f".format(it) } ?: "-"}  ·  " +
                        "max ${d.max?.let { "%.1f".format(it) } ?: "-"}  ·  media ${d.mean?.let { "%.1f".format(it) } ?: "-"} °C",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text("Asse X = orario, asse Y = gradi (°C).", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------- Impostazioni (hub) ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHub(nav: NavHostController) {
    BackTopBar("Impostazioni", nav) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            HubItem("Modalità notturna", "Curva temperatura notturna automatica") { nav.navigate("nightmode") }
            HorizontalDivider()
            HubItem("Pianificazioni", "Accensioni/spegnimenti a orario") { nav.navigate("schedules") }
            HorizontalDivider()
            HubItem("Sicurezza", "Auto-spegnimento dopo N ore") { nav.navigate("safety") }
            HorizontalDivider()
            HubItem("Notifiche", "Soglie e avvisi") { nav.navigate("notifconfig") }
            HorizontalDivider()
            HubItem("Pi-hole", "Statistiche e blocco pubblicità") { nav.navigate("pihole") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubItem(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

// ---------------- Modalità notturna v2 ----------------
@Composable
fun NightModeScreen(state: UiState, vm: ClimaViewModel, nav: NavHostController) {
    LaunchedEffect(Unit) { vm.loadNightmode() }
    val cfg = state.nightmode ?: NightModeConfig()
    val devices = state.devices

    var enabled by remember(cfg) { mutableStateOf(cfg.enabled) }
    var start by remember(cfg) { mutableStateOf(cfg.start) }
    var end by remember(cfg) { mutableStateOf(cfg.end) }
    var target by remember(cfg) { mutableStateOf(cfg.targetTemp.toString()) }
    var offset by remember(cfg) { mutableStateOf(cfg.nightOffset.toString()) }
    var ramp by remember(cfg) { mutableStateOf(cfg.rampMinutes.toString()) }
    var preWake by remember(cfg) { mutableStateOf(cfg.preWakeMinutes.toString()) }
    var selected by remember(cfg, devices) {
        val init = if (cfg.deviceIds.isNotEmpty()) cfg.deviceIds.toSet()
        else devices.filter { it.deviceName?.contains("camera", true) == true }.map { it.deviceId }.toSet()
        mutableStateOf(init)
    }

    BackTopBar("Modalità notturna", nav) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Come funziona", fontWeight = FontWeight.Bold)
                    Text(
                        "Punta a una temperatura comfort all'addormentamento, la lascia salire " +
                            "dolcemente di alcuni gradi nel cuore della notte (sonno profondo, meno " +
                            "consumo) e la riporta al comfort prima del risveglio. Regola in base alla " +
                            "temperatura misurata della stanza, con isteresi per non far pendolare il " +
                            "compressore e spegnendo quando la stanza è già fresca. Si applica solo ai " +
                            "climatizzatori selezionati.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Attiva", Modifier.weight(1f)); Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Text("Climatizzatori", fontWeight = FontWeight.Bold)
            devices.forEach { dev ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = dev.deviceId in selected, onCheckedChange = { c ->
                        selected = if (c) selected + dev.deviceId else selected - dev.deviceId
                    })
                    Text(dev.deviceName ?: dev.deviceId)
                }
            }
            NumField(start, { start = it }, "Inizio (HH:MM)")
            NumField(end, { end = it }, "Fine (HH:MM)")
            NumField(target, { target = it }, "Temperatura comfort °C")
            NumField(offset, { offset = it }, "Aumento notturno °C")
            NumField(ramp, { ramp = it }, "Durata salita (min)")
            NumField(preWake, { preWake = it }, "Ritorno comfort prima del risveglio (min)")
            Button(
                onClick = {
                    vm.saveNightmode(cfg.copy(
                        enabled = enabled, deviceIds = selected.toList(), start = start, end = end,
                        targetTemp = target.toDoubleOrNull() ?: cfg.targetTemp,
                        nightOffset = offset.toDoubleOrNull() ?: cfg.nightOffset,
                        rampMinutes = ramp.toIntOrNull() ?: cfg.rampMinutes,
                        preWakeMinutes = preWake.toIntOrNull() ?: cfg.preWakeMinutes,
                    ))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Salva") }
        }
    }
}

// ---------------- Pianificazioni ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(state: UiState, vm: ClimaViewModel, nav: NavHostController) {
    LaunchedEffect(Unit) { vm.loadSchedules() }
    val devices = state.devices
    var time by remember { mutableStateOf("22:00") }
    var actionType by remember { mutableStateOf("power_on") }
    var value by remember { mutableStateOf("24") }
    var targets by remember(devices) { mutableStateOf(devices.map { it.deviceId }.toSet()) }

    val actionLabels = listOf(
        "power_on" to "Accendi", "power_off" to "Spegni", "setpoint" to "Setpoint",
        "mode" to "Modalità", "fan" to "Ventola", "away_on" to "Away ON", "away_off" to "Away OFF"
    )

    BackTopBar("Pianificazioni", nav) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Pianificazioni attive", fontWeight = FontWeight.Bold)
            if (state.schedules.isEmpty()) Text("Nessuna pianificazione.", style = MaterialTheme.typography.bodySmall)
            state.schedules.forEach { s ->
                Card {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${s.time} · ${actionLabels.toMap()[s.action.type] ?: s.action.type}" +
                                (s.action.value?.let { " $it" } ?: ""), fontWeight = FontWeight.Bold)
                            Text(if (s.targets.isEmpty()) "Tutti i clima" else "${s.targets.size} clima",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { s.id?.let { vm.deleteSchedule(it) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Elimina")
                        }
                    }
                }
            }
            HorizontalDivider()
            Text("Nuova pianificazione", fontWeight = FontWeight.Bold)
            NumField(time, { time = it }, "Orario (HH:MM)")
            Text("Azione", style = MaterialTheme.typography.bodyMedium)
            ChipRow(actionLabels.map { it.first }, actionType, labels = actionLabels.toMap()) { actionType = it }
            if (actionType in listOf("setpoint", "mode", "fan")) {
                NumField(value, { value = it }, when (actionType) {
                    "setpoint" -> "Valore °C"; "mode" -> "cool/heat/dry/wind/auto"; else -> "auto/low/medium/high/turbo"
                })
            }
            if (actionType !in listOf("away_on", "away_off")) {
                Text("Climatizzatori", style = MaterialTheme.typography.bodyMedium)
                devices.forEach { dev ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = dev.deviceId in targets, onCheckedChange = { c ->
                            targets = if (c) targets + dev.deviceId else targets - dev.deviceId
                        })
                        Text(dev.deviceName ?: dev.deviceId)
                    }
                }
            }
            Button(
                onClick = {
                    val act = ScheduleAction(actionType, if (actionType in listOf("setpoint", "mode", "fan")) value else null)
                    val tg = if (actionType in listOf("away_on", "away_off")) emptyList()
                    else if (targets.size == devices.size) emptyList() else targets.toList()
                    vm.upsertSchedule(Schedule(time = time, action = act, targets = tg))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Aggiungi") }
        }
    }
}

// ---------------- Sicurezza ----------------
@Composable
fun SafetyScreen(state: UiState, vm: ClimaViewModel, nav: NavHostController) {
    LaunchedEffect(Unit) { vm.loadSafety() }
    val cfg = state.safety ?: SafetyConfig()
    var enabled by remember(cfg) { mutableStateOf(cfg.enabled) }
    var hours by remember(cfg) { mutableStateOf(cfg.maxRuntimeHours.toString()) }

    BackTopBar("Sicurezza", nav) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Spegne automaticamente i climatizzatori accesi da troppo tempo.",
                style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Attiva", Modifier.weight(1f)); Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            NumField(hours, { hours = it }, "Ore massime di funzionamento")
            Button(onClick = { vm.saveSafety(SafetyConfig(enabled, hours.toDoubleOrNull() ?: cfg.maxRuntimeHours)) },
                modifier = Modifier.fillMaxWidth()) { Text("Salva") }
        }
    }
}

// ---------------- Config notifiche ----------------
@Composable
fun NotifConfigScreen(state: UiState, vm: ClimaViewModel, nav: NavHostController) {
    LaunchedEffect(Unit) { vm.loadNotifConfig() }
    val cfg = state.notifConfig ?: NotificationConfig()
    var hi by remember(cfg) { mutableStateOf(cfg.tempHighEnabled) }
    var hiT by remember(cfg) { mutableStateOf(cfg.tempHighThreshold.toString()) }
    var lo by remember(cfg) { mutableStateOf(cfg.tempLowEnabled) }
    var loT by remember(cfg) { mutableStateOf(cfg.tempLowThreshold.toString()) }
    var off by remember(cfg) { mutableStateOf(cfg.offlineEnabled) }
    var offM by remember(cfg) { mutableStateOf(cfg.offlineMinutes.toString()) }
    var filt by remember(cfg) { mutableStateOf(cfg.filterEnabled) }

    BackTopBar("Notifiche", nav) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledSwitch("Avviso temperatura alta", hi) { hi = it }
            if (hi) NumField(hiT, { hiT = it }, "Soglia alta °C")
            LabeledSwitch("Avviso temperatura bassa", lo) { lo = it }
            if (lo) NumField(loT, { loT = it }, "Soglia bassa °C")
            LabeledSwitch("Avviso collector offline", off) { off = it }
            if (off) NumField(offM, { offM = it }, "Minuti senza dati")
            LabeledSwitch("Avviso filtro da controllare", filt) { filt = it }
            Button(onClick = {
                vm.saveNotifConfig(NotificationConfig(
                    tempHighEnabled = hi, tempHighThreshold = hiT.toDoubleOrNull() ?: cfg.tempHighThreshold,
                    tempLowEnabled = lo, tempLowThreshold = loT.toDoubleOrNull() ?: cfg.tempLowThreshold,
                    offlineEnabled = off, offlineMinutes = offM.toIntOrNull() ?: cfg.offlineMinutes,
                    filterEnabled = filt,
                ))
            }, modifier = Modifier.fillMaxWidth()) { Text("Salva") }
        }
    }
}

// ---------------- Notifiche (lista) ----------------
@Composable
fun NotificationsScreen(state: UiState, vm: ClimaViewModel, nav: NavHostController) {
    LaunchedEffect(Unit) { vm.loadAlerts() }
    BackTopBar("Notifiche", nav) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (state.alerts.isNotEmpty()) {
                TextButton(onClick = { vm.markAllRead() }, modifier = Modifier.align(Alignment.End).padding(end = 8.dp)) {
                    Text("Segna tutte come lette")
                }
            }
            if (state.alerts.isEmpty()) Text("Nessuna notifica.", Modifier.padding(16.dp))
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.alerts.reversed()) { a -> AlertRow(a) }
            }
        }
    }
}

@Composable
fun AlertRow(a: Alert) {
    Card(colors = CardDefaults.cardColors(
        containerColor = if (a.read) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
    )) {
        Column(Modifier.padding(12.dp)) {
            Text(a.message, fontWeight = if (a.read) FontWeight.Normal else FontWeight.Bold)
            Text(a.time.replace("T", " "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------- Pi-hole ----------------
@Composable
fun PiholeScreen(state: UiState, vm: ClimaViewModel, nav: NavHostController) {
    LaunchedEffect(Unit) { vm.loadPihole() }
    val p = state.pihole
    BackTopBar("Pi-hole", nav) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (p == null) {
                state.error?.let { Text("Errore: $it", color = MaterialTheme.colorScheme.error) }
                    ?: Text("Caricamento…")
                return@BackTopBar
            }
            val blocking = p.blocking == "enabled"
            Card(colors = CardDefaults.cardColors(
                containerColor = if (blocking) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer
            )) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (blocking) "Blocco ATTIVO" else "Blocco DISATTIVATO", fontWeight = FontWeight.Bold)
                    p.timer?.let { if (!blocking) Text("Riattivazione tra ${it.toInt()} s", style = MaterialTheme.typography.bodySmall) }
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (blocking) {
                    OutlinedButton(onClick = { vm.setPiholeBlocking(false, 300) }) { Text("5 min") }
                    OutlinedButton(onClick = { vm.setPiholeBlocking(false, 1800) }) { Text("30 min") }
                    OutlinedButton(onClick = { vm.setPiholeBlocking(false) }) { Text("Disattiva") }
                } else {
                    Button(onClick = { vm.setPiholeBlocking(true) }) { Text("Riattiva blocco") }
                }
            }
            HorizontalDivider()
            StatRow("Query oggi", p.total?.toString() ?: "-")
            StatRow("Bloccate", buildString {
                append(p.blocked?.toString() ?: "-")
                p.percentBlocked?.let { append("  (${"%.1f".format(it)}%)") }
            })
            StatRow("Domini unici", p.uniqueDomains?.toString() ?: "-")
            StatRow("Domini in blocklist", p.domainsBlocked?.toString() ?: "-")
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

// ---------------- helper UI ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, nav: NavHostController, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        }
    ) { content(it) }
}

@Composable
fun NumField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
fun LabeledSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChipRow(options: List<String>, selected: String? = null, labels: Map<String, String> = emptyMap(), onClick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            FilterChip(selected = opt == selected, onClick = { onClick(opt) }, label = { Text(labels[opt] ?: opt) })
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).background(color)); Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------- grafico multi-serie ----------------
data class Series(val label: String, val color: Color, val dashed: Boolean, val points: List<HistoryPoint>)

private fun parseEpoch(t: String): Long? =
    try { OffsetDateTime.parse(t).toInstant().toEpochMilli() } catch (e: Exception) { null }

@Composable
fun MultiSeriesChart(series: List<Series>, modifier: Modifier) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()

    val parsed = series.map { s -> s to s.points.mapNotNull { p -> p.value?.let { v -> parseEpoch(p.time)?.let { it to v } } } }
    val all = parsed.flatMap { it.second }
    if (all.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("Dati insufficienti", style = MaterialTheme.typography.bodySmall) }
        return
    }
    var minY = all.minOf { it.second }; var maxY = all.maxOf { it.second }
    if (minY == maxY) { minY -= 1; maxY += 1 }
    val rangeY = maxY - minY
    val tMin = all.minOf { it.first }; val tMax = all.maxOf { it.first }
    val spanMs = (tMax - tMin).coerceAtLeast(1L)
    val fmt = DateTimeFormatter.ofPattern(if (spanMs > 36L * 3600 * 1000) "dd/MM" else "HH:mm").withZone(ZoneId.systemDefault())
    val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor)

    Canvas(modifier) {
        val leftPad = 44f; val bottomPad = 30f; val topPad = 10f; val rightPad = 14f
        val plotW = size.width - leftPad - rightPad
        val plotH = size.height - topPad - bottomPad
        val ticks = 4
        for (i in 0..ticks) {
            val frac = i.toFloat() / ticks
            val v = minY + rangeY * frac
            val y = topPad + plotH - plotH * frac
            drawLine(gridColor, Offset(leftPad, y), Offset(leftPad + plotW, y), strokeWidth = 1f)
            val l = measurer.measure("${v.roundToInt()}°", labelStyle)
            drawText(l, topLeft = Offset(leftPad - l.size.width - 4f, y - l.size.height / 2))
        }
        for (i in 0..2) {
            val frac = i / 2f
            val tx = leftPad + plotW * frac
            val l = measurer.measure(fmt.format(Instant.ofEpochMilli(tMin + (spanMs * frac).toLong())), labelStyle)
            drawText(l, topLeft = Offset((tx - l.size.width / 2).coerceIn(0f, size.width - l.size.width), topPad + plotH + 8f))
        }
        parsed.forEach { (s, pts) ->
            if (pts.size < 2) return@forEach
            val path = Path()
            pts.sortedBy { it.first }.forEachIndexed { i, (t, v) ->
                val x = leftPad + plotW * ((t - tMin).toFloat() / spanMs)
                val y = topPad + plotH - plotH * ((v - minY) / rangeY).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = s.color, style = Stroke(width = 4f,
                pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 10f)) else null))
        }
    }
}
