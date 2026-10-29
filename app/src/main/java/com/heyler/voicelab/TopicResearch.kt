package com.heyler.voicelab

import okhttp3.HttpUrl.Companion.toHttpUrl

// Kept only to read maps saved by an earlier build; there is no Wikipedia HTTP client.
data class ResearchSource(val id:String,val title:String,val url:String,val excerpt:String,val accessedAt:Long,
    val publisher:String="Wikipedia",val license:String="CC BY-SA 4.0")
internal object TopicResearch {
    fun validUrl(url:String):Boolean=runCatching{val u=url.toHttpUrl();u.scheme=="https"&&u.host=="es.wikipedia.org"&&u.encodedPath.startsWith("/wiki/")&&u.username.isEmpty()&&u.password.isEmpty()}.getOrDefault(false)
}
