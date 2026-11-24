package com.lowell.tvkumanda

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.security.SecureRandom
import java.util.Base64

const val TV_PORT = 8080

/** TV Kumanda sunucusuna WebSocket (yoksa HTTP) ile baglanir. */
class TvClient(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("tv", Context.MODE_PRIVATE)
    var ip by mutableStateOf(prefs.getString("ip", "") ?: "")
        private set
    var connected by mutableStateOf(false)
        private set
    var scanning by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var out: OutputStream? = null
    @Volatile private var sock: Socket? = null
    private val sendLock = Any()
    private val rnd = SecureRandom()
    private var loop: Job? = null

    fun updateIp(v: String) {
        val t = v.trim()
        prefs.edit().putString("ip", t).apply()
        ip = t
        closeWs()
    }

    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch { runLoop() }
    }

    fun stop() { loop?.cancel(); closeWs() }

    private fun closeWs() {
        try { sock?.close() } catch (_: Exception) { }
        out = null
        connected = false
    }

    private suspend fun runLoop() {
        while (currentCoroutineContext().isActive) {
            val h = ip
            if (h.isEmpty()) { delay(800); continue }
            try { connectWs(h) } catch (_: Exception) { }
            connected = false
            out = null
            delay(1500)
        }
    }

    private fun connectWs(host: String) {
        val s = Socket()
        sock = s
        s.use {
            it.connect(InetSocketAddress(host, TV_PORT), 2500)
            it.tcpNoDelay = true
            it.soTimeout = 25000
            val o = it.getOutputStream()
            val i = BufferedInputStream(it.getInputStream())
            val key = Base64.getEncoder().encodeToString(ByteArray(16).also { b -> rnd.nextBytes(b) })
            o.write(("GET /ws HTTP/1.1\r\nHost: $host\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\n\r\n").toByteArray())
            o.flush()
            val head = readHeader(i)
            if (!head.startsWith("HTTP/1.1 101")) return
            out = o
            connected = true
            val pinger = scope.launch {
                while (isActive) { delay(8000); send(JSONObject().put("t", "ping")) }
            }
            try { readFrames(DataInputStream(i), o) } finally { pinger.cancel() }
        }
    }

    private fun readHeader(i: BufferedInputStream): String {
        val sb = StringBuilder()
        while (sb.length < 4096) {
            val c = i.read()
            if (c < 0) break
            sb.append(c.toChar())
            if (sb.endsWith("\r\n\r\n")) break
        }
        return sb.toString()
    }

    private fun readFrames(di: DataInputStream, o: OutputStream) {
        while (true) {
            val b1 = di.readUnsignedByte()
            val b2 = di.readUnsignedByte()
            val op = b1 and 0x0F
            var len = (b2 and 0x7F).toLong()
            if (len == 126L) len = di.readUnsignedShort().toLong() else if (len == 127L) len = di.readLong()
            if (len > (1 shl 20)) return
            val data = ByteArray(len.toInt())
            di.readFully(data)
            if (op == 0x8) return
            if (op == 0x9) synchronized(sendLock) { writeFrame(o, data, 0xA) }
            if (op == 0x1) {
                val t = String(data)
                if (t.contains("skipped")) onMessage?.invoke(t)
            }
        }
    }

    var onMessage: ((String) -> Unit)? = null

    private fun writeFrame(o: OutputStream, p: ByteArray, op: Int) {
        val n = p.size
        val mask = ByteArray(4).also { rnd.nextBytes(it) }
        val head = java.io.ByteArrayOutputStream()
        head.write(0x80 or op)
        if (n < 126) head.write(0x80 or n)
        else { head.write(0x80 or 126); head.write(n shr 8); head.write(n and 0xFF) }
        head.write(mask)
        val body = ByteArray(n) { (p[it].toInt() xor mask[it % 4].toInt()).toByte() }
        head.write(body)
        o.write(head.toByteArray())
        o.flush()
    }

    /** Olay gonder: WebSocket varsa onunla, yoksa HTTP POST. */
    fun send(o: JSONObject) {
        val w = out
        if (w != null) {
            synchronized(sendLock) {
                try { writeFrame(w, o.toString().toByteArray(), 0x1); return }
                catch (_: Exception) { out = null; connected = false; try { sock?.close() } catch (_: Exception) { } }
            }
        }
        if (ip.isNotEmpty() && o.optString("t") != "ping") scope.launch { try { postRaw(o) } catch (_: Exception) { } }
    }

    fun key(k: String) = send(JSONObject().put("t", "key").put("k", k))

    private fun url(path: String) = URL("http://$ip:$TV_PORT$path")

    private fun postRaw(o: JSONObject): String? {
        val c = url("/api").openConnection() as HttpURLConnection
        c.connectTimeout = 3000; c.readTimeout = 20000
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(o.toString().toByteArray()) }
        return try { c.inputStream.bufferedReader().readText() } catch (_: Exception) { c.errorStream?.bufferedReader()?.readText() }
    }

    suspend fun post(o: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        try { postRaw(o)?.let { JSONObject(it) } } catch (_: Exception) { null }
    }

    suspend fun get(path: String): String? = withContext(Dispatchers.IO) {
        try {
            val c = url(path).openConnection() as HttpURLConnection
            c.connectTimeout = 3000; c.readTimeout = 25000
            c.inputStream.bufferedReader().readText()
        } catch (_: Exception) { null }
    }

    private fun phonePrefix(): String? {
        var any: String? = null
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (ni.isLoopback || !ni.isUp) continue
                for (a in ni.inetAddresses) {
                    if (a is Inet4Address && a.isSiteLocalAddress) {
                        val h = a.hostAddress ?: continue
                        if (ni.name.startsWith("wlan") || ni.name.startsWith("eth")) return h.substringBeforeLast('.') + "."
                        any = h.substringBeforeLast('.') + "."
                    }
                }
            }
        } catch (_: Exception) { }
        return any
    }

    /** Ayni agda 8080'de TV Kumanda sunucusunu bulur. */
    suspend fun scan(): String? {
        scanning = true
        try {
            val pre = phonePrefix() ?: return null
            return withContext(Dispatchers.IO) {
                (1..254).map { n ->
                    async {
                        val h = "$pre$n"
                        try {
                            val c = URL("http://$h:$TV_PORT/status").openConnection() as HttpURLConnection
                            c.connectTimeout = 600; c.readTimeout = 1500
                            if (c.responseCode == 200 && c.inputStream.bufferedReader().readText().contains("\"hid\"")) h else null
                        } catch (_: Exception) { null }
                    }
                }.awaitAll().firstOrNull { it != null }
            }
        } finally { scanning = false }
    }
}
