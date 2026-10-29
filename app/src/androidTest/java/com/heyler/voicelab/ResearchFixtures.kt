package com.heyler.voicelab

import org.json.JSONArray
import org.json.JSONObject

/** Short educational paraphrases used as test data, never the user's recording. */
internal object ResearchFixtures {
    val lines=listOf(SpeechLine(9901,"Hoy vamos a estudiar eclipses solares. La Luna se interpone entre la Tierra y el Sol y proyecta una sombra sobre nuestro planeta.",true),
        SpeechLine(9902,"La órbita de la Luna está inclinada. Por eso los eclipses solares no se producen en cada luna nueva. Hablamos de las temporadas de eclipses y de las distancias relativas entre el Sol, la Luna y la Tierra.",true))
    val source=ResearchSource("R1","Eclipse solar","https://es.wikipedia.org/wiki/Eclipse_solar",
        "Un eclipse solar ocurre cuando la Luna se interpone entre la Tierra y el Sol. Puede ser total, anular, parcial o híbrido. La inclinación de la órbita lunar impide un eclipse en cada luna nueva. El tamaño aparente depende de la distancia de los astros. Las temporadas de eclipses duran aproximadamente 34 días. La luna nueva se repite cada 29,53 días. Cada año hay entre dos y cinco eclipses solares. La geometría orbital determina dónde se proyecta la sombra.",1_800_000_000_000L)
    private val entries=listOf(
        Triple("¿Qué son?","Alineación de los astros","Un eclipse solar ocurre cuando la Luna se interpone entre la Tierra y el Sol."),
        Triple("Tipos de eclipses","Total, anular, parcial e híbrido","Puede ser total, anular, parcial o híbrido."),
        Triple("Inclinación orbital","Por qué no hay uno cada mes","La inclinación de la órbita lunar impide un eclipse en cada luna nueva."),
        Triple("Distancia y tamaño aparente","Tamaños vistos desde la Tierra","El tamaño aparente depende de la distancia de los astros."),
        Triple("Temporadas de eclipses","Una ventana de alineación","Las temporadas de eclipses duran aproximadamente 34 días."),
        Triple("Luna nueva y repetición","El ciclo sinódico","La luna nueva se repite cada 29,53 días."),
        Triple("Frecuencia anual","Entre dos y cinco","Cada año hay entre dos y cinco eclipses solares."),
        Triple("Rutas sobre la Tierra","La sombra y su recorrido","La geometría orbital determina dónde se proyecta la sombra."))
    fun response()=JSONObject().put("title","Patrones de los eclipses solares")
        .put("overview","La alineación, la inclinación orbital y las distancias permiten entender los eclipses solares y su repetición.")
        .put("branches",JSONArray().apply{entries.forEach{(title,subtitle,text)->put(JSONObject().put("title",title).put("points",JSONArray().put(
            JSONObject().put("subtitle",subtitle).put("text",text).put("origin","model").put("references",JSONArray()).put("evidence",""))))}}).toString()
    fun map()=ResearchedMapProtocol.parse(response(),"Eclipses solares",lines,emptyList())
    fun state()=AssistantState(conversationKey=9917,lines=lines,summary="La clase trata sobre la geometría de los eclipses.",
        organization=ClassOrganization("Eclipses solares",sections=listOf(OrganizedSection("Geometría del eclipse",lines.joinToString(" "){it.text},lines.map{it.id})),map=map()))
}
