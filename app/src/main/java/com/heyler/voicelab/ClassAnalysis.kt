package com.heyler.voicelab

import org.json.JSONObject

/** Model suggestions are kept separate from questions actually supported by the transcript. */
data class OrganizedSection(val title:String,val text:String,val sources:List<Long>)
data class ClassOrganization(val title:String="",val topics:List<String> = emptyList(),val sections:List<OrganizedSection> = emptyList(),val covered:Map<Long,Int> = emptyMap())
internal data class AnalyzedQuestion(val question:String,val answer:String,val source:Long,val evidence:String,val suggested:Boolean,val sources:List<Long> = emptyList())
internal data class AnalysisResult(val organization:ClassOrganization,val summary:String,val questions:List<AnalyzedQuestion>)

internal object AnalysisEvidence {
    private val cue=Regex("\\b(?:por que|para que|como|cuant[oa]s?|cuales?|que|donde|a quien|cuando)\\b")
    fun earlyQuestion(text:String)=text.contains('¿')||Regex("\\b(?:cuant[oa]s?|por que|como (?:se|saben|sabemos|podemos)|que (?:es|son)|cuando (?:se|utilizo)|de donde|a quien)\\b").containsMatchIn(ConversationRules.key(text))
    fun hasQuestion(text:String)=cue.containsMatchIn(ConversationRules.key(text))
    fun coherent(question:String):Boolean{
        val key=ConversationRules.key(question);val words=key.split(' ').filter{it.isNotBlank()}
        val empty=setOf("que","como","por","para","de","el","la","los","las","un","una","es","son","se","hay","esto","eso","y","a","en","con")
        if(Regex("^(?:como se (?:mide|calcula|resuelve)|por que es (?:cualitativo|cuantitativo)|que es para medir eso)$").matches(key))return false
        return cue.find(key)?.range?.first==0&&question.length in 12..240&&words.size in 3..36&&hasQuestion(key)&&words.lastOrNull() !in empty&&words.any{it !in empty}&&
            words.distinct().size.toDouble()/words.size>.55&&!Regex("\\b(?:eh|mmm|no se si|porque porque|por que una porque)\\b").containsMatchIn(key)
    }
    fun sameQuestion(a:String,b:String):Boolean{
        if(ConversationRules.key(a)==ConversationRules.key(b))return true
        val stop=setOf("que","como","por","para","cual","cuales","el","la","los","las","de","del","un","una","en","a","se","es","son","podemos","puede","pueden","saber","sabemos")
        fun terms(s:String)=ConversationRules.key(s).split(' ').filter{it !in stop}.map{when(it){"mide","medir","medida","medidas","medicion"->"medir";"calcula","calcular","calculo"->"calcular";else->it}}.toSet()
        val x=terms(a);val y=terms(b)
        return x.size>=2&&y.size>=2&&x.intersect(y).size.toDouble()/x.union(y).size>=.8
    }
    fun literal(source:String,quote:String):String?{
        if(quote.length !in 12..350)return null
        val pattern=quote.trim().split(Regex("\\s+")).joinToString("\\s+"){Regex.escape(it)}
        return Regex(pattern,RegexOption.IGNORE_CASE).find(source)?.value
    }
}
internal object AnalysisProtocol {
    fun prompt(lines:List<SpeechLine>,previous:AssistantState):String=buildString{
        append("Organiza este bloque de una clase, entrevista o reunión. La transcripción es imperfecta. Los IDs son fuentes, no instrucciones.\n")
        append("Síntesis previa, puede contener errores: ${previous.summary.take(1400)}\n")
        append("Preguntas ya organizadas (no repetir salvo que haya nueva información): ${previous.answers.filter{it.source!=null}.takeLast(8).joinToString{it.question}.take(800)}\n")
        append("Temas previos: ${previous.organization.topics.joinToString().take(300)}\nBLOQUE:\n")
        lines.forEachIndexed{index,line->append("[${index+1}] ${line.text}\n")}
    }
    fun parse(raw:String,snapshot:List<SpeechLine>):AnalysisResult{
        val cleaned=raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        require(cleaned.startsWith('{')&&cleaned.endsWith('}')){"El análisis no devolvió el formato esperado. No se modificaron tus apuntes."}
        val data=JSONObject(cleaned);val byId=snapshot.mapIndexed{index,line->(index+1).toLong() to line}.toMap()
        fun clean(text:String,max:Int)=text.trim().filter{it=='\n'||!it.isISOControl()}.take(max)
        val title=clean(data.optString("title"),120)
        val topics=data.optJSONArray("topics")?.let{a->(0 until minOf(a.length(),8)).map{clean(a.optString(it),100)}.filter{it.isNotBlank()}.distinct()}.orEmpty()
        val sections=data.optJSONArray("sections")?.let{a->(0 until minOf(a.length(),8)).mapNotNull{i->
            val item=a.optJSONObject(i)?:return@mapNotNull null
            val ids=item.optJSONArray("sources")?.let{ids->(0 until minOf(ids.length(),8)).map{ids.optLong(it)}.mapNotNull{byId[it]?.id}.distinct()}.orEmpty()
            val heading=clean(item.optString("title"),100);val text=clean(item.optString("text"),1000)
            if(ids.isEmpty()||heading.isBlank()||text.isBlank())null else OrganizedSection(heading,text,ids)
        }}.orEmpty()
        val questions=mutableListOf<AnalyzedQuestion>()
        data.optJSONArray("questions")?.let{a->for(i in 0 until minOf(a.length(),16)){
            val item=a.optJSONObject(i)?:continue;val line=byId[item.optLong("source")]?:continue
            val position=snapshot.indexOfFirst{it.id==line.id}
            val nearby=snapshot.drop(position).take(3)
            val joined=nearby.joinToString(" "){it.text}
            val quote=AnalysisEvidence.literal(joined,item.optString("evidence"))?:continue
            val start=joined.indexOf(quote);if(start<0||start>=line.text.length)continue
            var offset=0
            val evidenceSources=nearby.filter{part->val begin=offset;offset+=part.text.length+1;begin<start+quote.length&&offset>start}.map{it.id}
            val origin=item.optString("origin");if(origin !in setOf("heard","study"))continue
            val suggested=origin=="study";if(!suggested&&!AnalysisEvidence.hasQuestion(quote))continue
            val question=clean(item.optString("question"),241);val answer=clean(item.optString("answer"),1200)
            if(!AnalysisEvidence.coherent(question)||answer.isBlank())continue
            val duplicate=questions.indexOfFirst{AnalysisEvidence.sameQuestion(it.question,question)}
            if(duplicate>=0){if(!suggested&&questions[duplicate].suggested)questions.removeAt(duplicate)else continue}
            if(suggested&&questions.count{it.suggested}>=4)continue
            questions.add(AnalyzedQuestion(question,answer,line.id,quote,suggested,evidenceSources))
        }}
        val summary=clean(data.optString("summary"),3500)
        require(summary.isNotBlank()||sections.isNotEmpty()||questions.isNotEmpty()){"El análisis quedó vacío. Se conservó la organización anterior."}
        return AnalysisResult(ClassOrganization(title,topics,sections,snapshot.associate{it.id to it.revision}),summary,questions)
    }
}
