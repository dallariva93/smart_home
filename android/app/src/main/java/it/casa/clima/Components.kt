package it.casa.clima

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Sfondo di brand: scuro con un alone neon in alto (radiale ciano molto soft). */
@Composable
fun BrandBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to NeonCyan.copy(alpha = 0.06f),
                    0.35f to Color.Transparent,
                    1f to Color.Transparent,
                )
            )
    ) { content() }
}

/** Intestazione di sezione: etichetta maiuscola, spaziata, tenue. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 4.dp),
    )
}

/** Card del design system: angoli morbidi, superficie elevata, bordo neon se "active". */
@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable () -> Unit,
) {
    val border by animateColorAsState(
        if (active) NeonCyan.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant,
        label = "cardBorder",
    )
    val shape = RoundedCornerShape(22.dp)
    var m = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .border(if (active) 1.5.dp else 1.dp, border, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m.padding(contentPadding)) { content() }
}

/** Pillola di stato luminosa (ON/OFF, online, ecc.). */
@Composable
fun StatusPill(text: String, on: Boolean, modifier: Modifier = Modifier) {
    val c = if (on) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .clip(CircleShape)
            .background(c.copy(alpha = if (on) 0.16f else 0.10f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        Text(text, color = c, style = MaterialTheme.typography.labelLarge)
    }
}

/** Etichetta + valore su riga, per liste di statistiche. */
@Composable
fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

/** Scaffold con TopAppBar e tasto Indietro, sfondo di brand. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { pad -> BrandBackground { content(pad) } }
}

/** Riga di chip selezionabili (modalità/ventola/azioni) con icona opzionale. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChipRow(
    options: List<String>,
    selected: String? = null,
    labels: Map<String, String> = emptyMap(),
    icons: Map<String, ImageVector> = emptyMap(),
    onClick: (String) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { opt ->
            val isSel = opt == selected
            FilterChip(
                selected = isSel,
                onClick = { onClick(opt) },
                label = { Text(labels[opt] ?: opt) },
                leadingIcon = icons[opt]?.let { ic -> { Icon(ic, null, Modifier.size(18.dp)) } },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSel,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}

/** Stato vuoto elegante con icona in cerchio tenue. */
@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Banner d'errore con eventuale "Riprova". */
@Composable
fun ErrorBanner(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    GlowCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Qualcosa è andato storto", fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error)
                Text(message, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onRetry != null) TextButton(onClick = onRetry) { Text("Riprova") }
        }
    }
}

/** Caricamento centrato. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/* --------------------------------------------------------------------------
 * Termostato circolare — pezzo forte del controllo: arco neon a 270° con glow,
 * numero grande del setpoint al centro e +/- per la regolazione.
 * ------------------------------------------------------------------------ */
@Composable
fun CircularThermostat(
    setpoint: Int,
    current: Double?,
    isOn: Boolean,
    min: Int = 16,
    max: Int = 30,
    onChange: (Int) -> Unit,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val arcColor = if (isOn) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
    val onSurfaceMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val frac by animateFloatAsState(
        ((setpoint - min).toFloat() / (max - min)).coerceIn(0f, 1f),
        label = "thermoFrac",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            Modifier.fillMaxWidth(0.78f).aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 26f
                val startAngle = 135f
                val maxSweep = 270f
                val topLeft = Offset(stroke, stroke)
                val arcSize = Size(size.width - stroke * 2, size.height - stroke * 2)
                // traccia di fondo
                drawArc(track, startAngle, maxSweep, false, topLeft, arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round))
                // glow (più passate larghe e trasparenti)
                val sweep = maxSweep * frac
                listOf(2.6f to 0.07f, 1.8f to 0.12f).forEach { (w, a) ->
                    drawArc(arcColor.copy(alpha = a), startAngle, sweep, false, topLeft, arcSize,
                        style = Stroke(stroke * w, cap = StrokeCap.Round))
                }
                // arco principale
                drawArc(arcColor, startAngle, sweep, false, topLeft, arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SETPOINT", style = MaterialTheme.typography.labelMedium, color = onSurfaceMuted)
                Row(verticalAlignment = Alignment.Top) {
                    Text("$setpoint", fontSize = 76.sp, fontWeight = FontWeight.Bold, color = onSurface)
                    Text("°", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = arcColor,
                        modifier = Modifier.padding(top = 12.dp))
                }
                current?.let {
                    Text("Stanza ${it.roundToInt()}°C", style = MaterialTheme.typography.bodyMedium,
                        color = onSurfaceMuted)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(
                onClick = { if (setpoint > min) onChange(setpoint - 1) },
                modifier = Modifier.size(58.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = onSurface,
                ),
            ) { Icon(Icons.Filled.Remove, "Diminuisci", Modifier.size(26.dp)) }
            FilledIconButton(
                onClick = { if (setpoint < max) onChange(setpoint + 1) },
                modifier = Modifier.size(58.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) { Icon(Icons.Filled.Add, "Aumenta", Modifier.size(26.dp)) }
        }
    }
}

/** Pallino di legenda per i grafici. */
@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Wrapper grafico: titolo + canvas dentro una GlowCard. */
@Composable
fun ChartCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    GlowCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
