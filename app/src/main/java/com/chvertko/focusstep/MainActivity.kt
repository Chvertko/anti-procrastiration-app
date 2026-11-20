package com.chvertko.focusstep

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chvertko.focusstep.data.PinCode
import com.chvertko.focusstep.data.QuestionRepository
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val resumeState = mutableIntStateOf(0)
    private var continueSetupAfterNotification = false
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && continueSetupAfterNotification) openAccessibilitySettings(this)
        continueSetupAfterNotification = false
    }

    fun continueProtectionSetup() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            continueSetupAfterNotification = true
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openAccessibilitySettings(this)
        }
    }

    fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            continueSetupAfterNotification = false
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        AppPrefs.restoreInterruptedAudioMute(this)
        Telemetry.applyConsent(this)
        setContent { FocusStepApp(resumeState.intValue) }
    }

    override fun onResume() {
        super.onResume()
        ProtectionStatusService.ensureRunning(this)
        com.chvertko.focusstep.widget.FocusStepWidgetProvider.updateAll(this)
        resumeState.intValue++
    }
}

@Composable
private fun FocusStepApp(refreshKey: Int) {
    val context = LocalContext.current
    var page by remember { mutableStateOf("home") }
    var verifyPin by remember { mutableStateOf(false) }
    var homeRefresh by remember { mutableIntStateOf(0) }
    val uiScope = rememberCoroutineScope()
    MaterialTheme(colorScheme = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF8DE0C1),
        secondary = androidx.compose.ui.graphics.Color(0xFFF4BD78),
        onPrimary = androidx.compose.ui.graphics.Color(0xFF102019),
        secondaryContainer = androidx.compose.ui.graphics.Color(0xFF244D40),
        onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFB9F2DC),
        background = androidx.compose.ui.graphics.Color(0xFF101512),
        surface = androidx.compose.ui.graphics.Color(0xFF19211D),
    )) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (page == "settings") {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.navigationBarsPadding()) {
                        Button(onClick = { page = "home"; homeRefresh++ }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                            Text("Готово")
                        }
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("home" to "Сегодня", "questions" to "Вопросы", "settings" to "Настройки").forEach { (key, label) ->
                        FilterChip(selected = page == key, onClick = {
                            Telemetry.event(context, "navigation_selected", mapOf("destination" to key))
                            if (key == "settings" && PinCode.isSet(context)) verifyPin = true else page = key
                        }, label = { Text(label) })
                    }
                }
                when (page) {
                    "questions" -> QuestionsPage()
                    "settings" -> SettingsPage(onDone = { page = "home"; homeRefresh++ })
                    else -> HomePage(
                        refreshKey + homeRefresh,
                        onSettings = { if (PinCode.isSet(context)) verifyPin = true else page = "settings" },
                        onDisabled = {
                            homeRefresh++
                            uiScope.launch { delay(750); homeRefresh++ }
                        },
                    )
                }
            }
        }
        if (verifyPin) PinVerifyDialog(
            onVerified = { verifyPin = false; page = "settings" },
            onCancel = { verifyPin = false },
        )
    }
}

