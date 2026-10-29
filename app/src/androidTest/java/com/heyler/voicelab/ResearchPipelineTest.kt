package com.heyler.voicelab

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ResearchPipelineTest {
    @Test fun unsupportedFactsAndInventedReferencesAreExcluded(){
        val data=JSONObject(ResearchFixtures.response());val branch=data.getJSONArray("branches").getJSONObject(0)
        val points=branch.getJSONArray("points")
        val invented=JSONObject(points.getJSONObject(0).toString()).put("text","El hemisferio norte siempre recibe un porcentaje exacto de eclipses.").put("origin","research").put("evidence","Esa afirmación no figura en la fuente.")
        points.put(invented)
        points.put(JSONObject(points.getJSONObject(0).toString()).put("origin","research").put("references",JSONArray().put("R999")))
        val map=ResearchedMapProtocol.parse(data.toString(),"Eclipses solares",ResearchFixtures.lines,emptyList())
        assertEquals(1,map.branches.first().points.size)
        assertFalse(map.branches.flatMap{it.points}.any{it.text.contains("porcentaje")})
        assertEquals(8,map.branches.size)
    }
    @Test fun originalAudioAndExternalKnowledgeRetainSeparateProvenance(){
        val data=JSONObject(ResearchFixtures.response());data.getJSONArray("branches").getJSONObject(0).getJSONArray("points").put(
            JSONObject().put("subtitle","La explicación grabada").put("text","La Luna puede proyectar su sombra sobre nuestro planeta.").put("origin","recording")
                .put("sources",JSONArray().put(1)).put("references",JSONArray().put("R1")).put("evidence","La Luna se interpone entre la Tierra y el Sol"))
        val point=ResearchedMapProtocol.parse(data.toString(),"Eclipses solares",ResearchFixtures.lines,emptyList()).branches.first().points.last()
        assertEquals(listOf(9901L),point.recording);assertTrue(point.references.isEmpty())
    }
    @Test fun mapsAndCitationsReopenWithinTheirOwnConversation(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="research-map-test-${System.nanoTime()}.db";val store=ConversationStore(context,name)
        try{
            val state=ResearchFixtures.state();store.save(91,state)
            store.save(92,state.copy(organization=ClassOrganization()))
            assertEquals(state.organization.map,store.load(91)!!.organization.map)
            assertNull(store.load(92)!!.organization.map)
            assertTrue(ResearchedMapDocument.text(store.load(91)!!.organization.map!!).contains("Ampliación de IA"))
        }finally{store.close();context.deleteDatabase(name)}
    }
}
