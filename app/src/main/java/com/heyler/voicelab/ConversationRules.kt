package com.heyler.voicelab

import java.text.Normalizer

data class QuestionSpan(val text:String,val start:Int,val sourceText:String?=null)
object ConversationRules {
    fun folded(text:String)=Normalizer.normalize(text.lowercase(),Normalizer.Form.NFD).replace(Regex("\\p{M}"),"")
    fun key(text:String)=folded(text).replace(Regex("[^a-z0-9 ]")," ").replace(Regex("\\s+")," ").trim()
    private val cues=Regex("\\b(?:no\\s+se\\s+si|me\\s+pregunto\\s+si|queria\\s+saber\\s+si|alguien\\s+sabe\\s+si|por\\s+que|para\\s+que|como\\s+(?:es\\s+que|se|puede|puedo|funciona|ocurre|podemos|deben|debemos|sabemos|sabian|seria|hacer|hace|sucede|calcula|mide|llama)|de\\s+que\\s+es\\s+lo\\s+que|en\\s+que\\s+es\\s+lo\\s+que|que\\s+(?:cosa|riesgo|riesgos|medida|medidas|controles|actividades|herramienta|herramientas|acciones|factores|tipo|tipos|parte|voy|vamos|debo|deben|debemos|deberia|tengo|tenemos|puedo|puede|pueden|podemos|podriamos|hacemos|causa|causan|significa(?:\\s+esto)?|son|es(?:\\s+lo\\s+que)?|diferencia|ocurre|sucede|hace|implica|representa)|cual(?:es)?(?:\\s+(?:es|son|fue|serian|la|el))?|cuando\\s+(?:ocurre|sera|se|empieza|termina|se\\s+escoge)|cuant[oa]s?\\b|que\\s+tan|quien(?:es)?(?:\\s+(?:es|son|fue|hace|puede))?|donde(?:\\s+(?:esta|queda|ocurre|se|viene|aparece))?|de\\s+donde|a\\s+donde|desde\\s+cuando|hasta\\s+cuando|en\\s+que|con\\s+que|a\\s+quien)\\b")
    /** Strong clause transitions that stop ASR from merging a question with its explanation. */
    private val narration=Regex("\\b(?:pero|porque|por\\s+eso|asi\\s+que|sin\\s+embargo|entonces|luego|despues|ademas|ok(?:ay)?|claro|bueno|pues|a\\s+ver|vamos\\s+camino|y\\s+tambien|y\\s+(?:esto|eso|ahi|aqui|ya|por\\s+ejemplo|por\\s+que|como\\s+(?:es|se)|cual\\s+es)|ya\\s+(?:sabemos|aparece|tenemos)|que\\s+no\\s+solamente|no\\s+solamente|no\\s+esta\\s+mal|correcto(?:\\s+entonces)?|es\\s+justamente|la\\s+razon\\s+es\\s+que|por\\s+ejemplo)\\b")
    private val secondQuestion=Regex("\\b(?:en|y|pero)\\s+(?:que|como|cual(?:es)?|cuando|donde|cuant[oa]s?|quien(?:es)?)\\b")
    private val shortQuestionLeadIn=Regex("(?:^|\\b)(?:y|pero|bueno|ahora|entonces|no|dime|pregunta|pregunto|a ver)$")
    private val reportingQuestionLeadIn=Regex("(?:^|\\b)(?:me preguntan|me preguntaban|nos preguntan|nos preguntaban|preguntaron|preguntaba|preguntaban|quiero saber|nos interesa saber)(?: [a-z0-9]+){0,4}$")
    private val visualQuestionLeadIn=Regex("(?:^|\\b)mira(?: [a-z0-9]+){0,4}$")
    private val predicates=Regex("\\b(?:es|son|fue|sera|era|eran|esta|estan|estaba|estaban|recibe|reciben|sabemos|sabia|causa|causan|ocurre|ocurren|ocurrio|funciona|funcionan|calcula|calcular|mide|miden|medir|resuelve|resolver|puede|puedo|pueden|podemos|podria|podriamos|debo|debes|debemos|deben|deberia|voy|vamos|significa|significan|tiene|tienen|cambia|cambian|explica|explicar|hace|hacen|hacer|propone|proponer|plantea|plantear|dejo|dejas|deja|dejan|dejamos|produce|producen|sucede|suceden|existe|existen|sirve|sirven|utiliza|utilizan|determina|determinan|hay|se|has|han|visto|vio|observado|tenido|sido|queda|viene|proviene|permite|permiten|llama|llaman|depende|necesita|requiere|bloquea|bloquean|bloquear|cubre|cubren|cubrir|tapa|tapan|afecta|afectan|provoca|provocan|genera|generan|incluye|incluyen|representa|representan|contiene|contienen)\\b")
    private val filler=setOf("que","por","como","cual","cuando","es","son","fue","se","el","la","los","las","un","una","de","del","en","y","a","no","esto","eso","asi","aqui","ahi","todo","algo","pero","raro","funciona","puede","puedo","calcula")
    fun questions(text:String)=spans(text).map{it.text}
    fun spans(text:String,allowContextual:Boolean=false,relaxImplicitStarts:Boolean=false):List<QuestionSpan>{
        val found=mutableListOf<QuestionSpan>()
        fun add(raw:String,start:Int,explicit:Boolean=false,cue:String=""){
            val startAllowed=explicit||questionStartAllowed(text,start,cue)||(relaxImplicitStarts&&credibleMidSentenceStart(text,start,cue))
            if(!startAllowed)return
            val normalizedRaw=folded(raw)
            val discourseBoundary=narration.find(normalizedRaw)?.range?.first?:raw.length
            val repeatedQuestionBoundary=if(explicit)null else secondQuestion.find(normalizedRaw,minOf(12,normalizedRaw.length))?.range?.first
            val nextCompleteQuestionBoundary=if(explicit)null else cues.findAll(normalizedRaw).firstOrNull{next->
                val gap=next.range.first
                if(gap<18)return@firstOrNull false
                val absolute=start+gap
                val prefix=key(raw.substring(0,gap))
                val prefixWords=prefix.split(' ').filter{it.isNotBlank()}
                val hasQuestionShape=prefixWords.size>=6&&cues.containsMatchIn(prefix)&&predicates.containsMatchIn(prefix)&&prefixWords.filter{it !in filler}.distinct().size>=2
                hasQuestionShape&&(questionStartAllowed(text,absolute,next.value)||(relaxImplicitStarts&&credibleMidSentenceStart(text,absolute,next.value)))
            }?.range?.first
            val boundary=minOf(discourseBoundary,repeatedQuestionBoundary?:raw.length,nextCompleteQuestionBoundary?:raw.length)
            val sourcePhrase=raw.take(boundary).trim().trim(',', ';', ':').replace(Regex("\\s+")," ")
            val phrase=polishASRQuestion(sourcePhrase)
            val normalized=key(phrase)
            val words=normalized.split(" ")
            if(words.lastOrNull() in setOf("un","una","el","la","los","las","de","del","en","para","con","y","o","que","por","se","es","son","hay","si","pues","verdad","correcto","tambien","debo","debes","deben","puedo","puede","pueden","podemos","quiero","quiere","quieren","va","van","su","sus"))return
            if(Regex("^(?:que hay|que es|como es|por que es|como se)$").matches(normalized))return
            if(Regex("^no se si se (?:explica|entiende)$").matches(normalized))return
            if(words.filter{it !in filler}.distinct().size<2 && words.size>5)return
            if(words.size !in 3..(if(explicit)36 else 22)||phrase.length>220||(!explicit&&!cues.containsMatchIn(key(sourcePhrase)))||!predicates.containsMatchIn(key(phrase)))return
            if(words.distinct().size<3||words.none{it !in filler}||words.distinct().size.toDouble()/words.size<.45)return
            if(Regex("^(?:que es perfecto(?: |$)|que la hay$|estas siendo|pero que|por que no$)").containsMatchIn(key(phrase)))return
            found.add(QuestionSpan("¿$phrase?",start,sourcePhrase))
        }
        Regex("¿([^¿?]+)\\?").findAll(text).forEach{add(it.groupValues[1],it.range.first,true)}
        if(allowContextual){
            val short=Regex("¿(?:como (?:se (?:calcula|resuelve|aplica|demuestra)|funciona)(?: esto| eso)?|por que (?:ocurre|sucede|pasa)(?: esto| eso)?|que (?:significa|representa) (?:esto|eso))\\?")
            short.findAll(folded(text)).forEach{match->found.add(QuestionSpan(text.substring(match.range),match.range.first))}
        }
        cues.findAll(folded(text)).forEach{m->
            val tail=text.substring(m.range.first)
            add(tail.takeWhile{it !in "?.!¿"},m.range.first,cue=m.value)
        }
        val accepted=found.distinctBy{key(it.text)}
        return accepted.filter{candidate->accepted.none{other->key(other.text)!=key(candidate.text)&&key(other.text).contains(key(candidate.text))}}.sortedBy{it.start}.takeLast(8)
    }
    fun salient(text:String):List<String> = text.split(Regex("(?<=[.!?])\\s+"))
        .map{it.trim()}.filter{key(it).split(" ").size>=8}
        .sortedByDescending{sentence->Regex("\\b(?:decidi|acord|importante|porque|significa|consiste|ocurre|objetivo|resultado|pregunta|tarea|trabajo|examen|evaluacion|definicion|ejercicio)").findAll(key(sentence)).count()}
        .distinctBy{key(it)}.take(4)

