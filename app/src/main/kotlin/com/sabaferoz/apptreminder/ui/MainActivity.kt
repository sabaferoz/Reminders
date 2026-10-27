package com.sabaferoz.apptreminder.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sabaferoz.apptreminder.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val route = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Release builds hide content from screenshots and the recent-apps thumbnail (psychiatric data).
        if (!BuildConfig.DEBUG) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        route.value = intent?.getStringExtra(EXTRA_ROUTE)
        setContent {
            AppTheme { AppRoot(route) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route.value = intent.getStringExtra(EXTRA_ROUTE)
    }

    companion object { const val EXTRA_ROUTE = "route" }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("dashboard", "Dashboard", Icons.Filled.Home),
    Tab("reminders", "Reminders", Icons.Filled.Notifications),
    Tab("patients", "Patients", Icons.Filled.Person),
    Tab("history", "History", Icons.Filled.DateRange),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

@Composable
private fun AppRoot(routeFlow: MutableStateFlow<String?>) {
    val vm: MainViewModel = viewModel()
    val nav = rememberNavController()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val target by routeFlow.collectAsState()
    LaunchedEffect(target) {
        target?.let { nav.navigate(it) { launchSingleTop = true; popUpTo("dashboard") }; routeFlow.value = null }
    }

    val msg by vm.message.collectAsState()
    LaunchedEffect(msg) { msg?.let { snackbar.showSnackbar(it); vm.toast(null) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BottomBar(nav) },
    ) { pad ->
        NavHost(nav, startDestination = "dashboard", modifier = Modifier.padding(pad)) {
            composable("dashboard") { DashboardScreen(vm, onOpenReminders = { nav.navigate("reminders") }) }
            composable("reminders") { RemindersScreen(vm) }
            composable("patients") { PatientsScreen(vm, onEdit = { nav.navigate("patient/$it") }) }
            composable("patient/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
                PatientEditScreen(vm, e.arguments?.getLong("id") ?: -1L, onDone = { nav.popBackStack() })
            }
            composable("history") { HistoryScreen(vm) }
            composable("settings") { SettingsScreen(vm) }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController) {
    val current = nav.currentBackStackEntryAsState().value?.destination?.route
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        tabs.forEach { t ->
            NavigationBarItem(
                selected = current == t.route || (t.route == "patients" && current?.startsWith("patient/") == true),
                onClick = { nav.navigate(t.route) { launchSingleTop = true; popUpTo("dashboard") } },
                icon = { Icon(t.icon, t.label) },
                label = { Text(t.label) },
            )
        }
    }
}
