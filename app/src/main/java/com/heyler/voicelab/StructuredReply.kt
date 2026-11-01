package com.heyler.voicelab

import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException

internal class StructuredReplyException:IOException(SummaryIssues.INVALID_FORMAT)

internal object SummaryIssues {
    const val INVALID_FORMAT="No se pudo organizar el resumen recibido. Se conservan los apuntes anteriores. Pulsa Generar resumen para reintentar."
    fun display(message:String):String = if(message.length>500||
        Regex("JSONException|Expected ['\\\"]|at character \\d+|\\{\\s*\\\"|\\\"sections\\\"\\s*:|\\\"previous\\\"\\s*:",RegexOption.IGNORE_CASE).containsMatchIn(message))INVALID_FORMAT else message
}

/** Never forwards parser diagnostics: Android's JSONException can contain the entire transcript. */
internal object StructuredReply {
    fun objectValue(raw:String):JSONObject {
        val text=raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        if(text.length>100000||!text.startsWith('{')||!text.endsWith('}'))throw StructuredReplyException()
        try{
            val reader=JSONTokener(text)
            val value=reader.nextValue() as? JSONObject?:throw StructuredReplyException()
            if(reader.nextClean()!='\u0000')throw StructuredReplyException()
            return value
        }catch(_:JSONException){throw StructuredReplyException()}
    }
    fun string(data:JSONObject,key:String):String {
        if(!data.has(key)||data.isNull(key))return ""
        return data.opt(key) as? String?:throw StructuredReplyException()
    }
}
