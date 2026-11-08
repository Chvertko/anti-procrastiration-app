package com.chvertko.focusstep.data

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class Question(
    val id: String,
    val category: String,
    val prompt: String,
    val options: List<String>,
    val answerIndex: Int,
    val explanation: String,
)

object QuestionRepository {
    private const val REMOTE_FILE = "remote_questions.json"
    private const val MAX_BYTES = 1_000_000
    private val allowedCategories = setOf("javascript", "typescript", "web")

    fun load(context: Context): List<Question> {
        val remote = context.filesDir.resolve(REMOTE_FILE)
        val source = if (remote.exists()) remote.readText() else
            context.assets.open("questions.json").bufferedReader().use { it.readText() }
        return parse(source)
    }

    fun resetToBundled(context: Context) {
        context.filesDir.resolve(REMOTE_FILE).delete()
    }

    fun updateFromHttps(context: Context, address: String): Int {
        val url = URL(address.trim())
        require(url.protocol.equals("https", ignoreCase = true)) { "Разрешены только HTTPS-ссылки." }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "FocusStep-Android/0.1")
        }
        try {
            require(connection.responseCode in 200..299) { "Сервис ответил кодом ${connection.responseCode}." }
            require(connection.contentLengthLong <= MAX_BYTES || connection.contentLengthLong < 0) { "JSON-файл больше 1 МБ." }
            val bytes = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_BYTES) { "JSON-файл больше 1 МБ." }
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
            val json = String(bytes, StandardCharsets.UTF_8)
            val parsed = parse(json)
            require(parsed.isNotEmpty()) { "В файле нет вопросов." }
            val destination = context.filesDir.resolve(REMOTE_FILE)
            val temporary = context.filesDir.resolve("$REMOTE_FILE.tmp")
            temporary.writeText(json, StandardCharsets.UTF_8)
            java.nio.file.Files.move(
                temporary.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
            parsed.size
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(text: String): List<Question> {
        val root = JSONObject(text)
        require(root.optInt("schemaVersion") == 1) { "Неподдерживаемая версия JSON-схемы." }
        val array = root.optJSONArray("questions") ?: error("Не найден массив questions.")
        require(array.length() in 1..500) { "Количество вопросов должно быть от 1 до 500." }
        val ids = mutableSetOf<String>()
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val id = item.getString("id").take(100)
            val category = item.getString("category")
            val prompt = item.getString("prompt").trim().take(500)
            val optionsJson = item.getJSONArray("options")
            val options = (0 until optionsJson.length()).map { optionsJson.getString(it).trim().take(200) }
            val answer = item.getInt("answerIndex")
            val explanation = item.optString("explanation", "").trim().take(500)
            require(id.isNotBlank() && ids.add(id)) { "Пустой или повторяющийся ID вопроса: $id" }
            require(category in allowedCategories) { "Неизвестная категория: $category" }
            require(prompt.isNotBlank() && options.size in 2..6 && options.all { it.isNotBlank() }) { "Некорректный вопрос: $id" }
            require(answer in options.indices) { "Некорректный answerIndex у вопроса $id" }
            Question(id, category, prompt, options, answer, explanation)
        }
    }
}

object PinCode {
    private const val PREFS = "focusstep"
    private const val SALT = "pin_salt"
    private const val HASH = "pin_hash"

    fun isSet(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(HASH)

    fun save(context: Context, pin: String) {
        require(pin.length in 4..12 && pin.all { it.isDigit() })
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = derive(pin, salt)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(SALT, android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
            .putString(HASH, android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP))
            .apply()
    }

    fun verify(context: Context, pin: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val salt = prefs.getString(SALT, null)?.let { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) } ?: return false
        val expected = prefs.getString(HASH, null)?.let { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) } ?: return false
        return java.security.MessageDigest.isEqual(expected, derive(pin, salt))
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 160_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}
