package com.heyler.voicelab

/** A source-backed outline, shared by incremental analysis and the readable summary. */
internal object KnowledgeSummary {
    fun cleanTitle(text:String)=ConversationNaming.saved("",text).takeUnless{it==ConversationNaming.UNKNOWN}.orEmpty()
    fun heading(text:String):String {
        val title=text.trim().removePrefix("#").trim().replace(Regex("\\s+")," ").trim(' ',':','.')
        val key=ConversationRules.key(title)
        if(title.length !in 4..80||title.split(' ').size>12||title.any{it in "¿?"}||
            Regex("(?:tema|seccion|parte|punto|capitulo)(?: \\d+)?").matches(key)||
            key in setOf("resumen","introduccion","desarrollo","conclusion","conclusiones","otros","contenido","ideas principales"))return ""
        return title.replaceFirstChar{it.uppercase()}
    }
    fun previousSections(state:AssistantState)=state.organization.sections.filter{note->
        note.sources.isNotEmpty()&&note.sources.all{id->state.lines.any{line->
            line.id==id&&(line.final||line.edited)&&(state.organization.covered[id]?.let{it==line.revision}?:true)
        }}
    }

    fun merge(previous:AssistantState,incoming:ClassOrganization):ClassOrganization {
        val notes=previousSections(previous).toMutableList()
        incoming.sections.forEach{section->
            val keys=(section.replaces+section.title).map{ConversationRules.key(it)}.toSet()
            val matches=notes.withIndex().filter{ConversationRules.key(it.value.title) in keys}
            val position=matches.firstOrNull()?.index?:notes.size
            val sources=(matches.flatMap{it.value.sources}+section.sources).distinct()
            // A legacy/malformed response without a replacement reference cannot discard notes
            // the model might not have received in its bounded context.
            val text=if(section.replaces.isNotEmpty()||matches.isEmpty())section.text else {
                val paragraphs=matches.map{it.value.text}+section.text
                paragraphs.distinct().filter{part->paragraphs.none{it!=part&&it.contains(part)}}.joinToString("\n\n")
            }
            matches.asReversed().forEach{notes.removeAt(it.index)}
            notes.add(position.coerceAtMost(notes.size),section.copy(text=text,sources=sources,replaces=emptyList()))
        }
        // Never silently evict an earlier topic to make room for the latest block.
        val subject=incoming.title.ifBlank{previous.organization.title}
        return incoming.copy(title=subject,topics=(previous.organization.topics+incoming.topics).distinct(),
            sections=notes,covered=previous.organization.covered+incoming.covered,map=previous.organization.map)
    }
    fun title(state:AssistantState)=ConversationNaming.analysisTitle(state)
        .ifBlank{ConversationNaming.automatic(state.copy(automaticTitle=""))}
        .ifBlank{cleanTitle(state.automaticTitle)}.ifBlank{ConversationNaming.UNKNOWN}

    fun document(state:AssistantState)=buildList {
        add(title(state))
        if(state.summary.isNotBlank())add(state.summary.trim())
        state.organization.sections.forEach{add(it.title+"\n"+it.text)}
    }.joinToString("\n\n")
}