@Composable
private fun HomePage(refreshKey: Int, onSettings: () -> Unit, onDisabled: () -> Unit) {
    val context = LocalContext.current
    var showDisclosure by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showDisablePinDialog by remember { mutableStateOf(false) }
    var showDisableConfirmation by remember { mutableStateOf(false) }
    var readiness by remember(refreshKey) { mutableStateOf(ProtectionReadinessChecker.check(context)) }
    var showXiaomiGuide by remember { mutableStateOf(false) }
    val apps = remember(refreshKey) { AppPrefs.selectedApps(context) }
    val stats = remember(refreshKey) { AppPrefs.dailyStats(context) }
    val serviceEnabled = readiness.isReady

    LaunchedEffect(refreshKey) {
        repeat(10) {
            readiness = ProtectionReadinessChecker.check(context)
            if (readiness.isReady) return@LaunchedEffect
            delay(2_000L)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ElevatedCard {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.ic_focusstep), contentDescription = "FocusStep", modifier = Modifier.size(52.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("FocusStep", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (serviceEnabled) "Защита работает · ${apps.size} приложений" else "Пауза перед выбранными приложениями", style = MaterialTheme.typography.bodySmall)
                    Text("Сегодня: ${stats.attempts} попыток · ${stats.completed} заданий · ${stats.waits} пауз", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        ElevatedCard(colors = CardDefaults.elevatedCardColors(
            containerColor = if (serviceEnabled) androidx.compose.ui.graphics.Color(0xFF173A2A) else MaterialTheme.colorScheme.surface,
        )) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    when {
                        serviceEnabled -> "Защита активна"
                        apps.isEmpty() -> "Выбери приложения"
                        readiness.accessibilityEnabled && !readiness.serviceResponsive -> "Служба не отвечает"
                        else -> "Нужна настройка"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    when {
                        serviceEnabled -> "Служба работает в фоне. Выбрано приложений: ${apps.size}."
                        apps.isEmpty() -> "Сначала выбери приложения, для которых нужна пауза."
                        else -> "Чтобы защита работала постоянно, выполни все пункты ниже."
                    },
                )
                if (!serviceEnabled) readiness.missing.forEach { item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("• $item", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            when {
                                item.contains("приложение") -> onSettings()
                                item.contains("PIN") -> showPinDialog = true
                                item.contains("специальных") || item.contains("не отвечает") -> {
                                    AppPrefs.setProtectionRequested(context, true)
                                    showDisclosure = true
                                }
                                item.contains("уведомлен") -> (context as? MainActivity)?.requestNotificationPermission()
                                item.contains("батареи") -> runCatching {
                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                                else -> showXiaomiGuide = true
                            }
                        }) { Text("Настроить") }
                    }
                }
                Button(onClick = {
                    if (serviceEnabled) {
                        if (PinCode.isSet(context)) showDisablePinDialog = true else showPinDialog = true
                    } else when {
                        apps.isEmpty() -> onSettings()
                        !PinCode.isSet(context) -> showPinDialog = true
                        !readiness.accessibilityEnabled -> {
                            AppPrefs.setProtectionRequested(context, true)
                            com.chvertko.focusstep.widget.FocusStepWidgetProvider.updateAll(context)
                            showDisclosure = true
                        }
                        !readiness.notificationsAllowed -> (context as? MainActivity)?.requestNotificationPermission()
                        !readiness.batteryUnrestricted -> runCatching {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        readiness.isXiaomi && (!readiness.autostartConfirmed || !readiness.recentsLockConfirmed) -> showXiaomiGuide = true
                        else -> openAccessibilitySettings(context)
                    }
                }, colors = if (serviceEnabled) ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFFB23A48),
                    contentColor = androidx.compose.ui.graphics.Color.White,
                ) else ButtonDefaults.buttonColors()) {
                    Text(when {
                        serviceEnabled -> "Отключить защиту"
                        apps.isEmpty() -> "Выбрать приложения"
                        !PinCode.isSet(context) -> "Задать PIN"
                        !readiness.accessibilityEnabled -> "Продолжить настройку"
                        !readiness.notificationsAllowed -> "Разрешить уведомления"
                        !readiness.batteryUnrestricted -> "Настроить батарею"
                        readiness.isXiaomi && (!readiness.autostartConfirmed || !readiness.recentsLockConfirmed) -> "Настроить работу в фоне"
                        else -> "Проверить настройки"
                    })
                }
                if (serviceEnabled) Text("Для отключения нужны PIN и подтверждение.", style = MaterialTheme.typography.bodySmall)
                if (serviceEnabled) Text("После ответа доступ длится ${AppPrefs.accessMinutes(context)} мин. Обратный отсчёт виден в уведомлении FocusStep.", style = MaterialTheme.typography.bodySmall)
            }
        }
        ElevatedCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Сегодня", style = MaterialTheme.typography.titleLarge)
                Text("Попыток открыть выбранные приложения: ${stats.attempts}")
                Text("Вопросов решено: ${stats.completed}")
                Text("Паузы ожидания завершены: ${stats.waits}")
                Text("Экранного времени сэкономлено: ${stats.savedSeconds / 60} мин")
                Text("Оценка времени, когда ты отказался от открытия выбранного приложения после паузы.", style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(onClick = { showPinDialog = true }) { Text(if (PinCode.isSet(context)) "Изменить PIN" else "Задать PIN для защитных настроек") }
        TextButton(onClick = onSettings) { Text("Настройки и вопросы") }
        Text("Учебный прототип. Android позволяет отключить этот сервис или удалить приложение.", style = MaterialTheme.typography.bodySmall)
    }

    if (showDisclosure) AlertDialog(
        onDismissRequest = { showDisclosure = false },
        title = { Text("Почему нужен доступ?") },
        text = { Text("FocusStep использует службу специальных возможностей только для выбранных приложений. На следующем экране выбери FocusStep, включи переключатель и подтверди системный запрос. Android не разрешает приложению включить этот доступ за тебя. Содержимое экрана и вводимые данные не читаются и не отправляются.") },
        confirmButton = { Button(onClick = {
            showDisclosure = false
            (context as? MainActivity)?.continueProtectionSetup() ?: openAccessibilitySettings(context)
        }) { Text("Продолжить") } },
        dismissButton = { TextButton(onClick = { showDisclosure = false }) { Text("Отмена") } },
    )
    if (showXiaomiGuide) AlertDialog(
        onDismissRequest = { showXiaomiGuide = false },
        title = { Text("Фоновая работа Xiaomi") },
        text = { Text("В настройках Xiaomi разреши FocusStep фоновый автозапуск и режим батареи «Без ограничений». В списке последних приложений закрепи FocusStep замком. Вернись сюда после настройки и отметь оба пункта. Xiaomi может завершать приложения через очистку памяти.") },
        confirmButton = { Button(onClick = {
            AppPrefs.setBackgroundAutostartConfirmed(context, true)
            AppPrefs.setRecentsLockConfirmed(context, true)
            showXiaomiGuide = false
            readiness = ProtectionReadinessChecker.check(context)
        }) { Text("Настроил оба пункта") } },
        dismissButton = { TextButton(onClick = { showXiaomiGuide = false }) { Text("Позже") } },
    )
    if (showPinDialog) PinSetupDialog(onDone = { showPinDialog = false; if (serviceEnabled) showDisablePinDialog = true }, onCancel = { showPinDialog = false })
    if (showDisablePinDialog) PinVerifyDialog(
        prompt = "Введи PIN, чтобы отключить защиту.",
        onVerified = { showDisablePinDialog = false; showDisableConfirmation = true },
        onCancel = { showDisablePinDialog = false },
    )
    if (showDisableConfirmation) AlertDialog(
        onDismissRequest = { showDisableConfirmation = false },
        title = { Text("Отключить защиту?") },
        text = { Text("FocusStep очистит выбранные приложения и временные разрешения, выключит системную службу и снимет индикатор защиты.") },
        confirmButton = { Button(onClick = {
            AppPrefs.clearProtectionSettings(context)
            context.sendBroadcast(Intent(com.chvertko.focusstep.monitor.AppMonitorService.ACTION_DISABLE_PROTECTION).setPackage(context.packageName))
            showDisableConfirmation = false
            onDisabled()
        }, colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFB94B43))) { Text("Отключить всё") } },
        dismissButton = { TextButton(onClick = { showDisableConfirmation = false }) { Text("Отмена") } },
    )
}

