package com.heyler.voicelab

/** Context-aware chat names with a short warm-up so titles are not built from the first ASR fragment. */
internal object ConversationNaming {
    const val UNKNOWN="Desconocido"
    private val generic=setOf("desconocido","conversacion","tu conversacion","nueva conversacion","tema por confirmar","saludos","saludo","introduccion","hola","sin titulo")
    private val stop=setOf("el","la","los","las","un","una","de","del","y","en","a","para","sobre","como","con","que","tema","clase","porque","cuando","donde","quien","cual","cuales","esto","esta","este","estos","estas","todo","toda","tambien","pero","entonces","ahora","luego","despues","puede","pueden","tiene","tienen","hacer","hace","ser","son","fue","hay","muy","mas","menos","cada","solo","sola","dicho","parte","vamos","estamos","estaba","estaban","desde","hasta","entre","algunos","algunas")
    private fun normalizedTerms(text:String)=ConversationRules.key(text).split(' ').filter{it.length>=3&&it !in stop}.map{if(it.length>4)it.removeSuffix("s")else it}.toSet()
    private fun clean(text:String):String?{
        if(!SummaryQuality.usable(text))return null
        val name=SummaryQuality.clean(text).substringBefore(':').trim().replace(Regex("\\s+")," ").trim(' ','.',';')
        val key=ConversationRules.key(name)
        val words=key.split(' ')
        if(name.length !in 4..84||key in generic||name.contains('·')||name.any{it in "¿?\n"}||words.size>10||words.lastOrNull() in stop||normalizedTerms(name).size<2)return null
        return name.replaceFirstChar{it.uppercase()}
    }
    fun display(state:AssistantState)=state.conversationTitle.trim().ifBlank{automatic(state).ifBlank{UNKNOWN}}
    fun saved(manual:String,automatic:String)=manual.trim().ifBlank{clean(automatic)?:UNKNOWN}
    fun automatic(state:AssistantState):String{
        clean(state.automaticTitle)?.let{return it}
        val final=state.lines.filter{it.final||it.edited}
        LessonMemory.build(final).topics.lastOrNull()?.title?.let{clean(it)?.let{return it}}
        analysisTitle(state).let{clean(it)?.let{return it}}
        contextualTitle(final)?.let{return it}
        return ""
    }
    private fun contextualTitle(lines:List<SpeechLine>):String?{
        val text=lines.joinToString(" "){it.text.trim()}.trim()
        if(text.length<90||text.split(Regex("\\s+")).size<15)return null
        val ranked=Regex("[\\p{L}]{4,}").findAll(text.lowercase())
            .map{match->
                val word=match.value
                val key=ConversationRules.key(word).let{if(it.length>4)it.removeSuffix("s")else it}
                key to word
            }.filter{(key,_)->key !in stop}
            .groupBy({it.first},{it.second})
            .mapNotNull{(key,forms)->if(key.isBlank())null else key to (forms.maxByOrNull{it.length}.orEmpty() to forms.size)}
            .sortedWith(compareByDescending<Pair<String,Pair<String,Int>>>{it.second.second}.thenByDescending{it.second.first.length}
                .thenBy{it.first})
        if(ranked.size<3||ranked.first().second.second<2)return null
        val title=ranked.take(3).joinToString(" "){(_,value)->value.first.replaceFirstChar{it.uppercase()}}
        return clean(title)
    }
    fun analysisTitle(state:AssistantState):String{
        val final=state.lines.filter{it.final||it.edited}
        val candidate=clean(state.organization.title)?:return ""
        val evidence=state.organization.sections.flatMap{it.sources}.toSet()
        val sources=final.filter{it.id in evidence}
        val text=sources.joinToString(" "){it.text}
        if(text.length<100||text.split(Regex("\\s+")).size<16)return ""
        val subject=normalizedTerms(candidate)
        if(subject.isEmpty()||subject.count{it in normalizedTerms(text)}<minOf(2,subject.size))return ""
        return candidate
    }
}
