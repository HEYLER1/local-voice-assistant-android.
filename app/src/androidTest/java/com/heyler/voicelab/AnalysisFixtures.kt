package com.heyler.voicelab

import org.json.JSONArray
import org.json.JSONObject

internal object AnalysisFixtures {
    fun eclipse()=JSONObject().put("title","Eclipses solares").put("topics",JSONArray().put("Astronomía"))
        .put("summary","La clase explica la posición de la Luna y la Tierra durante los eclipses.")
        .put("sections",JSONArray().put(JSONObject().put("title","Geometría del eclipse").put("text","La Luna pasa entre la Tierra y el Sol.").put("sources",JSONArray().put(1))))
        .put("questions",JSONArray().put(JSONObject().put("source",1).put("evidence","Por qué ocurre un eclipse solar").put("origin","heard").put("question","¿Por qué ocurre un eclipse solar?").put("answer","La Luna pasa entre la Tierra y el Sol."))).toString()
    fun software()=JSONObject().put("title","Métricas de software y auditoría").put("topics",JSONArray().put("Métricas de software").put("Auditoría de sistemas"))
        .put("summary","Se comparan medidas de tamaño y tipos de métricas para evaluar sistemas.")
        .put("sections",JSONArray().put(JSONObject().put("title","Tamaño de software").put("text","Las líneas de código no son la única medida.").put("sources",JSONArray().put(1))))
        .put("questions",JSONArray()
            .put(q("¿Cómo se mide el tamaño de un sistema de software?","cómo se mide el tamaño de un sistema de software","heard"))
            .put(q("¿Cómo medir el tamaño de un sistema de software?","cómo se mide el tamaño de un sistema de software","heard"))
            .put(q("¿Cómo se relacionan las métricas con una auditoría?","Las métricas ayudan a evaluar sistemas","study"))
            .put(q("¿Qué es COCOMO II?","COCOMO II aparece literalmente aquí","heard"))
            .put(q("¿Cómo se mide?","cómo se mide el tamaño de un sistema de software","heard"))).toString()
    private fun q(question:String,evidence:String,origin:String)=JSONObject().put("question",question).put("evidence",evidence).put("origin",origin).put("source",1).put("answer","Se consideran medidas de tamaño y complejidad. Explicación adicional: la elección depende del objetivo.")
    fun sse(text:String)="data: "+JSONObject().put("choices",JSONArray().put(JSONObject().put("delta",JSONObject().put("content",text)).put("finish_reason","stop")))+"\n\ndata: [DONE]\n\n"
}
