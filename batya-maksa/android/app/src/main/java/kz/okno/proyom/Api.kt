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
        val b64 = encode(drawing)
        val res = post("analyze", JSONObject().put("image", b64))
        return WindowSpec.fromJson(res.getJSONObject("spec"))
    }

    suspend fun render(spec: WindowSpec): Bitmap {
        val res = post("render", JSONObject().put("spec", spec.toJson()))
        val bytes = Base64.decode(res.getString("image"), Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw ApiException("Сервер вернул неожиданный ответ")
    }

    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val conn = (URL(baseUrl.trimEnd('/') + "/" + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 120000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).let { it.optString("message").ifBlank { it.optString("error") } } }.getOrNull()
                throw ApiException("Ошибка ИИ-сервера: ${msg?.ifBlank { null } ?: "HTTP $code"}")
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
