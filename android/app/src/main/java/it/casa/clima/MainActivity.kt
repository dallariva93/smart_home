package it.casa.clima

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge con icone di sistema chiare (tema scuro di brand).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
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
