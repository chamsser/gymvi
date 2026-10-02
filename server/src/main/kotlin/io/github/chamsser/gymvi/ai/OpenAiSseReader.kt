package io.github.chamsser.gymvi.ai

import java.io.FilterInputStream
import java.io.InputStream

/** Bounded, UTF-8 SSE framing shared by the real transport and its wire tests. */
internal object OpenAiSseReader {
    fun read(input: InputStream, onData: (String) -> Unit) {
        val bounded = object : FilterInputStream(input) {
            var count = 0
            override fun read(): Int = super.read().also { if (it >= 0) add(1) }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
                `in`.read(bytes, offset, length).also { if (it > 0) add(it) }
            fun add(amount: Int) {
                count += amount
                if (count > 2 * 1024 * 1024) throw AiProviderException("AI_PROVIDER_RESPONSE_TOO_LARGE")
            }
        }
        val reader = bounded.bufferedReader(Charsets.UTF_8)
        val data = StringBuilder()
        fun dispatch() {
            if (data.isNotEmpty()) {
                val value = data.toString().removeSuffix("\n")
                data.setLength(0)
                if (value != "[DONE]") onData(value)
            }
        }
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) dispatch()
            else if (line.startsWith("data:")) data.append(line.substring(5).removePrefix(" ")).append('\n')
        }
        dispatch()
    }
}
