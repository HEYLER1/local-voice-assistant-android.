package com.heyler.voicelab

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

data class ConversationEntry(val id:Long,val title:String,val updated:Long,val fragments:Int=0,val questions:Int=0)
class ConversationStore(context:Context):SQLiteOpenHelper(context,"conversations.db",null,1){
    override fun onCreate(db:SQLiteDatabase){db.execSQL("CREATE TABLE sessions(id INTEGER PRIMARY KEY, title TEXT NOT NULL, updated INTEGER NOT NULL, data TEXT NOT NULL)")}
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){}
    fun list():List<ConversationEntry> = readableDatabase.rawQuery("SELECT id,title,updated,data FROM sessions ORDER BY updated DESC",null).use{c->buildList{while(c.moveToNext()){val data=JSONObject(c.getString(3));add(ConversationEntry(c.getLong(0),c.getString(1),c.getLong(2),data.optJSONArray("lines")?.length()?:0,data.optJSONArray("answers")?.length()?:0))}}}
    fun save(id:Long,state:AssistantState){
        if(state.lines.isEmpty()&&state.answers.isEmpty())return
        val data=JSONObject().put("summary",state.summary)
        data.put("lines",JSONArray().apply{state.lines.forEach{put(JSONObject().put("id",it.id).put("text",it.text).put("revision",it.revision).put("voice",it.voice))}})
        data.put("answers",JSONArray().apply{state.answers.forEach{put(JSONObject().put("id",it.id).put("source",it.source?:JSONObject.NULL).put("revision",it.revision).put("question",it.question).put("text",it.text).put("status",it.status).put("first",it.firstTextMs?:JSONObject.NULL))}})
        val title=if(state.lines.isNotEmpty())KnowledgeOrganizer.build(state.lines).topic else (state.answers.firstOrNull()?.question?:"Conversación").take(90)
        writableDatabase.insertWithOnConflict("sessions",null,ContentValues().apply{put("id",id);put("title",title);put("updated",System.currentTimeMillis());put("data",data.toString())},SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun load(id:Long):AssistantState?=readableDatabase.rawQuery("SELECT data FROM sessions WHERE id=?",arrayOf(id.toString())).use{c->if(!c.moveToFirst())null else{
        val data=JSONObject(c.getString(0));val lines=data.getJSONArray("lines");val answers=data.getJSONArray("answers")
        AssistantState(lines=List(lines.length()){i->val l=lines.getJSONObject(i);SpeechLine(l.getLong("id"),l.getString("text"),true,l.getInt("revision"),l.getString("voice"),true)},answers=List(answers.length()){i->val a=answers.getJSONObject(i);val status=a.getString("status");LiveAnswer(a.getString("id"),if(a.isNull("source"))null else a.getLong("source"),a.getInt("revision"),a.getString("question"),a.getString("text"),if(status=="En espera"||status.startsWith("Preparando")||status.startsWith("Respondiendo"))"Interrumpida al cerrar; texto conservado"else status,if(a.isNull("first"))null else a.getDouble("first"))},summary=data.optString("summary"))
    }}
    fun delete(id:Long){writableDatabase.delete("sessions","id=?",arrayOf(id.toString()))}
}
