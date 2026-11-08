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
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chvertko.focusstep.data.PinCode
import com.chvertko.focusstep.data.QuestionRepository
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val resumeState = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FocusStepApp(resumeState.intValue) }
    }

    override fun onResume() {
        super.onResume()
        resumeState.intValue++
    }
}

@Composable
private fun FocusStepApp(refreshKey: Int) {
    val context = LocalContext.current
    var page by remember { mutableStateOf("home") }
    var verifyPin by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF8DE0C1),
        secondary = androidx.compose.ui.graphics.Color(0xFFF4BD78),
        background = androidx.compose.ui.graphics.Color(0xFF101512),
        surface = androidx.compose.ui.graphics.Color(0xFF19211D),
    )) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("home" to "Сегодня", "questions" to "Вопросы", "settings" to "Настройки").forEach { (key, label) ->
                        FilterChip(selected = page == key, onClick = {
                            if (key == "settings" && PinCode.isSet(context)) verifyPin = true else page = key
                        }, label = { Text(label) })
                    }
                }
                when (page) {
                    "questions" -> QuestionsPage()
                    "settings" -> SettingsPage()
                    else -> HomePage(refreshKey, onSettings = { if (PinCode.isSet(context)) verifyPin = true else page = "settings" })
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
private fun HomePage(refreshKey: Int, onSettings: () -> Unit) {
    val context = LocalContext.current
    var showDisclosure by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    val serviceEnabled = remember(refreshKey) { isMonitorEnabled(context) }
    val apps = remember(refreshKey) { AppPrefs.selectedApps(context) }
    val stats = remember(refreshKey) { AppPrefs.dailyStats(context) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("FocusStep", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Сделай короткую паузу перед лентой.", style = MaterialTheme.typography.bodyLarge)
        ElevatedCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (serviceEnabled && apps.isNotEmpty()) "Защита активна" else "Нужна настройка", style = MaterialTheme.typography.titleLarge)
                Text(if (serviceEnabled) "Сервис включён. Выбрано приложений: ${apps.size}." else "Выбери приложения и включи доступ FocusStep в настройках специальных возможностей.")
                Button(onClick = { if (apps.isEmpty()) onSettings() else showDisclosure = true }, enabled = !serviceEnabled) { Text(if (apps.isEmpty()) "Выбрать приложения" else "Включить защиту") }
                if (serviceEnabled) Text("Отключение доступно в системных настройках Android.", style = MaterialTheme.typography.bodySmall)
            }
        }
        ElevatedCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Сегодня", style = MaterialTheme.typography.titleLarge)
                Text("Попыток открыть выбранные приложения: ${stats.attempts}")
                Text("Вопросов решено: ${stats.completed}")
                Text("Паузы ожидания завершены: ${stats.waits}")
                Text("Время на заданиях: ${stats.seconds / 60} мин")
                Text("Время считается внутри FocusStep; это не оценка экранного времени, сэкономленного в других приложениях.", style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(onClick = { showPinDialog = true }) { Text(if (PinCode.isSet(context)) "Изменить PIN" else "Задать PIN для защитных настроек") }
        TextButton(onClick = onSettings) { Text("Настройки и вопросы") }
        Text("Учебный прототип. Android позволяет отключить этот сервис или удалить приложение.", style = MaterialTheme.typography.bodySmall)
    }

    if (showDisclosure) AlertDialog(
        onDismissRequest = { showDisclosure = false },
        title = { Text("Почему нужен доступ?") },
        text = { Text("FocusStep использует службу специальных возможностей, чтобы получить событие о том, что на экране открылось приложение, которое ты сам выбрал. Содержимое экрана и вводимые данные не читаются и не отправляются. После включения Android покажет постоянный системный сервис; его можно отключить в любой момент в настройках.") },
        confirmButton = { Button(onClick = { showDisclosure = false; context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Понятно, открыть настройки") } },
        dismissButton = { TextButton(onClick = { showDisclosure = false }) { Text("Отмена") } },
    )
    if (showPinDialog) PinSetupDialog(onDone = { showPinDialog = false }, onCancel = { showPinDialog = false })
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
private fun SettingsPage() {
    val context = LocalContext.current
    var wait by remember { mutableIntStateOf(AppPrefs.waitMinutes(context)) }
    var url by remember { mutableStateOf(AppPrefs.remoteUrl(context)) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(AppPrefs.selectedApps(context)) }
    val apps = remember { launcherApps(context) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Правила", style = MaterialTheme.typography.headlineSmall)
        Text("Выбери приложения. При их открытии появится вопрос или таймер.")
        apps.forEach { app ->
            val checked = app.first in selected
            Row(Modifier.fillMaxWidth().clickable {
                selected = if (checked) selected - app.first else selected + app.first
                AppPrefs.setSelectedApps(context, selected)
            }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = { value ->
                    selected = if (value) selected + app.first else selected - app.first
                    AppPrefs.setSelectedApps(context, selected)
                })
                Column { Text(app.second); Text(app.first, style = MaterialTheme.typography.labelSmall) }
            }
        }
        HorizontalDivider()
        Text("Ожидание при пропуске: $wait мин")
        Slider(value = wait.toFloat(), onValueChange = { wait = it.toInt().coerceIn(2, 5); AppPrefs.setWaitMinutes(context, wait) }, valueRange = 2f..5f, steps = 2)
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
                    mainHandler.post { message = "Загружено вопросов: $count"; busy = false }
                } catch (e: Exception) { mainHandler.post { message = e.message ?: "Не удалось загрузить JSON."; busy = false } }
            }.start()
        }) { Text(if (busy) "Загрузка…" else "Обновить каталог") }
        TextButton(onClick = {
            QuestionRepository.resetToBundled(context)
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
                Text("PIN затрудняет импульсивные изменения, но не мешает отключить сервис в Android или удалить приложение.")
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
private fun PinVerifyDialog(onVerified: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Защитные настройки") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Введи PIN, чтобы изменить список приложений и правила.")
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
