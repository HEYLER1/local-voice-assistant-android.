package com.heyler.voicelab

/** Extractive structure: source text remains literal; generated summary is separate. */
data class KnowledgeOutline(val topic:String,val ideas:List<SpeechLine>,val figures:List<SpeechLine>)
data class KnowledgeTopic(val title:String,val lines:List<SpeechLine>)
object KnowledgeOrganizer {
    fun topics(lines:List<SpeechLine>):List<KnowledgeTopic>{
        val buckets=linkedMapOf<String,MutableList<SpeechLine>>()
        lines.forEach{line->val title=build(listOf(line)).topic.substringBefore(" · ");buckets.getOrPut(title){mutableListOf()}.add(line)}
        return buckets.map{KnowledgeTopic(it.key,it.value)}
    }
    fun build(lines:List<SpeechLine>):KnowledgeOutline {
        val stop=setOf("para","porque","como","esta","este","esto","tiene","puede","cuando","donde","pero","tambien","sobre","entre","desde","estamos","hablando","mucho","muchas","cosas","vamos","entonces","ahora","hablar","hacer","todos","todas","ocurre","ocurren","calcula","calcular","estudiando","observando","habla","hablamos","tienen","durante","hace","dice","dijo","primer","primero","segundo")
        val terms=lines.flatMap{ConversationRules.key(it.text).split(" ")}.filter{it.length>=4&&it !in stop&&it.any{c->c.isLetter()}}
        val topic=terms.groupingBy{it}.eachCount().entries.sortedByDescending{it.value}.take(3).joinToString(" · "){it.key.replaceFirstChar{c->c.uppercase()}}.ifBlank{"Tu conversación"}
        val quotes=ConversationRules.salient(lines.joinToString(" "){it.text})
        val ideas=lines.filter{l->quotes.any{it.contains(l.text.trim())||l.text.contains(it)}}.take(4).ifEmpty{lines.filter{ConversationRules.key(it.text).split(" ").size>=6}.take(4)}
        return KnowledgeOutline(topic,ideas,lines.filter{NumericMentions.spans(it.text).isNotEmpty()}.take(4))
    }
}
