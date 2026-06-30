package it.casa.clima

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import it.casa.clima.net.*
import kotlin.math.roundToInt

private val modeIcons = mapOf(
    "cool" to Icons.Filled.AcUnit, "heat" to Icons.Filled.Whatshot,
    "dry" to Icons.Filled.WaterDrop, "wind" to Icons.Filled.Air, "auto" to Icons.Filled.Autorenew,
)
private val modeLabels = mapOf(
    "cool" to "Freddo", "heat" to "Caldo", "dry" to "Deumid.", "wind" to "Vento", "auto" to "Auto",
)
private val fanLabels = mapOf(
    "auto" to "Auto", "low" to "Bassa", "medium" to "Media", "high" to "Alta", "turbo" to "Turbo",
)
private val oscLabels = mapOf(
    "fixed" to "Fisso", "all" to "Tutte", "vertical" to "Verticale", "horizontal" to "Orizzontale",
)

// ---------------- Dashboard ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(state: UiState, vm: ClimaViewModel, nav: NavHostController) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Clima Casa", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Aggiorna")
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate("notifications") }) {
                        BadgedBox(badge = {
                            if (state.unread > 0) Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ) { Text("${state.unread}") }
                        }) { Icon(Icons.Filled.Notifications, contentDescription = "Notifiche") }
                    }
                    IconButton(onClick = { nav.navigate("settings") }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Impostazioni")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { pad ->
        BrandBackground {
            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = { vm.refresh() },
                modifier = Modifier.padding(pad).fillMaxSize(),
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { HomeHero(state) }
                    state.error?.let { item { ErrorBanner(it, onRetry = { vm.refresh() }) } }
                    item { AwayCard(state.away?.enabled == true) { vm.setAway(it) } }

                    if (state.devices.isNotEmpty()) {
                        item { SectionHeader("Climatizzatori") }
                        items(state.devices, key = { it.deviceId }) { dev ->
                            DeviceCard(dev) { nav.navigate("control/${dev.deviceId}") }
                        }
                    } else if (!state.loading) {
                        item {
                            EmptyState(Icons.Filled.Thermostat, "Nessun climatizzatore",
                                "Tira giù per aggiornare o controlla la connessione al backend.")
                        }
                    }

                    if (state.plugs.isNotEmpty()) {
                        item { SectionHeader("Prese") }
                        items(state.plugs, key = { it.id }) { plug ->
                            PlugCard(plug) { on -> vm.setPlug(plug.id, on) }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun HomeHero(state: UiState) {
    val onCount = state.devices.count { it.isOn }
    val temps = state.devices.mapNotNull { it.temperature }
    val avg = if (temps.isNotEmpty()) temps.average() else null
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(
                NeonCyan.copy(alpha = 0.20f), ElectricBlue.copy(alpha = 0.10f),
            )))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.35f))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("CASA", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    avg?.let { "${it.roundToInt()}°C" } ?: "—",
                    style = MaterialTheme.typography.displaySmall,
                )
                Text(
                    if (state.devices.isEmpty()) "In attesa di dispositivi"
                    else "$onCount di ${state.devices.size} attivi · temperatura media",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier.size(58.dp).clip(CircleShape)
                    .background(NeonCyan.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Thermostat, null, Modifier.size(30.dp), tint = NeonCyan) }
        }
    }
}

@Composable
private fun AwayCard(away: Boolean, onToggle: (Boolean) -> Unit) {
    GlowCard(active = away) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(
                    (if (away) ElectricLime else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.16f)
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Eco, null,
                    tint = if (away) ElectricLime else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Modalità Away / Eco", fontWeight = FontWeight.Bold)
                Text(
                    if (away) "Tutto spento · automazioni notturne in pausa" else "Spegne tutto con un tocco",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = away, onCheckedChange = onToggle)
        }
    }
}

