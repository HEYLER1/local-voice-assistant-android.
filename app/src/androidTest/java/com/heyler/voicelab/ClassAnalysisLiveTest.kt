package com.heyler.voicelab

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Explicitly invoked live service check. All text below is fictional, never a user's recording. */
class ClassAnalysisLiveTest {
    @Test fun noisyFictionalClassIsOrganizedWithGroundedQuestions()=runBlocking{
        val lines=listOf(
            SpeechLine(801,"En esta clase ficticia vamos a hablar de métricas de software. cuántas líneas de código puede tener un sistema grande puede tener millones pero las librerías y la reutilización hacen que las líneas no expliquen todo el esfuerzo",true),
            SpeechLine(802,"cómo se mide el tamaño de un software hay métricas de tamaño y complejidad. qué diferencia hay entre las métricas cuantitativas y las cualitativas las primeras expresan números y las otras características. que hay un eh no se no se",true),
            SpeechLine(803,"Pasamos a auditoría de sistemas. por qué deben estar los respaldos en ubicaciones diferentes para evitar que un mismo incidente dañe todas las copias. Las entrevistas se hacen a personas involucradas en el proceso. Esta es una demostración inventada.",true),
            SpeechLine(804,"Continuamos con el esfuerzo del desarrollo. qué factores influyen en el costo de construir software importa el esfuerzo de las personas, la complejidad de las funciones y los recursos disponibles. La cantidad de personas no reemplaza el análisis del trabajo real. No hemos establecido una cifra ni una fecha de entrega.",true),
            SpeechLine(805,"para qué sirve documentar un sistema un equipo necesita entender qué hace el sistema cuando otra persona debe mantenerlo. La documentación ayuda a reconocer procesos, entradas y salidas. No significa que todas las metodologías usen los mismos diagramas ni que una medida explique por sí sola la calidad.",true),
            SpeechLine(806,"En una entrevista de auditoría cuándo se usan preguntas abiertas cuando queremos conocer explicaciones y experiencias del personal. cuándo se usan preguntas cerradas cuando buscamos respuestas delimitadas y comparables. Esto es un caso didáctico completamente ficticio sin información de una empresa real.",true))
        val start=android.os.SystemClock.elapsedRealtime()
        val result=OnlineAssistant().analyze(lines,AssistantState())
        assertTrue(result.organization.title,result.organization.title.contains("software",true)||result.organization.title.contains("métrica",true)||result.organization.title.contains("auditor",true))
        assertTrue("Expected at least two grounded questions",result.questions.count{!it.suggested}>=2)
        assertTrue(result.questions.all{q->lines.first{it.id==q.source}.text.contains(q.evidence)})
        assertFalse(result.questions.any{ConversationRules.key(it.question)=="que hay un"||ConversationRules.key(it.question)=="como se mide"})
        val report=JSONObject().put("fixture","fictional-noisy-software-and-audit-class").put("provider",OnlineAssistant.NAME).put("elapsedMs",android.os.SystemClock.elapsedRealtime()-start)
            .put("title",result.organization.title).put("summary",result.summary).put("topics",JSONArray(result.organization.topics))
            .put("questions",JSONArray().apply{result.questions.forEach{q->put(JSONObject().put("question",q.question).put("answer",q.answer).put("source",q.source).put("evidence",q.evidence).put("suggested",q.suggested))}})
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null),"ui-tests").apply{mkdirs()}.resolve("v-semantic-live-synthetic.json").writeText(report.toString(2))
    }
}
