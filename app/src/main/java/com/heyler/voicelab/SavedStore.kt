package com.heyler.voicelab

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

// Explicitly selected texts and tasks. Audio is never stored.
data class SavedItem(val id:Long,val kind:String,val text:String,val scope:String,val due:String,val done:Boolean,val created:Long,val source:String,val cloudId:String="",val updated:Long=created)
class SavedStore(context:Context,databaseName:String="selected-text.db",private val ownerId:String="legacy"):SQLiteOpenHelper(context,databaseName,null,4){
    override fun onCreate(db:SQLiteDatabase){
        db.execSQL("CREATE TABLE selected(id INTEGER PRIMARY KEY,kind TEXT NOT NULL,text TEXT NOT NULL,scope TEXT NOT NULL,due TEXT NOT NULL,done INTEGER NOT NULL,created INTEGER NOT NULL,source TEXT NOT NULL DEFAULT 'Seleccionado por usuario',owner_id TEXT NOT NULL,cloud_id TEXT NOT NULL,updated INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX selected_owner_created ON selected(owner_id,created DESC)")
        db.execSQL("CREATE UNIQUE INDEX selected_owner_cloud ON selected(owner_id,cloud_id)")
        db.execSQL("CREATE TABLE sync_deletions(cloud_id TEXT NOT NULL,deleted_at INTEGER NOT NULL,owner_id TEXT NOT NULL,PRIMARY KEY(owner_id,cloud_id))")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){
        if(oldVersion<2)db.execSQL("ALTER TABLE selected ADD COLUMN source TEXT NOT NULL DEFAULT 'Seleccionado por usuario'")
        if(oldVersion<3){db.execSQL("ALTER TABLE selected ADD COLUMN owner_id TEXT NOT NULL DEFAULT 'legacy'");db.execSQL("CREATE INDEX selected_owner_created ON selected(owner_id,created DESC)")}
        if(oldVersion<4){
            db.execSQL("ALTER TABLE selected ADD COLUMN cloud_id TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE selected ADD COLUMN updated INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE selected SET cloud_id=lower(hex(randomblob(16))),updated=created WHERE cloud_id=''")
            db.execSQL("CREATE UNIQUE INDEX selected_owner_cloud ON selected(owner_id,cloud_id)")
            db.execSQL("CREATE TABLE sync_deletions(cloud_id TEXT NOT NULL,deleted_at INTEGER NOT NULL,owner_id TEXT NOT NULL,PRIMARY KEY(owner_id,cloud_id))")
        }
    }
    fun list():List<SavedItem> = readableDatabase.query("selected",null,"owner_id=?",arrayOf(ownerId),null,null,"created DESC").use{c->buildList{while(c.moveToNext())add(SavedItem(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getInt(5)==1,c.getLong(6),c.getString(7),c.getString(9),c.getLong(10)))}}
    fun save(id:Long?,kind:String,text:String,scope:String,due:String)=saveWithSource(id,kind,text,scope,due,"Texto escrito por usuario")
    fun saveWithSource(id:Long?,kind:String,text:String,scope:String,due:String,source:String):String{
        require(text.isNotBlank())
        val db=writableDatabase;val now=System.currentTimeMillis();val cloudId=id?.let(::cloudId)?:UUID.randomUUID().toString()
        val values=ContentValues().apply{put("kind",kind);put("text",text.trim());put("scope",scope.trim());put("due",due.trim());put("source",source);put("owner_id",ownerId);put("updated",now)}
        if(id==null){values.put("done",0);values.put("created",now);values.put("cloud_id",cloudId);db.insertOrThrow("selected",null,values)}
        else db.update("selected",values,"id=? AND owner_id=?",arrayOf(id.toString(),ownerId))
        db.delete("sync_deletions","cloud_id=? AND owner_id=?",arrayOf(cloudId,ownerId))
        return cloudId
    }
    fun cloudId(id:Long):String?=readableDatabase.rawQuery("SELECT cloud_id FROM selected WHERE id=? AND owner_id=?",arrayOf(id.toString(),ownerId)).use{if(it.moveToFirst())it.getString(0)else null}
    fun upsertCloud(item:SavedItem){
        val db=writableDatabase
        val values=ContentValues().apply{put("kind",item.kind);put("text",item.text);put("scope",item.scope);put("due",item.due);put("done",if(item.done)1 else 0);put("created",item.created);put("source",item.source);put("owner_id",ownerId);put("cloud_id",item.cloudId);put("updated",item.updated)}
        if(db.update("selected",values,"cloud_id=? AND owner_id=?",arrayOf(item.cloudId,ownerId))==0)db.insertOrThrow("selected",null,values)
        db.delete("sync_deletions","cloud_id=? AND owner_id=?",arrayOf(item.cloudId,ownerId))
    }
    fun cloudDeletions():Map<String,Long> = readableDatabase.rawQuery("SELECT cloud_id,deleted_at FROM sync_deletions WHERE owner_id=?",arrayOf(ownerId)).use{c->buildMap{while(c.moveToNext())put(c.getString(0),c.getLong(1))}}
    fun clearCloudDeletion(cloudId:String){writableDatabase.delete("sync_deletions","cloud_id=? AND owner_id=?",arrayOf(cloudId,ownerId))}
    fun removeFromCloud(cloudId:String){writableDatabase.delete("selected","cloud_id=? AND owner_id=?",arrayOf(cloudId,ownerId));clearCloudDeletion(cloudId)}
    fun delete(id:Long){val db=writableDatabase;val cloudId=cloudId(id);if(ownerId!="legacy"&&!cloudId.isNullOrBlank())db.insertWithOnConflict("sync_deletions",null,ContentValues().apply{put("cloud_id",cloudId);put("deleted_at",System.currentTimeMillis());put("owner_id",ownerId)},SQLiteDatabase.CONFLICT_REPLACE);db.delete("selected","id=? AND owner_id=?",arrayOf(id.toString(),ownerId))}
    fun done(id:Long,value:Boolean){writableDatabase.update("selected",ContentValues().apply{put("done",if(value)1 else 0);put("updated",System.currentTimeMillis())},"id=? AND owner_id=?",arrayOf(id.toString(),ownerId))}
    fun hasLegacyData():Boolean=readableDatabase.rawQuery("SELECT 1 FROM selected WHERE owner_id='legacy' LIMIT 1",null).use{it.moveToFirst()}
    fun assignLegacyDataToCurrentOwner(){require(ownerId!="legacy");writableDatabase.update("selected",ContentValues().apply{put("owner_id",ownerId)},"owner_id='legacy'",null)}
}
