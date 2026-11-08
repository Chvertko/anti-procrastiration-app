package com.chvertko.focusstep.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chvertko.focusstep.AppPrefs
import com.chvertko.focusstep.data.QuestionRepository
import kotlinx.coroutines.delay

class GateActivity : ComponentActivity() {
    companion object { const val EXTRA_PACKAGE = "target_package" }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        setContent { GateScreen(target) }
    }
}

@Composable
private fun GateScreen(targetPackage: String) {
    val activity = androidx.compose.ui.platform.LocalContext.current as Activity
    val context = activity
    val prefs = context.getSharedPreferences("focusstep", 0)
    val waitKey = "wait_until_$targetPackage"
    var waitUntil by remember { mutableLongStateOf(prefs.getLong(waitKey, 0L)) }
    val question = remember {
        val enabled = AppPrefs.selectedCategories(context)
        val candidates = runCatching { QuestionRepository.load(context) }.getOrDefault(emptyList())
            .filter { enabled.isEmpty() || it.category in enabled }
        if (candidates.isEmpty()) null else {
            val last = prefs.getString("last_question", null)
            candidates.filter { it.id != last }.ifEmpty { candidates }.random().also {
                prefs.edit().putString("last_question", it.id).apply()
            }
        }
    }
    val appLabel = remember(targetPackage) {
        runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(targetPackage, 0)).toString() }
            .getOrDefault("выбранное приложение")
    }
    var remaining by remember { mutableLongStateOf(((waitUntil - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L)) }
    var selected by remember { mutableIntStateOf(-1) }
    var feedback by remember { mutableStateOf("") }
    var solved by remember { mutableStateOf(false) }
    val startTime = remember { System.currentTimeMillis() }

    LaunchedEffect(waitUntil) {
        if (waitUntil > 0) {
            while (true) {
                remaining = ((waitUntil - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L)
                delay(1_000)
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF8DE0C1), secondary = androidx.compose.ui.graphics.Color(0xFFF4BD78))) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Небольшая пауза", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("Ты собирался открыть $appLabel", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (waitUntil > 0) {
                    Text("Можно пропустить вопрос. Подожди, пока закончится таймер:")
                    Text("${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.displayMedium)
                    LinearProgressIndicator(progress = { 1f - remaining.toFloat() / (AppPrefs.waitMinutes(context) * 60f) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { returnToHome(activity) }, modifier = Modifier.fillMaxWidth()) { Text("Вернуться назад") }
                    Button(onClick = {
                        AppPrefs.grantAccess(context, targetPackage)
                        AppPrefs.recordOutcome(context, false, 0)
                        activity.finish()
                    }, enabled = remaining == 0L, modifier = Modifier.fillMaxWidth()) { Text(if (remaining == 0L) "Открыть $appLabel" else "Ожидание…") }
                } else if (solved) {
                    Text("Хороший шаг. ${question?.explanation.orEmpty()}")
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        AppPrefs.grantAccess(context, targetPackage)
                        AppPrefs.recordOutcome(context, true, ((System.currentTimeMillis() - startTime) / 1000).toInt())
                        activity.finish()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Продолжить в $appLabel") }
                    TextButton(onClick = { returnToHome(activity) }, modifier = Modifier.fillMaxWidth()) { Text("Вернуться к своему делу") }
                } else if (question != null) {
                    Text(question.prompt, style = MaterialTheme.typography.titleLarge)
                    question.options.forEachIndexed { index, option ->
                        OutlinedButton(onClick = { selected = index; feedback = "" }, modifier = Modifier.fillMaxWidth()) {
                            Text((if (selected == index) "●  " else "○  ") + option)
                        }
                    }
                    if (feedback.isNotBlank()) Text(feedback, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        if (selected == question.answerIndex) solved = true
                        else feedback = "Пока не совсем. Попробуй ещё раз или выбери ожидание."
                    }, enabled = selected >= 0, modifier = Modifier.fillMaxWidth()) { Text("Проверить ответ") }
                    OutlinedButton(onClick = {
                        val until = System.currentTimeMillis() + AppPrefs.waitMinutes(context) * 60_000L
                        prefs.edit().putLong(waitKey, until).apply()
                        waitUntil = until
                        remaining = AppPrefs.waitMinutes(context) * 60L
                        feedback = "Таймер запущен. Можно оставить экран или вернуться позже."
                    }, modifier = Modifier.fillMaxWidth()) { Text("Пропустить · подождать ${AppPrefs.waitMinutes(context)} мин") }
                    TextButton(onClick = { returnToHome(activity) }, modifier = Modifier.fillMaxWidth()) { Text("Вернуться назад") }
                } else {
                    Text("Каталог заданий не удалось открыть. Можно подождать и продолжить.")
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        val until = System.currentTimeMillis() + AppPrefs.waitMinutes(context) * 60_000L
                        prefs.edit().putLong(waitKey, until).apply()
                        waitUntil = until
                        activity.recreate()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Подождать ${AppPrefs.waitMinutes(context)} мин") }
                    TextButton(onClick = { returnToHome(activity) }, modifier = Modifier.fillMaxWidth()) { Text("Назад") }
                }
            }
        }
    }
}

private fun returnToHome(activity: Activity) {
    activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    activity.finish()
}
