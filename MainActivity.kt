package com.screenwise.app

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.screenwise.app.data.*
import com.screenwise.app.export.ReportExporter
import com.screenwise.app.ui.ScreenWiseViewModel
import com.screenwise.app.usage.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val Charcoal = Color(0xFF171D20)
private val Mint = Color(0xFFBCEBC0)
private val OffWhite = Color(0xFFF7F8F4)
private val DarkBg = Color(0xFF101414)
private val Fresh = Color(0xFF467D56)
private val Categories = listOf("Social Media", "Entertainment", "Gaming", "Productivity", "Education", "Communication", "Other")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: ScreenWiseViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            val scheme = if (dark) darkColorScheme(primary = Mint, onPrimary = Charcoal, background = DarkBg,
                surface = Color(0xFF1B2422), onSurface = OffWhite, onBackground = OffWhite)
            else lightColorScheme(primary = Fresh, onPrimary = Color.White, background = OffWhite,
                surface = Color.White, onSurface = Charcoal, onBackground = Charcoal)
            val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
                uri?.let {
                    try { ReportExporter.writeCsv(this, it, vm.report.value); Toast.makeText(this, "CSV saved", Toast.LENGTH_SHORT).show() }
                    catch (e: Exception) { Toast.makeText(this, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
                }
            }
            val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
                uri?.let {
                    try { ReportExporter.writePdf(this, it, vm.report.value); Toast.makeText(this, "PDF saved", Toast.LENGTH_SHORT).show() }
                    catch (e: Exception) { Toast.makeText(this, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
                }
            }
            val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            MaterialTheme(colorScheme = scheme, typography = Typography()) {
                val lifecycle = LocalLifecycleOwner.current
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refresh() }
                    lifecycle.lifecycle.addObserver(observer)
                    onDispose { lifecycle.lifecycle.removeObserver(observer) }
                }
                // Only refresh while this UI is in composition; not a hidden background polling service.
                LaunchedEffect(settings.onboarded) {
                    while (true) { delay(45000); vm.refresh() }
                }
                if (!settings.onboarded) {
                    Onboarding(vm, onUsage = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                        onNotification = {
                            if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        })
                } else {
                    ScreenWiseApp(vm, onUsage = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                        onCsv = { csvLauncher.launch("screenwise-report.csv") },
                        onPdf = { pdfLauncher.launch("screenwise-report.pdf") },
                        onNotification = {
                            if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        })
                }
            }
        }
    }
}