private fun openAccessibilitySettings(context: Context) {
    val component = ComponentName(context, com.chvertko.focusstep.monitor.AppMonitorService::class.java)
    val detail = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
        .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
    val opened = runCatching { context.startActivity(detail) }.isSuccess
    if (!opened) runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
}

@Composable
private fun QuestionsPage() {
    val context = LocalContext.current
    var questions by remember { mutableStateOf(QuestionRepository.load(context)) }
    var category by remember { mutableStateOf("all") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "Все", "javascript" to "JS", "typescript" to "TS", "web" to "Web").forEach { (id, label) ->
                FilterChip(selected = category == id, onClick = { category = id }, label = { Text(label) })
            }
        }
        Text("${questions.count { category == "all" || it.category == category }} вопросов в каталоге", modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(questions.filter { category == "all" || it.category == category }, key = { it.id }) { q ->
                ElevatedCard {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(q.category.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(q.prompt, fontWeight = FontWeight.SemiBold)
                        q.options.forEachIndexed { index, answer -> Text("${'A' + index}. $answer", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(onDone: () -> Unit) {
    val context = LocalContext.current
    var wait by remember { mutableIntStateOf(AppPrefs.waitMinutes(context)) }
    var url by remember { mutableStateOf(AppPrefs.remoteUrl(context)) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(AppPrefs.selectedApps(context)) }
    var showWidgetStats by remember { mutableStateOf(AppPrefs.widgetStatsVisible(context)) }
    var accessMinutes by remember { mutableIntStateOf(AppPrefs.accessMinutes(context)) }
    val apps = remember { launcherApps(context) }
    var appSearch by remember { mutableStateOf("") }
    var telemetryEnabled by remember { mutableStateOf(Telemetry.isEnabled(context)) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Правила", style = MaterialTheme.typography.headlineSmall)
        if (BuildConfig.FIREBASE_CONFIGURED) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Диагностика beta")
                    Text("Отправлять события использования и отчёты о сбоях. Названия приложений, ответы и PIN не отправляются.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = telemetryEnabled, onCheckedChange = { enabled ->
                    telemetryEnabled = enabled
                    Telemetry.setEnabled(context, enabled)
                })
            }
        } else {
            Text("Сбор метрик пока не настроен: Firebase конфигурация не добавлена в эту сборку.", style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        ElevatedCard {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Выбрано для защиты · ${selected.size}", style = MaterialTheme.typography.titleMedium)
                val chosen = apps.filter { it.first in selected }
                if (chosen.isEmpty()) Text("Пока ничего не выбрано.", style = MaterialTheme.typography.bodySmall)
                chosen.forEach { (packageName, label) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            selected = selected - packageName
                            AppPrefs.setSelectedApps(context, selected)
                            com.chvertko.focusstep.widget.FocusStepWidgetProvider.updateAll(context)
                        }) { Text("Убрать") }
                    }
                }
            }
        }
        ElevatedCard {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Виджет FocusStep", style = MaterialTheme.typography.titleMedium)
                Text("Плашка показывает значок приложения, состояние защиты и статистику за день.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Показывать статистику", modifier = Modifier.weight(1f))
                    Switch(checked = showWidgetStats, onCheckedChange = {
                        showWidgetStats = it
                        AppPrefs.setWidgetStatsVisible(context, it)
                        com.chvertko.focusstep.widget.FocusStepWidgetProvider.updateAll(context)
                    })
                }
                OutlinedButton(onClick = {
                    val manager = android.appwidget.AppWidgetManager.getInstance(context)
                    val provider = ComponentName(context, com.chvertko.focusstep.widget.FocusStepWidgetProvider::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported) {
                        val sent = runCatching { manager.requestPinAppWidget(provider, null, null) }.getOrDefault(false)
                        message = if (sent) "Подтверди добавление плашки на рабочий экран." else "Добавь FocusStep через список виджетов лаунчера."
                    } else message = "Добавь FocusStep через список виджетов лаунчера."
                }, modifier = Modifier.fillMaxWidth()) { Text("Добавить плашку на экран") }
            }
        }
        Text("Добавить и убрать приложения", style = MaterialTheme.typography.titleMedium)
        Text("Выбери приложения. При их открытии появится вопрос или таймер.")
        OutlinedTextField(appSearch, { appSearch = it }, label = { Text("Поиск приложений") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        val filteredApps = apps.filter { it.second.contains(appSearch, ignoreCase = true) || it.first.contains(appSearch, ignoreCase = true) }
        if (filteredApps.isEmpty()) Text("Приложения не найдены.", style = MaterialTheme.typography.bodySmall)
        filteredApps.forEach { app ->
            val checked = app.first in selected
            Row(Modifier.fillMaxWidth().clickable {
                selected = if (checked) selected - app.first else selected + app.first
                AppPrefs.setSelectedApps(context, selected)
                com.chvertko.focusstep.widget.FocusStepWidgetProvider.updateAll(context)
                Telemetry.event(context, "monitored_app_selection_changed", mapOf("selected" to (!checked).toString(), "selected_count" to selected.size.toString()))
            }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = { value ->
                    selected = if (value) selected + app.first else selected - app.first
                    AppPrefs.setSelectedApps(context, selected)
                    com.chvertko.focusstep.widget.FocusStepWidgetProvider.updateAll(context)
                    Telemetry.event(context, "monitored_app_selection_changed", mapOf("selected" to value.toString(), "selected_count" to selected.size.toString()))
                })
                Column { Text(app.second); Text(app.first, style = MaterialTheme.typography.labelSmall) }
            }
        }
        HorizontalDivider()
        Text("Ожидание при пропуске: $wait мин")
        Slider(value = wait.toFloat(), onValueChange = { wait = it.toInt().coerceIn(2, 5); AppPrefs.setWaitMinutes(context, wait) }, valueRange = 2f..5f, steps = 2)
        Text("Доступ после ответа: $accessMinutes мин")
        Text("По окончании отсчёта вопрос снова появится, если ты всё ещё в выбранном приложении.", style = MaterialTheme.typography.bodySmall)
        Slider(value = accessMinutes.toFloat(), onValueChange = {
            accessMinutes = (it.toInt() / 5 * 5).coerceIn(5, 30)
            AppPrefs.setAccessMinutes(context, accessMinutes)
        }, valueRange = 5f..30f, steps = 4)
        if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            OutlinedButton(onClick = { (context as? MainActivity)?.requestNotificationPermission() }) {
                Text("Включить уведомление с таймером")
            }
        }
        HorizontalDivider()
        Text("Импорт вопросов из JSON по HTTPS", style = MaterialTheme.typography.titleMedium)
        Text("Файл должен содержать schemaVersion: 1 и массив questions. JSON проверяется перед заменой встроенного каталога.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = url, onValueChange = { url = it; AppPrefs.setRemoteUrl(context, it) }, label = { Text("HTTPS URL файла JSON") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && url.isNotBlank(), onClick = {
            busy = true; message = "Загружаю…"
            val address = url
            val mainHandler = Handler(Looper.getMainLooper())
            Thread {
                try {
                    val count = QuestionRepository.updateFromHttps(context, address)
                    Telemetry.event(context, "question_pack_import", mapOf("result" to "success", "question_count" to count.toString()))
                    mainHandler.post { message = "Загружено вопросов: $count"; busy = false }
                } catch (e: Exception) {
                    Telemetry.event(context, "question_pack_import", mapOf("result" to "failure"))
                    Telemetry.error(context, "question_pack_import", e)
                    mainHandler.post { message = e.message ?: "Не удалось загрузить JSON."; busy = false }
                }
            }.start()
        }) { Text(if (busy) "Загрузка…" else "Обновить каталог") }
        TextButton(onClick = {
            QuestionRepository.resetToBundled(context)
            Telemetry.event(context, "question_pack_reset")
            message = "Восстановлен встроенный каталог вопросов."
        }) { Text("Вернуть встроенный каталог") }
        if (message.isNotBlank()) Text(message)
        Text("Встроенный каталог содержит 124 вопроса и работает офлайн. Обновлённый JSON хранится только на этом устройстве.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PinSetupDialog(onDone: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var oldPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("PIN защитных настроек") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Попроси доверенного человека придумать PIN и ввести его здесь. Он затрудняет импульсивные изменения, но не мешает отключить сервис в Android или удалить приложение.")
                if (PinCode.isSet(context)) OutlinedTextField(oldPin, { oldPin = it.filter { c -> c.isDigit() }.take(12) }, label = { Text("Текущий PIN") })
                OutlinedTextField(pin, { pin = it.filter { c -> c.isDigit() }.take(12) }, label = { Text("Новый PIN (4–12 цифр)") })
                OutlinedTextField(confirm, { confirm = it.filter { c -> c.isDigit() }.take(12) }, label = { Text("Повтори PIN") })
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { Button(onClick = {
            if (PinCode.isSet(context) && !PinCode.verify(context, oldPin)) error = "Текущий PIN неверен."
            else if (pin.length < 4) error = "Нужно минимум 4 цифры."
            else if (pin != confirm) error = "PIN-коды не совпадают."
            else { PinCode.save(context, pin); onDone() }
        }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Отмена") } },
    )
}

@Composable
private fun PinVerifyDialog(prompt: String = "Введи PIN, чтобы изменить список приложений и правила.", onVerified: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Защитные настройки") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(prompt)
                OutlinedTextField(pin, { pin = it.filter { c -> c.isDigit() }.take(12) }, label = { Text("PIN") })
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { Button(onClick = { if (PinCode.verify(context, pin)) onVerified() else error = "PIN не подходит." }) { Text("Продолжить") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Отмена") } },
    )
}

private fun launcherApps(context: Context): List<Pair<String, String>> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return context.packageManager.queryIntentActivities(intent, 0)
        .mapNotNull { info -> info.activityInfo?.let { it.packageName to (info.loadLabel(context.packageManager)?.toString() ?: it.packageName) } }
        .filter { it.first != context.packageName }
        .distinctBy { it.first }
        .sortedBy { it.second.lowercase(Locale.ROOT) }
}

private fun isMonitorEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val expected = ComponentName(context, com.chvertko.focusstep.monitor.AppMonitorService::class.java)
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == expected.packageName && it.resolveInfo.serviceInfo.name == expected.className }
}
