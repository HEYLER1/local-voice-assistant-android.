package com.heyler.voicelab

/** Silence alone is never evidence of a failed recognizer. Times use elapsedRealtime. */
internal object SpeechHealth {
    fun stalled(now:Long,lastFrame:Long,lastWrite:Long,lastResult:Long,voiceMillis:Long):String?=when{
        now-lastFrame>12000->"La captura dejó de entregar audio"
        now-lastWrite>12000->"El servicio dejó de consumir audio"
        now-lastResult>45000 && voiceMillis>=8000->"Hay audio activo pero el servicio no entrega texto"
        else->null
    }
}