@Composable
fun DeviceCard(dev: DeviceState, onClick: () -> Unit) {
    GlowCard(active = dev.isOn, onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(
                        (if (dev.isOn) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.15f)
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(modeIcons[dev.acMode] ?: Icons.Filled.AcUnit, null, Modifier.size(22.dp),
                        tint = if (dev.isOn) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Text(dev.deviceName ?: dev.deviceId, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                StatusPill(if (dev.isOn) "ON" else "OFF", dev.isOn)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(dev.temperature?.let { "${it.roundToInt()}°" } ?: "—",
                    style = MaterialTheme.typography.displaySmall, fontSize = 46.sp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.padding(bottom = 6.dp)) {
                    dev.coolingSetpoint?.let {
                        Text("Setpoint ${it.roundToInt()}°C", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    dev.acMode?.let {
                        Text("Modalità ${modeLabels[it] ?: it}", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun PlugCard(plug: PlugState, onToggle: (Boolean) -> Unit) {
    GlowCard(active = plug.on == true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(
                    (if (plug.on == true) ElectricLime else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.15f)
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Bolt, null, Modifier.size(22.dp),
                    tint = if (plug.on == true) ElectricLime else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(plug.name ?: plug.id, fontWeight = FontWeight.Bold)
                val info = buildString {
                    plug.powerW?.let { append("${it} W") }
                    if (plug.online == false) { if (isNotEmpty()) append(" · "); append("offline") }
                }
                if (info.isNotEmpty()) Text(info, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = plug.on == true, onCheckedChange = onToggle)
        }
    }
}

// ---------------- Controllo dispositivo ----------------
@Composable
fun ControlScreen(id: String?, state: UiState, vm: ClimaViewModel, nav: NavHostController) {
    val device = state.devices.firstOrNull { it.deviceId == id }
    if (device == null) {
        BackScaffold("Controllo", { nav.popBackStack() }) { pad ->
            EmptyState(Icons.Filled.Thermostat, "Dispositivo non trovato",
                modifier = Modifier.padding(pad))
        }
        return
    }
    var setpoint by remember(device.coolingSetpoint) {
        mutableStateOf((device.coolingSetpoint ?: 24.0).roundToInt())
    }

    BackScaffold(device.deviceName ?: "Controllo", { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            GlowCard(active = device.isOn) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PowerSettingsNew,
                        null, tint = if (device.isOn) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Accensione", style = MaterialTheme.typography.titleMedium)
                        Text(if (device.isOn) "Acceso" else "Spento",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = device.isOn, onCheckedChange = { vm.setPower(device.deviceId, it) })
                }
            }

            CircularThermostat(
                setpoint = setpoint,
                current = device.temperature,
                isOn = device.isOn,
                onChange = { setpoint = it; vm.setSetpoint(device.deviceId, it.toDouble()) },
            )

            ControlSection("Modalità") {
                ChipRow(listOf("cool", "heat", "dry", "wind", "auto"), device.acMode,
                    labels = modeLabels, icons = modeIcons) { vm.setMode(device.deviceId, it) }
            }
            ControlSection("Ventola") {
                ChipRow(listOf("auto", "low", "medium", "high", "turbo"), device.fanMode,
                    labels = fanLabels) { vm.setFan(device.deviceId, it) }
            }
            ControlSection("Oscillazione") {
                ChipRow(listOf("fixed", "all", "vertical", "horizontal"), device.oscillationMode,
                    labels = oscLabels) { vm.setOscillation(device.deviceId, it) }
            }

            FilledTonalButton(
                onClick = { nav.navigate("chart/${device.deviceId}") },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Filled.ShowChart, null); Spacer(Modifier.width(8.dp))
                Text("Grafici e statistiche")
            }
        }
    }
}

@Composable
private fun ControlSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title)
        content()
    }
}

// ---------------- Grafici ----------------
@Composable
fun DeviceChartScreen(id: String?, state: UiState, vm: ClimaViewModel, nav: NavHostController) {
    val device = state.devices.firstOrNull { it.deviceId == id }
    var range by remember { mutableStateOf("24h") }
    var compare by remember { mutableStateOf(false) }
    val days = if (range == "7d") 7 else 2
    LaunchedEffect(range) {
        state.devices.forEach {
            vm.loadHistory(it.deviceId, range, if (range == "7d") "2h" else if (range == "48h") "30m" else "15m")
        }
        id?.let { vm.loadStats(it, days) }
    }
    val hist = id?.let { state.history[it] }
    val temps = (hist?.temperature ?: emptyList()).mapNotNull { it.value }
    val palette = listOf(
        MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary,
        ElectricLime, MaterialTheme.colorScheme.error,
    )

    BackScaffold("Grafici · ${device?.deviceName ?: ""}", { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ChipRow(listOf("24h", "48h", "7d"), range,
                labels = mapOf("24h" to "24h", "48h" to "48h", "7d" to "7 giorni")) { range = it }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Confronto stanze", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = compare, onCheckedChange = { compare = it })
            }

            if (compare) {
                ChartCard("Temperatura per stanza (°C)") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.devices.forEachIndexed { i, d -> LegendDot(palette[i % palette.size], d.deviceName ?: d.deviceId) }
                    }
                    val series = state.devices.mapIndexed { i, d ->
                        Series(d.deviceName ?: d.deviceId, palette[i % palette.size], false,
                            state.history[d.deviceId]?.temperature ?: emptyList())
                    }
                    MultiSeriesChart(series, Modifier.fillMaxWidth().height(260.dp))
                }
            } else {
                ChartCard("Temperatura e setpoint (°C)") {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendDot(MaterialTheme.colorScheme.primary, "Temperatura")
                        LegendDot(MaterialTheme.colorScheme.tertiary, "Setpoint")
                    }
                    MultiSeriesChart(
                        listOf(
                            Series("Temperatura", MaterialTheme.colorScheme.primary, false, hist?.temperature ?: emptyList()),
                            Series("Setpoint", MaterialTheme.colorScheme.tertiary, true, hist?.setpoint ?: emptyList()),
                        ),
                        Modifier.fillMaxWidth().height(260.dp),
                    )
                    if (temps.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            MiniStat("MIN", "${temps.min().roundToInt()}°")
                            MiniStat("MEDIA", "${"%.1f".format(temps.average())}°")
                            MiniStat("MAX", "${temps.max().roundToInt()}°")
                        }
                    }
                }
            }

            GlowCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Riepilogo giornaliero", style = MaterialTheme.typography.titleMedium)
                    val stats = (id?.let { state.stats[it] }) ?: emptyList()
                    if (stats.isEmpty()) {
                        Text("Nessun dato.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    stats.forEach { d ->
                        StatRow(d.day, "min ${fmt1(d.min)}  ·  max ${fmt1(d.max)}  ·  media ${fmt1(d.mean)} °C")
                    }
                }
            }
        }
    }
}