    /** Accept implicit questions only at a clause/segment start or after a clear question lead-in. */
    private fun questionStartAllowed(text:String,start:Int,cue:String):Boolean{
        if(start<=0)return true
        val prefix=text.substring(0,start)
        val trimmed=prefix.trimEnd()
        if(trimmed.isEmpty()||trimmed.lastOrNull() in setOf('\n','\r','?','!','.',';',','))return true
        val tail=key(trimmed.takeLast(100))
        if(cue.startsWith("que ")&&tail.split(' ').lastOrNull()=="lo"&&!cue.startsWith("que significa esto"))return false
        if(shortQuestionLeadIn.containsMatchIn(tail))return true
        if(tail.endsWith(" y")&&cues.findAll(folded(trimmed)).any()&&predicates.containsMatchIn(key(trimmed)))return true
        if(tail.endsWith(" nada")&&(cue.startsWith("cual")||cue.startsWith("que es lo que")||cue.startsWith("que significa")))return true
        if(reportingQuestionLeadIn.containsMatchIn(tail)||visualQuestionLeadIn.containsMatchIn(tail))return true
        // This common classroom construction is a real request for clarification: “subió; ¿qué significa esto?”
        if(cue.startsWith("que significa esto")&&tail.split(' ').lastOrNull() !in importanceWords)return true
        if(cue.startsWith("en que")&&cues.findAll(folded(prefix)).any())return true
        // ASR commonly drops the question mark in “ahora, ¿de qué…?”
        if(tail.endsWith("ahora de"))return true
        return false
    }
    /** Broader live-audio mode: retain coherent indirect questions even inside a long spoken turn. */
    private fun credibleMidSentenceStart(text:String,start:Int,cue:String):Boolean{
        val before=key(text.substring((start-100).coerceAtLeast(0),start))
        if(Regex("\\b(?:hemos\\s+definido|definimos|hemos\\s+dicho|dijimos|la\\s+definicion\\s+de|el\\s+concepto\\s+de|se\\s+define|lo|de\\s+lo)\\s*$").containsMatchIn(before))return false
        val normalizedCue=key(cue)
        // "qué significa esto" and "qué es lo que..." are common spoken questions
        // embedded after a short topic clause, even with no pause or punctuation.
        if(normalizedCue.startsWith("no se si")||normalizedCue.startsWith("me pregunto si")||normalizedCue.startsWith("queria saber si")||normalizedCue.startsWith("alguien sabe si"))return true
        if(normalizedCue.startsWith("que significa esto")||normalizedCue.startsWith("que es lo que"))return true
        // A wh-word alone is not enough: it often appears inside definitions and explanations.
        // Allow embedded action questions such as "qué controles voy a proponer", but require
        // a question-like verb frame rather than accepting any following noun phrase.
        if(normalizedCue.startsWith("que ")){
            val following=key(text.substring(start,(start+100).coerceAtMost(text.length)))
            if(Regex("^que\\s+(?:cosa|controles|actividades|herramientas|acciones|medidas|riesgos|factores)(?:\\s+[a-z0-9]+){0,3}\\s+(?:voy|vamos|debo|debemos|deben|puedo|podemos|pueden|puede|podriamos|hacemos)\\b").containsMatchIn(following))return true
            return false
        }
        return questionStartAllowed(text,start,cue)
    }
    /** Remove a common ASR false start while retaining the exact transcript quote separately. */
    private fun polishASRQuestion(source:String):String = source.replace(
        Regex("^(de|en) (que|qué|cual|cuál|cuales|cuáles) es lo que (?:yo )?debo\\b",RegexOption.IGNORE_CASE),
        "$1 $2 debo"
    )
    private val importanceWords=setOf("y","pero","bueno","ahora","entonces","no","de","la","el","los","las","un","una","que","como","es","son","a","en","con","por","para")
}
