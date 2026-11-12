package com.heyler.voicelab

import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EngineSmokeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun nativeScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        assertTrue(activity.window.decorView.isShown)
        instrumentation.runOnMainSync { activity.finish() }
    }

    @Test fun qwenMetricsProtocol() {
        val lab = LabViewModel(context.applicationContext as android.app.Application)
        val store = ViewModelStore().apply { put("lab", lab) }
        try {
            lab.clear()
            lab.benchmark()
            val deadline = SystemClock.elapsedRealtime() + 180000
            while (lab.state.value.busy && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
            assertFalse("Benchmark timed out", lab.state.value.busy)
            assertTrue(lab.state.value.status, lab.state.value.status.startsWith("Prueba terminada"))
            val report = JSONObject(lab.exportReport())
            assertEquals(9, report.getJSONArray("results").length())
            assertTrue(report.has("emulator"))
            assertFalse(report.has("asr"))
        } finally { lab.stop(); store.clear() }
    }

    @Test fun directAnswerContainsTheCircleCalculation() = runBlocking {
        val engines = LocalEngines(context)
        try {
            engines.loadLanguage()
            val text = StringBuilder()
            engines.answer("¿Cómo se calcula el área de un círculo con un radio de cinco metros? Responde con la fórmula y el resultado.") { text.append(it) }
            assertTrue("Expected a formula/calculation, received: $text", Regex("\\bpi\\b|π|r[²^]|78[.,]5|25", RegexOption.IGNORE_CASE).containsMatchIn(text.toString()))
        } finally { engines.close() }
    }

    @Test fun realLanguageModel() = runBlocking {
        val engines = LocalEngines(context)
        try {
            engines.loadLanguage()
            val text = StringBuilder()
            val times = engines.answer("Escribe hola en español.") { text.append(it) }
            assertTrue(text.isNotBlank())
            assertTrue(times.first > 0 && times.second >= times.first)
        } finally { engines.close() }
    }
}