private fun fmt1(v: Double?) = v?.let { "%.1f".format(it) } ?: "-"

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

// ---------------- Impostazioni (hub) ----------------
@Composable
fun SettingsHub(nav: NavHostController) {
    BackScaffold("Impostazioni", { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsRow(Icons.Filled.Bedtime, NeonCyan, "Modalità notturna",
                "Curva temperatura notturna automatica") { nav.navigate("nightmode") }
            SettingsRow(Icons.Filled.Schedule, ElectricBlue, "Pianificazioni",
                "Accensioni e spegnimenti a orario") { nav.navigate("schedules") }
            SettingsRow(Icons.Filled.Shield, ElectricLime, "Sicurezza",
                "Auto-spegnimento dopo N ore") { nav.navigate("safety") }
            SettingsRow(Icons.Filled.Notifications, Amber, "Notifiche",
                "Soglie e avvisi") { nav.navigate("notifconfig") }
            SettingsRow(Icons.Filled.Block, Danger, "Pi-hole",
                "Statistiche e blocco pubblicità") { nav.navigate("pihole") }
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    GlowCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = tint) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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

    BackScaffold("Modalità notturna", { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlowCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bedtime, null, tint = NeonCyan)
                        Spacer(Modifier.width(10.dp))
                        Text("Come funziona", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Punta a una temperatura comfort all'addormentamento, la lascia salire " +
                            "dolcemente di alcuni gradi nel cuore della notte (sonno profondo, meno " +
                            "consumo) e la riporta al comfort prima del risveglio. Regola in base alla " +
                            "temperatura misurata, con isteresi per non far pendolare il compressore. " +
                            "Si applica solo ai climatizzatori selezionati.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LabeledSwitch("Attiva modalità notturna", enabled) { enabled = it }

            SectionHeader("Climatizzatori")
            DeviceCheckList(devices, selected) { selected = it }

            SectionHeader("Parametri")
            NumField(start, { start = it }, "Inizio (HH:MM)")
            NumField(end, { end = it }, "Fine (HH:MM)")
            NumField(target, { target = it }, "Temperatura comfort °C")
            NumField(offset, { offset = it }, "Aumento notturno °C")
            NumField(ramp, { ramp = it }, "Durata salita (min)")
            NumField(preWake, { preWake = it }, "Ritorno comfort prima del risveglio (min)")
            PrimaryButton("Salva") {
                vm.saveNightmode(cfg.copy(
                    enabled = enabled, deviceIds = selected.toList(), start = start, end = end,
                    targetTemp = target.toDoubleOrNull() ?: cfg.targetTemp,
                    nightOffset = offset.toDoubleOrNull() ?: cfg.nightOffset,
                    rampMinutes = ramp.toIntOrNull() ?: cfg.rampMinutes,
                    preWakeMinutes = preWake.toIntOrNull() ?: cfg.preWakeMinutes,
                ))
            }
        }
    }
}

// ---------------- Pianificazioni ----------------
@Composable
fun SchedulesScreen(state: UiState, vm: ClimaViewModel, nav: NavHostController) {
    LaunchedEffect(Unit) { vm.loadSchedules() }
    val devices = state.devices
    var time by remember { mutableStateOf("22:00") }
    var actionType by remember { mutableStateOf("power_on") }
    var value by remember { mutableStateOf("24") }
    var targets by remember(devices) { mutableStateOf(devices.map { it.deviceId }.toSet()) }

    val actionLabels = mapOf(
        "power_on" to "Accendi", "power_off" to "Spegni", "setpoint" to "Setpoint",
        "mode" to "Modalità", "fan" to "Ventola", "away_on" to "Away ON", "away_off" to "Away OFF",
    )
    val actionOrder = actionLabels.keys.toList()

    BackScaffold("Pianificazioni", { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader("Attive")
            if (state.schedules.isEmpty()) {
                EmptyState(Icons.Filled.Schedule, "Nessuna pianificazione",
                    "Aggiungine una qui sotto.")
            }
            state.schedules.forEach { s ->
                GlowCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(ElectricBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) { Text(s.time, style = MaterialTheme.typography.labelLarge, color = ElectricBlue) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text((actionLabels[s.action.type] ?: s.action.type) +
                                (s.action.value?.let { " $it" } ?: ""), fontWeight = FontWeight.Bold)
                            Text(if (s.targets.isEmpty()) "Tutti i clima" else "${s.targets.size} clima",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { s.id?.let { vm.deleteSchedule(it) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Elimina",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            SectionHeader("Nuova pianificazione")
            NumField(time, { time = it }, "Orario (HH:MM)")
            ChipRow(actionOrder, actionType, labels = actionLabels) { actionType = it }
            if (actionType in listOf("setpoint", "mode", "fan")) {
                NumField(value, { value = it }, when (actionType) {
                    "setpoint" -> "Valore °C"; "mode" -> "cool/heat/dry/wind/auto"; else -> "auto/low/medium/high/turbo"
                })
            }
            if (actionType !in listOf("away_on", "away_off")) {
                SectionHeader("Climatizzatori")
                DeviceCheckList(devices, targets) { targets = it }
            }
            PrimaryButton("Aggiungi", Icons.Filled.Add) {
                val act = ScheduleAction(actionType, if (actionType in listOf("setpoint", "mode", "fan")) value else null)
                val tg = if (actionType in listOf("away_on", "away_off")) emptyList()
                else if (targets.size == devices.size) emptyList() else targets.toList()
                vm.upsertSchedule(Schedule(time = time, action = act, targets = tg))
            }
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

    BackScaffold("Sicurezza", { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlowCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, null, tint = ElectricLime)
                    Spacer(Modifier.width(12.dp))
                    Text("Spegne automaticamente i climatizzatori accesi da troppo tempo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LabeledSwitch("Attiva auto-spegnimento", enabled) { enabled = it }
            NumField(hours, { hours = it }, "Ore massime di funzionamento")
            PrimaryButton("Salva") {
                vm.saveSafety(SafetyConfig(enabled, hours.toDoubleOrNull() ?: cfg.maxRuntimeHours))
            }
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

    BackScaffold("Notifiche", { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlowCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledSwitch("Avviso temperatura alta", hi) { hi = it }
                    if (hi) NumField(hiT, { hiT = it }, "Soglia alta °C")
                    LabeledSwitch("Avviso temperatura bassa", lo) { lo = it }
                    if (lo) NumField(loT, { loT = it }, "Soglia bassa °C")
                    LabeledSwitch("Avviso collector offline", off) { off = it }
                    if (off) NumField(offM, { offM = it }, "Minuti senza dati")
                    LabeledSwitch("Avviso filtro da controllare", filt) { filt = it }
                }
            }
            PrimaryButton("Salva") {
                vm.saveNotifConfig(NotificationConfig(
                    tempHighEnabled = hi, tempHighThreshold = hiT.toDoubleOrNull() ?: cfg.tempHighThreshold,
                    tempLowEnabled = lo, tempLowThreshold = loT.toDoubleOrNull() ?: cfg.tempLowThreshold,
                    offlineEnabled = off, offlineMinutes = offM.toIntOrNull() ?: cfg.offlineMinutes,
                    filterEnabled = filt,
                ))
            }
        }
    }
}

// ---------------- Notifiche (lista) ----------------
@Composable
fun NotificationsScreen(state: UiState, vm: ClimaViewModel, nav: NavHostController) {
    LaunchedEffect(Unit) { vm.loadAlerts() }
    BackScaffold("Notifiche", { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (state.alerts.isNotEmpty()) {
                TextButton(onClick = { vm.markAllRead() },
                    modifier = Modifier.align(Alignment.End).padding(end = 8.dp)) {
                    Text("Segna tutte come lette")
                }
            }
            if (state.alerts.isEmpty()) {
                EmptyState(Icons.Filled.NotificationsNone, "Nessuna notifica",
                    "Gli avvisi su temperatura, offline e filtri compaiono qui.")
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.alerts.reversed()) { a -> AlertRow(a) }
            }
        }
    }
}

@Composable
private fun AlertRow(a: Alert) {
    GlowCard(active = !a.read) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                if (a.read) Icons.Filled.Notifications else Icons.Filled.NotificationsActive,
                null,
                tint = if (a.read) MaterialTheme.colorScheme.onSurfaceVariant else NeonCyan,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.message, fontWeight = if (a.read) FontWeight.Normal else FontWeight.Bold)
                Text(a.time.replace("T", " "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------- Pi-hole ----------------
@Composable
fun PiholeScreen(state: UiState, vm: ClimaViewModel, nav: NavHostController) {
    LaunchedEffect(Unit) { vm.loadPihole() }
    val p = state.pihole
    BackScaffold("Pi-hole", { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (p == null) {
                state.error?.let { ErrorBanner(it, onRetry = { vm.loadPihole() }) } ?: LoadingState()
                return@BackScaffold
            }
            val blocking = p.blocking == "enabled"
            GlowCard(active = blocking) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(
                            (if (blocking) ElectricLime else Danger).copy(alpha = 0.15f)
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (blocking) Icons.Filled.Shield else Icons.Filled.Block, null,
                            tint = if (blocking) ElectricLime else Danger)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (blocking) "Blocco attivo" else "Blocco disattivato",
                            fontWeight = FontWeight.Bold)
                        p.timer?.let {
                            if (!blocking) Text("Riattivazione tra ${it.toInt()} s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (blocking) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.setPiholeBlocking(false, 300) }, modifier = Modifier.weight(1f)) { Text("5 min") }
                    OutlinedButton(onClick = { vm.setPiholeBlocking(false, 1800) }, modifier = Modifier.weight(1f)) { Text("30 min") }
                    OutlinedButton(onClick = { vm.setPiholeBlocking(false) }, modifier = Modifier.weight(1f)) { Text("Off") }
                }
            } else {
                PrimaryButton("Riattiva blocco") { vm.setPiholeBlocking(true) }
            }
            GlowCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    }
}

// ---------------- helper UI condivisi ----------------
@Composable
fun DeviceCheckList(devices: List<DeviceState>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    Column {
        devices.forEach { dev ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = dev.deviceId in selected, onCheckedChange = { c ->
                    onChange(if (c) selected + dev.deviceId else selected - dev.deviceId)
                })
                Text(dev.deviceName ?: dev.deviceId)
            }
        }
    }
}

@Composable
fun NumField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun LabeledSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun PrimaryButton(text: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        if (icon != null) { Icon(icon, null); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
