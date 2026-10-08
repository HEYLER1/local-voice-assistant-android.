package com.heyler.voicelab

/** Study excerpts remain literal and traceable; no definitions or answers are generated here. */
data class StudyExcerpt(val sourceId:Long,val text:String)
data class StudyConcept(val term:String,val excerpt:StudyExcerpt)
data class StudyGuide(val ideas:List<StudyExcerpt>,val concepts:List<StudyConcept>,val figures:List<StudyExcerpt>){
    companion object{
        fun build(lines:List<SpeechLine>):StudyGuide{
            val excerpts=lines.flatMap{line->line.text.split(Regex("(?<=[.!?])\\s+|\\n+")).map{StudyExcerpt(line.id,it.trim())}}
                .filter{it.text.split(Regex("\\s+")).size>=6&&it.text.none{c->c=='¿'||c=='?'}}
                .distinctBy{ConversationRules.key(it.text)}
            val definition=Regex("^(.{3,65}?)\\s+(?:es|son|se define como|consiste en|se refiere a|ocurre cuando)\\s+(.{12,})",RegexOption.IGNORE_CASE)
            val concepts=excerpts.mapNotNull{excerpt->definition.find(excerpt.text)?.let{StudyConcept(it.groupValues[1],excerpt)}}.distinctBy{ConversationRules.key(it.term)}
            val ranked=excerpts.sortedByDescending{excerpt->
                (if(concepts.any{it.excerpt==excerpt})3 else 0)+(if(NumericMentions.spans(excerpt.text).isNotEmpty())1 else 0)
            }.take(6).toSet()
            return StudyGuide(excerpts.filter{it in ranked},concepts.take(6),excerpts.filter{NumericMentions.spans(it.text).isNotEmpty()}.take(8))
        }
    }
}