@Composable
private fun Onboarding(vm: ScreenWiseViewModel, onUsage: () -> Unit, onNotification: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val access by vm.permission.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(28.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("◉", color = MaterialTheme.colorScheme.primary, fontSize = 26.sp)
            Spacer(Modifier.width(10.dp))
            Text("ScreenWise", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${step + 1} / 4", color = MaterialTheme.colorScheme.primary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(when(step) { 0 -> "Know your screen.\nOwn your time."; 1 -> "Your time,\nyour choice."; 2 -> "Gentle reminders."; else -> "Make it yours." },
                style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, lineHeight = 42.sp)
            Text(when(step) {
                0 -> "A private, local-first view of how your own device is used. No account required."
                1 -> "Android requires you to enable Usage Access manually. Without it, app statistics cannot appear."
                2 -> "Allow notifications to receive optional break and usage-goal reminders. You can change this later."
                else -> "Choose your starting goals. You can refine them any time in Settings."
            }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            if (step == 1) {
                StatusPill(if (access) "Usage Access enabled" else "Usage Access not enabled")
                OutlinedButton(onClick = { onUsage() }) { Icon(Icons.Default.Settings, null); Spacer(Modifier.width(8.dp)); Text("Open Android Usage Access") }
                TextButton(onClick = { vm.refresh() }) { Text("Check permission again") }
            }
            if (step == 2) OutlinedButton(onClick = onNotification) { Text("Request notifications permission") }
            if (step == 3) {
                NumericSetting("Daily goal", settings.dailyGoal, "minutes", 30, 900, 30) { vm.saveGoal(Prefs.DAILY, it) }
                NumericSetting("Social media limit", settings.socialGoal, "minutes", 15, 480, 15) { vm.saveGoal(Prefs.SOCIAL, it) }
                NumericSetting("Gaming limit", settings.gameGoal, "minutes", 15, 480, 15) { vm.saveGoal(Prefs.GAME, it) }
            }
        }
        Column {
            Button(onClick = {
                if (step < 3) step++ else vm.saveBool(Prefs.ONBOARDED, true)
            }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                Text(if (step < 3) "Continue" else "Open my dashboard")
            }
            if (step > 0) TextButton(onClick = { step-- }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Back") }
        }
    }
}

private enum class Tab(val title: String) { Home("Home"), Apps("Apps"), Focus("Focus"), Reports("Reports"), Settings("Settings") }

@Composable
private fun ScreenWiseApp(vm: ScreenWiseViewModel, onUsage: () -> Unit, onCsv: () -> Unit, onPdf: () -> Unit, onNotification: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var selectedApp by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { item ->
                    val icon = when(item) {
                        Tab.Home -> Icons.Default.Home
                        Tab.Apps -> Icons.Default.Apps
                        Tab.Focus -> Icons.Default.Timer
                        Tab.Reports -> Icons.Default.BarChart
                        Tab.Settings -> Icons.Default.Settings
                    }
                    NavigationBarItem(selected = tab == item, onClick = { tab = item; selectedApp = null },
                        icon = { Icon(icon, item.title) }, label = { Text(item.title, fontSize = 10.sp) })
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            when {
                selectedApp != null -> AppDetail(vm, selectedApp!!, onBack = { selectedApp = null })
                tab == Tab.Home -> Home(vm, onUsage, onApps = { tab = Tab.Apps })
                tab == Tab.Apps -> Apps(vm, onSelect = { selectedApp = it })
                tab == Tab.Focus -> Focus(vm)
                tab == Tab.Reports -> Reports(vm, onCsv, onPdf)
                tab == Tab.Settings -> SettingsScreen(vm, onUsage, onNotification)
            }
        }
    }
}

