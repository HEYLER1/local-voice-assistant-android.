package com.heyler.voicelab

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase

// Explicitly selected texts. Conversation history uses a separate private database; audio is never stored.
data class SavedItem(val id:Long,val kind:String,val text:String,val scope:String,val due:String,val done:Boolean,val created:Long,val source:String)
class SavedStore(context:Context):SQLiteOpenHelper(context,"selected-text.db",null,2){
    override fun onCreate(db:SQLiteDatabase){db.execSQL("CREATE TABLE selected(id INTEGER PRIMARY KEY, kind TEXT NOT NULL, text TEXT NOT NULL, scope TEXT NOT NULL, due TEXT NOT NULL, done INTEGER NOT NULL, created INTEGER NOT NULL, source TEXT NOT NULL DEFAULT 'Seleccionado por usuario')")}
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){if(oldVersion<2)db.execSQL("ALTER TABLE selected ADD COLUMN source TEXT NOT NULL DEFAULT 'Seleccionado por usuario'")}
    fun list():List<SavedItem> = readableDatabase.query("selected",null,null,null,null,null,"created DESC").use{c->buildList{while(c.moveToNext())add(SavedItem(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getInt(5)==1,c.getLong(6),c.getString(7)))}}
    fun save(id:Long?,kind:String,text:String,scope:String,due:String)=saveWithSource(id,kind,text,scope,due,"Texto escrito por usuario")
    fun saveWithSource(id:Long?,kind:String,text:String,scope:String,due:String,source:String){require(text.isNotBlank());val v=ContentValues().apply{put("kind",kind);put("text",text.trim());put("scope",scope.trim());put("due",due.trim());put("source",source)};if(id==null){v.put("done",0);v.put("created",System.currentTimeMillis());writableDatabase.insertOrThrow("selected",null,v)}else writableDatabase.update("selected",v,"id=?",arrayOf(id.toString()))}
    fun delete(id:Long){writableDatabase.delete("selected","id=?",arrayOf(id.toString()))}
    fun done(id:Long,value:Boolean){writableDatabase.update("selected",ContentValues().apply{put("done",if(value)1 else 0)},"id=?",arrayOf(id.toString()))}
}
