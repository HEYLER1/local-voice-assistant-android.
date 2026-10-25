package com.heyler.voicelab

import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal class OnlineQuotaException(val waitMillis:Long):IOException("Se alcanzó el límite temporal del servicio gratuito. Se pausaron las solicitudes automáticas; puedes reintentar cuando termine la espera.")
internal object OnlineBudget {
    const val INTERVAL=30_000L
    fun retryMillis(header:String?,nowMillis:Long=System.currentTimeMillis()):Long{
        val seconds=header?.trim()?.toLongOrNull()
        val value=if(seconds!=null)seconds.coerceIn(0,86400)*1000 else runCatching{ZonedDateTime.parse(header,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-nowMillis}.getOrDefault(60_000L)
        return value.coerceIn(INTERVAL,86_400_000L)
    }
}
