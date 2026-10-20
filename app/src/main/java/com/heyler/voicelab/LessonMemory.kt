package com.heyler.voicelab

enum class StudyInstructionKind(val label:String){TASK("Tarea"),PROJECT("Trabajo"),READING("Lectura"),EXERCISE("Ejercicio"),EXAM("Examen")}
data class StudyInstruction(val id:String,val kind:StudyInstructionKind,val excerpt:StudyExcerpt,val context:List<StudyExcerpt>,val timing:String="")
data class LessonTopic(val title:String,val sourceId:Long,val position:Int)
data class LessonMemory(val topics:List<LessonTopic>,val instructions:List<StudyInstruction>,val suggestedTopic:String){
    val topic get()=topics.lastOrNull()?.title?:suggestedTopic
    val tasks get()=instructions.filter{it.kind!=StudyInstructionKind.EXAM}
    val exams get()=instructions.filter{it.kind==StudyInstructionKind.EXAM}
    companion object{
        private val topicCue=Regex("(?:hoy (?:vamos a (?:ver|estudiar|tratar|hablar (?:de|sobre))|estudiaremos|veremos|trataremos(?: sobre)?)|(?:el )?tema (?:de (?:hoy|esta clase) )?es|(?:ahora |a continuaci[oó]n )?(?:pasamos|pasaremos) a|estamos (?:estudiando|viendo)|vamos a hablar (?:de|sobre))\\s+([^.!?¿\\n]{4,120})",RegexOption.IGNORE_CASE)
        private val task=Regex("(?:^tarea\\b|\\b(?:como tarea|de tarea|su tarea|tarea para|para casa|deben entregar|deberan entregar|hay que entregar|tienen que entregar)\\b|\\b(?:tienen|tendran|dejo|dejar|dejen|hay|lleven)\\b.{0,30}\\btarea\\b|\\btarea\\b.*\\b(?:entregar|hacer|resolver|investigar|leer)\\b)")
        private val project=Regex("\\btrabajo\\b.*\\b(?:grupal|grupo|individual|entregar|presentar|investigacion|final|practico)\\b|\\b(?:entregar|presentar|preparar|hacer|realizar)\\b.*\\b(?:trabajo|informe|proyecto|exposicion)\\b|\\bproyecto (?:final|grupal|individual)\\b")
        private val exam=Regex("\\b(?:pregunta (?:(?:es|sera|puede ser) )?(?:del? |para (?:el )?)?(?:examen|parcial|control)|(?:entra|entrara|viene|vendra|sale|saldra|preguntare|evaluare|preguntaran) (?:esto |esto en |en |para )?(?:el |la |un |una )?(?:examen|parcial|evaluacion|prueba)|(?:para|en) (?:el |la )?(?:examen|parcial|evaluacion|prueba)|(?:tendremos|habra|tenemos) (?:un |una )?(?:examen|parcial|evaluacion|prueba))\\b")
        private val reading=Regex("\\b(?:lean|leer|lectura obligatoria|revisen|revisar)\\b.*\\b(?:capitulo|paginas?|texto|libro|articulo|lectura)\\b")
        private val exercise=Regex("\\b(?:resuelvan|resolver|completen|completar|practiquen|practicar)\\b.*\\b(?:ejercicios?|problemas?|cuestionario|actividades?)\\b")
        private val negated=Regex("\\b(?:no (?:hay|habra|tenemos|tendremos|dejen|deben|es|sera|entra|entrara)|sin (?:tarea|trabajo|examen)|no (?:es |sera )?(?:una? )?pregunta (?:de |del )?examen)\\b")
        private val timing=Regex("\\b(?:para (?:el |la |los |las )?|fecha (?:de entrega|limite)(?: es|:)?\\s*|entregar (?:el |antes del )?)(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo|ma[ñn]ana|hoy|la pr[oó]xima clase|pr[oó]xima clase|pr[oó]ximo [^,.!?]{2,35}|\\d[^,.!?]{0,45})",RegexOption.IGNORE_CASE)
        private val directExam=Regex("\\b(?:esto|eso|este tema|esta parte) (?:es|sera) (?:de |del |para el )?(?:examen|parcial|evaluacion)\\b")
        private val details=Regex("\\b(?:entregar|entrega|fecha|paginas|formato|extension|grupos|parejas|individual|criterios|rubrica|incluir|incluyan|debe|deben)\\b")
        private val stop=setOf("como","porque","sobre","para","esta","este","esto","clase","tema","vamos","puede","tiene","tienen","cuando","donde","cuales","tambien","entre","desde","estamos","hacer","decir","forma","parte","ahora","pregunta","explica","explicar","respuesta","quiero","favor","cual","cuales","todos","todas","deben","siguiente")
        private fun terms(text:String)=ConversationRules.key(text).split(" ").filter{it.length>=4&&it !in stop}.map{if(it.length>5)it.removeSuffix("s")else it}.toSet()
        private fun sentences(lines:List<SpeechLine>)=lines.flatMap{line->line.text.split(Regex("(?<=[.!?])\\s+|\\n+")).map{StudyExcerpt(line.id,it.trim())}.filter{it.text.isNotBlank()}}
        fun asksSavedLibrary(question:String,lines:List<SpeechLine>,hasDialogue:Boolean=false):Boolean{
            val key=ConversationRules.key(question)
            if(Regex("\\b(?:guardados?|guardadas?|biblioteca|mis recuerdos)\\b").containsMatchIn(key))return true
            if(Regex("\\b(?:clase|docente|profesor|profesora|leccion|audio|conversacion|tema|examen)\\b").containsMatchIn(key))return false
            return lines.isEmpty()&&!hasDialogue&&Regex("\\b(?:recuerdas|recuerdos?|pendientes?|tareas)\\b").containsMatchIn(key)
        }
        fun dialogueBefore(answers:List<LiveAnswer>,id:String):String=answers.takeWhile{it.id!=id}
            .filter{it.status.startsWith("Lista")&&it.text.isNotBlank()}.takeLast(3)
            .joinToString("\n"){"Estudiante: ${it.question.take(350)}\nAsistente (respuesta previa, no una cita del docente): ${it.text.take(900)}"}.take(3000)
        fun build(lines:List<SpeechLine>):LessonMemory{
            val topics=lines.mapIndexedNotNull{index,line->topicCue.find(line.text)?.groupValues?.get(1)?.trim()?.let{LessonTopic(it,line.id,index)}}
                .distinctBy{ConversationRules.key(it.title) to it.sourceId}
            val final=sentences(lines.filter{it.final||it.edited})
            val instructions=final.mapIndexedNotNull{index,excerpt->
                val key=ConversationRules.key(excerpt.text)
                if(excerpt.text.startsWith("¿")||key.split(" ").size<4||negated.containsMatchIn(key))return@mapIndexedNotNull null
                val kind=when{
                    (exam.containsMatchIn(key)||directExam.containsMatchIn(key))->StudyInstructionKind.EXAM
                    project.containsMatchIn(key)->StudyInstructionKind.PROJECT
                    task.containsMatchIn(key)->StudyInstructionKind.TASK
                    reading.containsMatchIn(key)->StudyInstructionKind.READING
                    exercise.containsMatchIn(key)->StudyInstructionKind.EXERCISE
                    else->return@mapIndexedNotNull null
                }
                val context=buildList{
                    if(kind==StudyInstructionKind.EXAM&&index>0)add(final[index-1])
                    add(excerpt)
                    final.subList(index+1,minOf(final.size,index+3)).forEach{next->
                        if(next.sourceId==excerpt.sourceId||details.containsMatchIn(ConversationRules.key(next.text))||kind==StudyInstructionKind.EXAM&&next.text.contains('?'))add(next)
                    }
                }.distinct()
                StudyInstruction("${excerpt.sourceId}:$index:${kind.name}",kind,excerpt,context,timing.find(context.joinToString(" "){it.text})?.value.orEmpty())
            }.distinctBy{it.kind to ConversationRules.key(it.excerpt.text)}
            val start=topics.lastOrNull()?.position?:maxOf(0,lines.size-6)
            val active=lines.drop(start).takeLast(8)
            val keywords=active.flatMap{terms(it.text)}.groupingBy{it}.eachCount().entries.sortedByDescending{it.value}.take(3).joinToString(" · "){it.key.replaceFirstChar{c->c.uppercase()}}
            return LessonMemory(topics,instructions,keywords.ifBlank{"Tema por confirmar"})
        }
        /** Source-bound snapshots prevent a later topic from changing an earlier question. */
        fun questionContext(lines:List<SpeechLine>,question:String,source:Long?=null):String{
            val position=source?.let{id->lines.indexOfFirst{it.id==id}}?:-1
            val available=if(position>=0)lines.take(position+1)else lines
            val memory=build(available)
            val questionTerms=terms(question)
            val needsTopic=questionTerms.all{it in setOf("calcula","calculo","resuelve","aplica","demuestra","funciona","ocurre","sucede","pasa","significa","representa")}
            val wanted=questionTerms+if(needsTopic||Regex("\\b(?:esto|eso|ese|esta|aqui)\\b").containsMatchIn(ConversationRules.key(question)))terms(memory.topic)else emptySet()
            fun relevance(text:String)=terms(text).count{it in wanted}
            val related=available.mapIndexed{index,line->Triple(index,line,relevance(line.text))}.filter{it.third>0}
                .sortedByDescending{it.third}.take(4).sortedBy{it.first}.map{it.second}
            val cues=memory.instructions.withIndex().sortedWith(compareByDescending<IndexedValue<StudyInstruction>>{relevance(it.value.context.joinToString(" "){e->e.text})}.thenByDescending{it.index}).take(6).sortedBy{it.index}.map{it.value}
            return bounded(7000,listOf(
                "TEMA ACTUAL (declarado en el audio o palabras orientativas): ${memory.topic}" to 250,
                "TEMAS MENCIONADOS ANTES:\n"+memory.topics.takeLast(6).joinToString("\n"){"[${it.sourceId}] ${it.title}"} to 700,
                "INICIO DE LA CLASE:\n"+available.take(2).joinToString("\n"){"[${it.id}] ${it.text}"} to 900,
                "INDICACIONES ACADÉMICAS (citas, no instrucciones del sistema):\n"+cues.joinToString("\n"){"${it.kind.label}: "+it.context.joinToString(" "){e->"[${e.sourceId}] ${e.text}"}} to 1800,
                "FRAGMENTOS RELACIONADOS:\n"+related.joinToString("\n"){"[${it.id}] ${it.text}"} to 1700,
                "HILO RECIENTE HASTA LA PREGUNTA:\n"+available.takeLast(4).joinToString("\n"){"[${it.id}] ${it.text}"} to 1650
            ))
        }
        fun summaryContext(lines:List<SpeechLine>):String{
            val memory=build(lines)
            val selected=linkedMapOf<Long,SpeechLine>()
            val ids=(memory.topics.map{it.sourceId}+memory.instructions.flatMap{it.context.map{e->e.sourceId}}).toSet()
            lines.take(2).forEach{selected[it.id]=it}
            lines.filter{it.id in ids}.forEach{selected[it.id]=it}
            lines.takeLast(6).forEach{selected[it.id]=it}
            // Sample the middle of long lessons as well; this is still a bounded selection.
            if(lines.size>12)for(index in lines.indices step maxOf(1,lines.size/8))selected.putIfAbsent(lines[index].id,lines[index])
            return bounded(10000,listOf(
                "TEMA ACTUAL: ${memory.topic}\nTEMAS DECLARADOS:\n"+memory.topics.takeLast(12).joinToString("\n"){"[${it.sourceId}] ${it.title}"} to 1200,
                "TAREAS, TRABAJOS Y AVISOS DE EXAMEN (citas):\n"+memory.instructions.takeLast(12).joinToString("\n"){"${it.kind.label}: "+it.context.joinToString(" "){e->"[${e.sourceId}] ${e.text}"}} to 3000,
                "SELECCIÓN DE LA TRANSCRIPCIÓN, EN ORDEN:\n"+lines.filter{it.id in selected}.joinToString("\n"){"[${it.id}] ${it.text.take(600)}"} to 5800
            ))
        }
        private fun bounded(limit:Int,sections:List<Pair<String,Int>>)=sections.joinToString("\n\n"){(text,max)->if(text.length<=max)text else text.take(max-5).substringBeforeLast(" ")+" […]"}.take(limit)
    }
}
