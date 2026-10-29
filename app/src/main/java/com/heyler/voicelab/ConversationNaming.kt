package com.heyler.voicelab

/** A chat name describes established subject matter, never the first random keywords. */
internal object ConversationNaming {
    const val UNKNOWN="Desconocido"
    private val generic=setOf("desconocido","conversacion","tu conversacion","nueva conversacion","tema por confirmar","saludos","saludo","introduccion","hola","sin titulo")
    private val stop=setOf("el","la","los","las","un","una","de","del","y","en","a","para","sobre","como","con","que","tema","clase")
    private fun terms(text:String)=ConversationRules.key(text).split(' ').filter{it.length>=3&&it !in stop}.map{if(it.length>4)it.removeSuffix("s")else it}.toSet()
    private fun clean(text:String):String?{
        // A subtitle is not part of the sidebar name. Keep a complete subject, never cut mid-phrase.
        val name=text.substringBefore(':').trim().replace(Regex("\\s+")," ").trim(' ','.',';')
        val key=ConversationRules.key(name)
        val words=key.split(' ')
        if(name.length !in 4..60||key in generic||name.contains('·')||name.any{it in "¿?\n"}||words.size>7||words.lastOrNull() in stop||terms(name).isEmpty())return null
        return name.replaceFirstChar{it.uppercase()}
    }
    fun display(state:AssistantState)=state.conversationTitle.trim().ifBlank{automatic(state).ifBlank{UNKNOWN}}
    fun saved(manual:String,automatic:String)=manual.trim().ifBlank{clean(automatic)?:UNKNOWN}
    fun automatic(state:AssistantState):String{
        clean(state.automaticTitle)?.let{return it}
        val final=state.lines.filter{it.final||it.edited}
        // A deliberate topic announcement is sufficient, but partial ASR hypotheses are not.
        LessonMemory.build(final).topics.firstOrNull()?.title?.let{clean(it)?.let{return it}}
        return analysisTitle(state)
    }
    fun analysisTitle(state:AssistantState):String{
        val final=state.lines.filter{it.final||it.edited}
        val candidate=clean(state.organization.title)?:return ""
        val evidence=state.organization.sections.flatMap{it.sources}.toSet()
        val sources=final.filter{it.id in evidence}
        val text=sources.joinToString(" "){it.text}
        // An analysis title needs a substantial source-backed passage and matching subject terms.
        if(text.length<200||text.split(Regex("\\s+")).size<35)return ""
        val subject=terms(candidate)
        if(subject.size<2||subject.count{it in terms(text)}<2)return ""
        return candidate
    }
}
