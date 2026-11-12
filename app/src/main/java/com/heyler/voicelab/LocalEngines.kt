package com.heyler.voicelab

import android.content.Context
import android.os.SystemClock
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

/** Qwen is retained only for the internal lab and local model metrics. */
class LocalEngines(private val context: Context) {
    private var engine: Engine? = null
    private var languageContext = 0
    @Volatile private var conversation: Conversation? = null

    fun loadLanguage(maxTokens: Int = 1024): Double {
        if (engine != null && languageContext == maxTokens) return 0.0
        unloadLanguage()
        val file = File(context.filesDir, "model.litertlm")
        check(file.exists()) { "Importa primero el modelo .litertlm." }
        val start = SystemClock.elapsedRealtimeNanos()
        val candidate = Engine(EngineConfig(modelPath=file.path, backend=Backend.CPU(), maxNumTokens=maxTokens, cacheDir=File(context.cacheDir, "llm").apply { mkdirs() }.path))
        try {
            candidate.initialize()
            engine = candidate
            languageContext = maxTokens
        } catch (error: Throwable) {
            candidate.close()
            throw error
        }
        return (SystemClock.elapsedRealtimeNanos() - start) / 1e6
    }

    fun unloadLanguage() { engine?.close(); engine = null; languageContext = 0 }
    suspend fun answer(prompt: String, onText: (String) -> Unit) = generate(prompt, 160, "Responde en español en una o dos frases. No inventes datos personales ni fuentes.", onText)

    internal suspend fun generate(prompt: String, limit: Int, instruction: String, onText: (String) -> Unit): Pair<Double, Double> {
        val start = SystemClock.elapsedRealtimeNanos()
        var first: Double? = null
        val template = """{% for message in messages %}{{ '<|im_start|>' + message['role'] + '\n' }}{% if message['content'] is string %}{{ message['content'] }}{% else %}{% for part in message['content'] %}{% if part['type'] == 'text' %}{{ part['text'] }}{% endif %}{% endfor %}{% endif %}{{ '<|im_end|>\n' }}{% endfor %}{% if add_generation_prompt %}{{ '<|im_start|>assistant\n<think>\n\n</think>\n\n' }}{% endif %}"""
        val config = ConversationConfig(
            systemInstruction = Contents.of(instruction),
            samplerConfig = SamplerConfig(topK=20, topP=0.9, temperature=0.2),
            extraContext = mapOf("enable_thinking" to false),
            chatTemplate = template
        )
        val activeConversation = checkNotNull(engine).createConversation(config)
        conversation = activeConversation
        try {
            activeConversation.sendMessageAsync(prompt, maxOutputToken=limit, thinkingConfig=ThinkingConfig(false)).collect { message ->
                currentCoroutineContext().ensureActive()
                val text = message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                if (text.isNotBlank()) {
                    if (first == null) first = (SystemClock.elapsedRealtimeNanos() - start) / 1e6
                    onText(text)
                }
            }
            check(first != null) { "El modelo no produjo texto." }
            return first!! to (SystemClock.elapsedRealtimeNanos() - start) / 1e6
        } finally {
            conversation = null
            activeConversation.close()
        }
    }

    fun cancel() { conversation?.cancelProcess() }
    fun close() { cancel(); unloadLanguage() }
}
