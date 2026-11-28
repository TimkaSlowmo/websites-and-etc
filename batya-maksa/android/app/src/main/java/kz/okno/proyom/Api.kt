package kz.okno.proyom

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ApiException(message: String) : Exception(message)

/** Клиент ИИ-сервера на Cloudflare Workers AI. */
class Api(private val baseUrl: String) {

    suspend fun analyze(drawing: Bitmap): WindowSpec {
        val b64 = withContext(Dispatchers.Default) { encode(drawing) }
        val res = post("analyze", JSONObject().put("image", b64))
        return WindowSpec.fromJson(res.getJSONObject("spec"))
    }

    /** ИИ-фото: scheme — PNG чистой схемы (до 512 px), по нему FLUX.2 рисует ту же сетку и створки. */
    suspend fun render(spec: WindowSpec, scheme: ByteArray?, style: String): Bitmap {
        val body = JSONObject().put("spec", spec.toJson()).put("style", style)
        if (scheme != null) body.put("scheme", Base64.encodeToString(scheme, Base64.NO_WRAP))
        val res = post("render", body)
        val bytes = Base64.decode(res.getString("image"), Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw ApiException("Сервер вернул неожиданный ответ")
    }

    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val conn = (URL(baseUrl.trimEnd('/') + "/" + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 150000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                // Сервер присылает готовый текст по-русски (например, про дневной лимит ИИ)
                val j = runCatching { JSONObject(text) }.getOrNull()
                val msg = j?.optString("message").orEmpty()
                throw ApiException(msg.ifBlank { "Ошибка ИИ-сервера: ${j?.optString("error")?.ifBlank { null } ?: "HTTP $code"}" })
            }
            JSONObject(text)
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Нет связи с сервером: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        fun encode(src: Bitmap, maxSide: Int = 1280): String {
            val scale = minOf(1f, maxSide.toFloat() / maxOf(src.width, src.height))
            val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    }
}
