package com.heyler.voicelab

/** Reject prompt leakage before it can replace saved notes or enter a map. */
internal object SummaryQuality {
    private val meta=Regex("(?:tema previo|notas previas|bloque\\s*:|sintesis fiel|sintesis de una frase|primer subtema|segundo subtema|tercer subtema|devuelve solo|no copies estas|<\\|im_|</?think>|(?:^|\\n)\\s*(?:system|assistant|user)\\s*:)")
    fun usable(text:String)=text.isNotBlank()&&!meta.containsMatchIn(ConversationRules.folded(text))
    fun display(text:String)=text.takeIf(::usable).orEmpty()
    fun clean(text:String)=text.replace(Regex("#{1,6}\\s*"),"").replace("**","").replace("__","").trim()
    fun heading(text:String):String{
        val clean=clean(text);val key=ConversationRules.key(clean)
        if(!usable(clean)||key in setOf("como","que","tema","subtema","resumen","subtitulo")||Regex("^(?:y|pero|porque|como|que|entonces|ademas|en el|el factor)\\b").containsMatchIn(key))return ""
        return clean
    }
}
