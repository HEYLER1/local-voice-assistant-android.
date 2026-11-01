package com.heyler.voicelab

/** A local readiness check; it never invokes a model or starts an automatic summary. */
internal object SummaryReadiness {
    private val filler=setOf("para","pero","como","porque","este","esta","esto","estos","estas","entonces","bueno","hola","gracias","muchas","mucho","tambien","tiene","tienen","vamos","hacer","decir","pues","claro","ahora","aqui")
    fun hasEnoughText(text:String):Boolean {
        val words=ConversationRules.key(text).split(' ').filter{it.isNotBlank()}
        return words.size>=30&&words.filter{it.length>=4&&it !in filler}.distinct().size>=8
    }
}