@Composable
private fun Page(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("◉", color = MaterialTheme.colorScheme.primary, fontSize = 25.sp)
            Spacer(Modifier.width(8.dp))
            Text("SCREENWISE", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.height(5.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(22.dp))
        content()
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SoftCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

private fun duration(ms: Long): String {
    val minutes = (ms / 60000).coerceAtLeast(0)
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

@Composable
private fun StatusPill(value: String) {
    Surface(color = Mint.copy(alpha = 0.36f), shape = RoundedCornerShape(18.dp)) {
        Text(value, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Color(0xFF1C5E37), fontSize = 12.sp)
    }
}

@Composable
private fun CircularGoal(progress: Float) {
    Box(Modifier.size(154.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val thickness = 14.dp.toPx()
            drawArc(color = Mint.copy(alpha = 0.34f), startAngle = -90f, sweepAngle = 360f,
                useCenter = false, style = Stroke(thickness, cap = StrokeCap.Round))
            drawArc(color = Fresh, startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false, style = Stroke(thickness, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(progress * 100).roundToInt()}%", fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Text("of daily goal", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState(message: String, onPermission: (() -> Unit)? = null) {
    SoftCard {
        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onPermission != null) Button(onClick = onPermission) { Text("Manage Usage Access") }
    }
}

@Composable
private fun Home(vm: ScreenWiseViewModel, onUsage: () -> Unit, onApps: () -> Unit) {
    val usage by vm.today.collectAsStateWithLifecycle()
    val yesterday by vm.yesterday.collectAsStateWithLifecycle()
    val access by vm.permission.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val focus by vm.focusSessions.collectAsStateWithLifecycle()
    val breaks by vm.breakRecords.collectAsStateWithLifecycle()
    val updatedAt by vm.updatedAt.collectAsStateWithLifecycle()
    val todayDate = LocalDate.now()
    val dateLabel = todayDate.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM"))
    Page("Your digital day", dateLabel) {
        if (!access) { EmptyState("Screen-time data is only visible after you grant Android Usage Access.", onUsage); return@Page }
        SoftCard {
            Text("TOTAL SCREEN TIME", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(duration(usage.totalMs), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    val delta = (usage.totalMs - yesterday.totalMs) / 60000
                    Text(if (yesterday.apps.isEmpty()) "Yesterday comparison unavailable" else "${if (delta > 0) "+" else ""}$delta min vs yesterday",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    Text("Goal ${settings.dailyGoal}m", fontWeight = FontWeight.SemiBold)
                    Text("${(settings.dailyGoal - usage.totalMs / 60000).coerceAtLeast(0)}m remaining", fontSize = 12.sp)
                }
                CircularGoal(if (settings.dailyGoal <= 0) 0f else usage.totalMs.toFloat() / (settings.dailyGoal * 60000f))
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStat("Top app", usage.apps.firstOrNull()?.label ?: "—", Modifier.weight(1f))
            MiniStat("Unlocks", usage.unlocks?.toString() ?: "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStat("Longest session", usage.sessions.maxOfOrNull { it.millis }?.let(::duration) ?: "—", Modifier.weight(1f))
            MiniStat("Breaks today", breaks.count { Instant.ofEpochMilli(it.completedAt).atZone(ZoneId.systemDefault()).toLocalDate() == todayDate }.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        Text("Your activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (usage.detailed) SoftCard { HourlyChart(usage.hourlyMs) }
        else EmptyState(usage.message.ifEmpty { "An hourly timeline is not available from Android for this period." })
        Spacer(Modifier.height(18.dp))
        Text("Daily wellness score", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (usage.apps.isEmpty()) EmptyState("Your wellness score will appear after Android provides real usage data.")
        else SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${vm.score().total}/100", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text("Self-defined habit score; not a health assessment.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            val score = vm.score()
            Text("Screen ${score.screen}/35 · Limits ${score.limits}/25 · Breaks ${score.breaks}/15 · Focus ${score.focus}/15 · Bedtime ${score.bedtime}/10", fontSize = 12.sp)
            Text("Bedtime points remain zero until reliable bedtime tracking is supported.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Most-used apps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onApps) { Text("See all") }
        }
        if (usage.apps.isEmpty()) EmptyState(usage.message.ifEmpty { "No app activity recorded yet." })
        else SoftCard { usage.apps.take(4).forEach { AppLine(it, usage.totalMs) } }
        Text("Last checked: ${if (updatedAt > 0L) time(updatedAt) else "—"}. Android's usage history may be incomplete.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(7.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HourlyChart(hours: List<Long>) {
    val max = hours.maxOrNull()?.coerceAtLeast(1) ?: 1L
    Text("Hourly foreground usage", fontWeight = FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth().height(128.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        hours.forEachIndexed { index, value ->
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.fillMaxWidth().fillMaxHeight((value.toFloat() / max).coerceAtLeast(0.025f))
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(if (index == java.time.LocalTime.now().hour) Fresh else Mint))
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("12 AM", fontSize = 10.sp); Text("6 AM", fontSize = 10.sp)
        Text("12 PM", fontSize = 10.sp); Text("6 PM", fontSize = 10.sp)
    }
}

@Composable
private fun AppLine(app: AppUsage, total: Long, onClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        try { context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap() }
        catch (_: Exception) { null }
    }
    val proportion = if (total <= 0) 0f else app.durationMs.toFloat() / total
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(37.dp).clip(RoundedCornerShape(11.dp)).background(Mint.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.fillMaxSize())
                else Text(app.label.take(1).uppercase(), color = Charcoal, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.category, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(duration(app.durationMs), fontWeight = FontWeight.SemiBold)
            if (onClick != null) Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp))
        }
        LinearProgressIndicator(progress = { proportion }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = Fresh, trackColor = Mint.copy(alpha = 0.35f))
    }
}

@Composable
private fun Apps(vm: ScreenWiseViewModel, onSelect: (String) -> Unit) {
    val report by vm.report.collectAsStateWithLifecycle()
    val access by vm.permission.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val chosen by vm.rangeLabel.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf("All") }
    var phoneEntry by remember { mutableStateOf(false) }
    val callEntries by vm.manualCalls.collectAsStateWithLifecycle()
    Page("App activity", "Understand where your time goes") {
        if (!access) { EmptyState("Enable Usage Access in Settings first."); return@Page }
        RangeButtons(chosen, vm::changeRange)
        Spacer(Modifier.height(16.dp))
        FilterChips(listOf("All") + Categories, category) { category = it }
        Spacer(Modifier.height(16.dp))
        val list = report.apps.map { app -> app.copy(category = categories.firstOrNull { it.packageName == app.packageName }?.category ?: app.category) }
            .filter { category == "All" || it.category == category }
        if (list.isEmpty()) EmptyState(report.message.ifEmpty { "No recorded apps in this category." })
        else SoftCard { list.forEachIndexed { i, app ->
            AppLine(app, report.totalMs, onClick = { onSelect(app.packageName) })
            if (i < list.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        } }
        Spacer(Modifier.height(18.dp))
        SoftCard {
            Text("Phone app usage ≠ call duration", fontWeight = FontWeight.Bold)
            val phone = report.apps.filter { it.packageName.contains("dialer", true) || it.label.contains("phone", true) }.sumOf { it.durationMs }
            Text("Phone / dialer foreground time: ${if (phone > 0) duration(phone) else "Unavailable"}")
            Text("Android does not expose reliable call duration through Usage Access. You may record your own approximate call time.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text("Manual entry today: ${callEntries.firstOrNull { it.date == LocalDate.now().toString() }?.minutes?.toString() ?: "—"} min")
            OutlinedButton(onClick = { phoneEntry = true }) { Text("Enter today's call minutes") }
        }
    }
    if (phoneEntry) {
        var value by rememberSaveable { mutableStateOf("") }
        AlertDialog(onDismissRequest = { phoneEntry = false }, title = { Text("Manual call time") },
            text = { OutlinedTextField(value = value, onValueChange = { value = it.filter(Char::isDigit).take(4) }, label = { Text("Minutes today") }, keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)) },
            confirmButton = { TextButton(onClick = { value.toIntOrNull()?.let { vm.manualCall(LocalDate.now().toString(), it) }; phoneEntry = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { phoneEntry = false }) { Text("Cancel") } })
    }
}

@Composable
private fun RangeButtons(chosen: String, onChoose: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf("Today", "Yesterday", "7 Days", "30 Days").forEach { value ->
            FilterChip(selected = chosen == value, onClick = { onChoose(value) },
                label = { Text(value, fontSize = 10.sp, maxLines = 1) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun FilterChips(options: List<String>, chosen: String, onChoose: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { value -> FilterChip(selected = chosen == value, onClick = { onChoose(value) }, label = { Text(value) }) }
    }
}

@Composable
private fun AppDetail(vm: ScreenWiseViewModel, pkg: String, onBack: () -> Unit) {
    val usage by vm.today.collectAsStateWithLifecycle()
    val limits by vm.limits.collectAsStateWithLifecycle()
    val categoryOverrides by vm.categories.collectAsStateWithLifecycle()
    val week by vm.dailySeries.collectAsStateWithLifecycle()
    val matched = usage.apps.firstOrNull { it.packageName == pkg }
    val limit = limits.firstOrNull { it.packageName == pkg }
    var edit by remember { mutableStateOf(false) }
    var selectCategory by remember { mutableStateOf(false) }
    Page(matched?.label ?: pkg, "Personal app analytics") {
        TextButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null); Text("Back to apps") }
        SoftCard {
            Text("Today", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(duration(matched?.durationMs ?: 0L), fontSize = 35.sp, fontWeight = FontWeight.Bold)
            Text("Sessions: ${matched?.sessionCount?.toString() ?: "Not reliably available"}")
            Text("Daily limit: ${if (limit?.enabled == true) "${limit.minutes} minutes" else "Off"}")
            if (matched != null && limit?.enabled == true) StatusPill(if (matched.durationMs >= limit.minutes * 60000L) "Daily limit exceeded" else "Within daily limit")
            Button(onClick = { edit = true }) { Text("Edit usage limit") }
        }
        Spacer(Modifier.height(16.dp))
        SoftCard {
            Text("Category", fontWeight = FontWeight.Bold)
            Text(categoryOverrides.firstOrNull { it.packageName == pkg }?.category ?: matched?.category ?: "Other")
            OutlinedButton(onClick = { selectCategory = true }) { Text("Change category") }
        }
        Spacer(Modifier.height(16.dp))
        SoftCard {
            Text("Usage trend (available days)", fontWeight = FontWeight.Bold)
            val byDay = week.map { it.copy(totalMs = it.byApp[pkg] ?: 0L) }
            if (byDay.isNotEmpty()) {
                Text("Daily average: ${duration(byDay.sumOf { it.totalMs } / byDay.size)}")
                DailyBars(byDay)
            } else Text("Historical daily records unavailable.")
        }
        Spacer(Modifier.height(16.dp))
        SoftCard {
            Text("Today's recorded intervals", fontWeight = FontWeight.Bold)
            val sessions = usage.sessions.filter { it.packageName == pkg }
            if (sessions.isEmpty()) Text("Detailed sessions are not available for this period.")
            else sessions.takeLast(30).reversed().forEach { s ->
                Text("${time(s.start)}–${time(s.end)}  ·  ${duration(s.millis)}", fontSize = 13.sp)
            }
        }
    }
    if (edit) {
        var minutes by remember(limit) { mutableStateOf((limit?.minutes ?: 45).toString()) }
        var enabled by remember(limit) { mutableStateOf(limit?.enabled ?: true) }
        AlertDialog(onDismissRequest = { edit = false }, title = { Text("Daily app limit") },
            text = { Column {
                OutlinedTextField(value = minutes, onValueChange = { minutes = it.filter(Char::isDigit).take(4) }, label = { Text("Minutes per day") })
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Enable limit"); Switch(checked = enabled, onCheckedChange = { enabled = it }) }
            } },
            confirmButton = { TextButton(onClick = { minutes.toIntOrNull()?.takeIf { it in 1..1440 }?.let { vm.setLimit(pkg, it, enabled) }; edit = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { edit = false }) { Text("Cancel") } })
    }
    if (selectCategory) AlertDialog(onDismissRequest = { selectCategory = false }, title = { Text("Choose category") },
        text = { Column { Categories.forEach { c -> TextButton(onClick = { vm.setCategory(pkg, c); selectCategory = false }) { Text(c) } } } },
        confirmButton = { TextButton(onClick = { selectCategory = false }) { Text("Close") } })
}

private fun time(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("hh:mm a"))

@Composable
private fun Focus(vm: ScreenWiseViewModel) {
    val sessions by vm.focusSessions.collectAsStateWithLifecycle()
    val breaks by vm.breakRecords.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var focusMinutes by rememberSaveable { mutableIntStateOf(25) }
    var mode by rememberSaveable { mutableStateOf("Study") }
    var finishAt by rememberSaveable { mutableLongStateOf(0L) }
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var breakFinishAt by rememberSaveable { mutableLongStateOf(0L) }
    var breakDone by rememberSaveable { mutableStateOf(false) }
    var nowElapsed by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(finishAt, breakFinishAt) {
        while (finishAt > 0 || breakFinishAt > 0) {
            nowElapsed = android.os.SystemClock.elapsedRealtime()
            if (finishAt > 0 && nowElapsed >= finishAt && !finished) {
                vm.recordFocus(startedAt, focusMinutes, mode, true)
                finished = true
            }
            delay(1000)
        }
    }
    val remaining = ((finishAt - nowElapsed) / 1000).coerceAtLeast(0)
    val breakRemaining = ((breakFinishAt - nowElapsed) / 1000).coerceAtLeast(0)
    Page("Focus, gently.", "Create some space for what matters") {
        SoftCard {
            StatusPill(if (finishAt > 0 && !finished) "FOCUS IN PROGRESS" else "YOUR FOCUS SPACE")
            Text("${remaining / 60}:${(remaining % 60).toString().padStart(2,'0')}", fontSize = 55.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(if (finished) "Session complete. Nice work." else "Choose a routine, then press start.",
                modifier = Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(25, 45, 60).forEach { m -> FilterChip(selected = focusMinutes == m, onClick = { if (finishAt == 0L) focusMinutes = m }, label = { Text("${m}m") }) }
            }
            FilterChips(listOf("Study", "Work", "Personal"), mode) { if (finishAt == 0L) mode = it }
            if (finishAt == 0L || finished) {
                Button(onClick = {
                    startedAt = System.currentTimeMillis()
                    finishAt = android.os.SystemClock.elapsedRealtime() + focusMinutes * 60000L
                    nowElapsed = android.os.SystemClock.elapsedRealtime()
                    finished = false
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PlayArrow, null); Text("Start focus") }
                if (finished) TextButton(onClick = { finishAt = 0 }) { Text("Reset") }
            } else OutlinedButton(onClick = {
                vm.recordFocus(startedAt, focusMinutes, mode, false)
                finishAt = 0L
            }, modifier = Modifier.fillMaxWidth()) { Text("End session early") }
            Text("Timer timing is reliable while the app is running. System battery restrictions can delay background alerts.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(18.dp))
        SoftCard {
            Text("Time for a break", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Rest your eyes, stretch, or drink some water. These are optional general wellbeing suggestions.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val activeBreak = breakFinishAt > 0L
            if (activeBreak) Text("${breakRemaining / 60}:${(breakRemaining % 60).toString().padStart(2,'0')} remaining", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (!activeBreak) Button(onClick = {
                breakFinishAt = android.os.SystemClock.elapsedRealtime() + settings.breakDuration * 60000L
                nowElapsed = android.os.SystemClock.elapsedRealtime()
                breakDone = false
            }) { Text("Start ${settings.breakDuration}-minute break") }
            else if (breakRemaining == 0L && !breakDone) Button(onClick = {
                vm.recordBreak(settings.breakDuration); breakDone = true; breakFinishAt = 0L
            }) { Text("Complete break") }
            else if (activeBreak) TextButton(onClick = { breakFinishAt = 0L }) { Text("Skip break") }
            Text("Completed breaks: ${breaks.size} total", fontSize = 12.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text("Recent sessions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (sessions.isEmpty()) EmptyState("No focus sessions yet. Try a 25-minute study session.")
        else SoftCard { sessions.take(7).forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (s.completed) Icons.Default.CheckCircle else Icons.Default.Cancel, null,
                    tint = if (s.completed) Fresh else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Column { Text("${s.mode} · ${s.plannedMinutes}m planned"); Text("${time(s.startedAt)} · ${if (s.completed) "Completed" else "Ended early"}", fontSize = 11.sp) }
            }
        } }
    }
}

@Composable
private fun Reports(vm: ScreenWiseViewModel, onCsv: () -> Unit, onPdf: () -> Unit) {
    val report by vm.report.collectAsStateWithLifecycle()
    val chosen by vm.rangeLabel.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val series by vm.dailySeries.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val categoryOverrides by vm.categories.collectAsStateWithLifecycle()
    val access by vm.permission.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var from by remember { mutableStateOf(LocalDate.now().minusDays(6)) }
    var to by remember { mutableStateOf(LocalDate.now()) }
    fun pickDate(start: Boolean) {
        val initial = if (start) from else to
        DatePickerDialog(context, { _, year, month, day ->
            val selected = LocalDate.of(year, month + 1, day)
            if (start) from = selected else to = selected
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }
    Page("Your reports", "Your habits, made understandable") {
        if (!access) { EmptyState("Grant Usage Access to generate reports."); return@Page }
        RangeButtons(chosen, vm::changeRange)
        Spacer(Modifier.height(15.dp))
        SoftCard {
            Text("Custom date range", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate(true) }, modifier = Modifier.weight(1f)) { Text(from.toString(), fontSize = 11.sp) }
                OutlinedButton(onClick = { pickDate(false) }, modifier = Modifier.weight(1f)) { Text(to.toString(), fontSize = 11.sp) }
            }
            Button(onClick = { vm.custom(from, to) }, enabled = !from.isAfter(to)) { Text("Generate report") }
        }
        Spacer(Modifier.height(14.dp))
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        SoftCard {
            Text("TRACKED APP TIME", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(duration(report.totalMs), fontSize = 35.sp, fontWeight = FontWeight.Bold)
            Text("${report.apps.size} apps · ${chosen.lowercase()} report", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Figures come from the device's available OS history; longer periods may use coarse aggregates.", fontSize = 11.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text("Daily progress", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        SoftCard {
            if (series.isEmpty()) Text("No daily history available.")
            else {
                val positive = series.count { it.totalMs > 0 }
                Text("Average: ${duration(series.sumOf { it.totalMs } / series.size)} per day")
                Text("Goal days: ${series.count { it.totalMs > 0 && it.totalMs / 60000 <= settings.dailyGoal }} / $positive with recorded activity",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                DailyBars(series)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Category breakdown", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        val grouped = report.apps.groupBy { app -> categoryOverrides.firstOrNull { it.packageName == app.packageName }?.category ?: app.category }
            .mapValues { (_, apps) -> apps.sumOf { it.durationMs } }.toList().sortedByDescending { it.second }
        if (grouped.isEmpty()) EmptyState(report.message.ifEmpty { "No OS usage data for this period." })
        else SoftCard { grouped.forEach { (name, ms) ->
            Row { Text(name, modifier = Modifier.weight(1f)); Text(duration(ms), fontWeight = FontWeight.Bold) }
            LinearProgressIndicator(progress = { if (report.totalMs == 0L) 0f else ms.toFloat() / report.totalMs },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = Fresh)
        } }
        Spacer(Modifier.height(18.dp))
        Text("Top applications", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        if (report.apps.isNotEmpty()) SoftCard { report.apps.take(10).forEach { AppLine(it, report.totalMs) } }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onPdf, enabled = !busy && report.to > report.from, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(5.dp)); Text("Save PDF") }
            OutlinedButton(onClick = onCsv, enabled = !busy && report.to > report.from, modifier = Modifier.weight(1f)) { Icon(Icons.Default.TableChart, null); Spacer(Modifier.width(5.dp)); Text("Save CSV") }
        }
        Text("Reports are written locally via Android's file picker. No upload occurs.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun NumericSetting(label: String, value: Int, unit: String, min: Int, max: Int, step: Int, onValue: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Medium)
                Text("$value $unit", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onValue((value - step).coerceAtLeast(min)) }, enabled = value > min) { Icon(Icons.Default.RemoveCircleOutline, "Decrease") }
            IconButton(onClick = { onValue((value + step).coerceAtMost(max)) }, enabled = value < max) { Icon(Icons.Default.AddCircleOutline, "Increase") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }
}

@Composable
private fun <T> SelectSetting(label: String, value: T, options: List<Pair<T,String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${options.firstOrNull { it.first == value }?.second ?: value.toString()}", modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (val0, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { onSelect(val0); expanded = false }) }
        }
    }
}

@Composable
private fun SettingsScreen(vm: ScreenWiseViewModel, onUsage: () -> Unit, onNotification: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val access by vm.permission.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var aboutPrivacy by remember { mutableStateOf(false) }
    Page("Your preferences", "Everything stays under your control") {
        SoftCard {
            Text("Appearance & language", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SelectSetting("Theme", settings.theme, listOf("system" to "System", "light" to "Light", "dark" to "Dark")) { vm.saveString(Prefs.THEME, it) }
            SelectSetting("Language", settings.language, listOf("en" to "English", "hi" to "हिन्दी")) { vm.saveString(Prefs.LANGUAGE, it) }
            if (settings.language == "hi") Text("हिन्दी विकल्प चुना गया। कुछ स्क्रीन अभी अंग्रेज़ी में उपलब्ध हैं।", fontSize = 11.sp)
        }
        Spacer(Modifier.height(14.dp))
        SoftCard {
            Text("Goals & breaks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            NumericSetting("Daily screen goal", settings.dailyGoal, "minutes", 30, 900, 30) { vm.saveGoal(Prefs.DAILY, it) }
            NumericSetting("Social media goal", settings.socialGoal, "minutes", 15, 480, 15) { vm.saveGoal(Prefs.SOCIAL, it) }
            NumericSetting("Gaming goal", settings.gameGoal, "minutes", 15, 480, 15) { vm.saveGoal(Prefs.GAME, it) }
            NumericSetting("Remind after", settings.breakEvery, "minutes", 15, 180, 15) { vm.saveGoal(Prefs.BREAK_EVERY, it) }
            NumericSetting("Break length", settings.breakDuration, "minutes", 5, 30, 5) { vm.saveGoal(Prefs.BREAK_DURATION, it) }
            NumericSetting("Bedtime target", settings.bedtime, "hour (24h)", 0, 23, 1) { vm.saveGoal(Prefs.BEDTIME, it) }
        }
        Spacer(Modifier.height(14.dp))
        SoftCard {
            Text("Reminders & access", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enable reminders", modifier = Modifier.weight(1f))
                Switch(checked = settings.notifications, onCheckedChange = { vm.saveBool(Prefs.NOTIFY, it) })
            }
            NumericSetting("Quiet hours start", settings.quietStart, "hour (24h)", 0, 23, 1) { vm.saveGoal(Prefs.QUIET_START, it) }
            NumericSetting("Quiet hours end", settings.quietEnd, "hour (24h)", 0, 23, 1) { vm.saveGoal(Prefs.QUIET_END, it) }
            Text("Usage permission: ${if (access) "Granted" else "Unavailable"}")
            OutlinedButton(onClick = onUsage) { Text("Manage Usage Access") }
            OutlinedButton(onClick = onNotification) { Text("Request notification permission") }
        }
        Spacer(Modifier.height(14.dp))
        SoftCard {
            Text("Data & privacy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            NumericSetting("Local records retention", settings.retentionDays, "days", 7, 365, 7) { vm.saveGoal(Prefs.RETENTION, it) }
            Text("This controls app-created focus/break records. Android manages its own usage history independently.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { aboutPrivacy = true }) { Text("Privacy & data use") }
            OutlinedButton(onClick = { vm.saveBool(Prefs.ONBOARDED, false) }) { Text("Restart onboarding") }
            TextButton(onClick = { confirmDelete = true }) { Text("Delete all ScreenWise local data", color = MaterialTheme.colorScheme.error) }
        }
        Text("ScreenWise 0.1.0 · Offline by design", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete local app data?") },
        text = { Text("This permanently removes ScreenWise settings, focus history, break entries, app limits, and manual call entries. Android's system-owned usage history is not deleted.") },
        confirmButton = { TextButton(onClick = { vm.resetData(); confirmDelete = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
    if (aboutPrivacy) AlertDialog(onDismissRequest = { aboutPrivacy = false }, title = { Text("Privacy first") },
        text = { Text("ScreenWise requests Usage Access to read Android's available on-device app usage history. It does not upload this data, capture messages, record calls, use location, or access your microphone. You control notifications and can delete all app-created data. Android retains its own system usage data independently.") },
        confirmButton = { TextButton(onClick = { aboutPrivacy = false }) { Text("Close") } })
}


@Composable
private fun DailyBars(series: List<DayUsage>) {
    val high = series.maxOfOrNull { it.totalMs }?.coerceAtLeast(1) ?: 1L
    Row(Modifier.fillMaxWidth().height(130.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        series.forEach { item ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(Modifier.fillMaxWidth().fillMaxHeight((item.totalMs.toFloat() / high).coerceIn(0.025f, 1f))
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(if (item.date == LocalDate.now()) Fresh else Mint))
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(series.firstOrNull()?.date?.toString() ?: "", fontSize = 10.sp)
        Text(series.lastOrNull()?.date?.toString() ?: "", fontSize = 10.sp)
    }
}
