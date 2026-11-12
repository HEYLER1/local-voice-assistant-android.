package com.heyler.voicelab

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Mirrors conversation records to the signed-in user's private Firestore path. */
internal class CloudHistorySync(private val firestore:FirebaseFirestore=FirebaseFirestore.getInstance()) {
    private fun conversations(uid:String)=firestore.collection("users").document(uid).collection("conversations")
    private fun savedItems(uid:String)=firestore.collection("users").document(uid).collection("savedItems")

    suspend fun merge(uid:String,store:ConversationStore,savedStore:SavedStore):Int {
        val remote=conversations(uid).get().awaitValue()
        val local=store.cloudRecords().associateBy{it.id}
        val localDeletes=store.cloudDeletions()
        val remoteIds=remote.documents.mapNotNull{it.id.toLongOrNull()}.toSet()
        var downloaded=0
        for(document in remote.documents){
            val id=document.id.toLongOrNull()?:continue
            val localRecord=local[id]
            val remoteUpdated=document.getLong("updated")?:0L
            val localDeletedAt=localDeletes[id]
            if(document.getBoolean("deleted")==true){
                if(localDeletedAt!=null&&localDeletedAt>remoteUpdated)markDeleted(uid,id,localDeletedAt)
                else store.removeFromCloud(id)
                continue
            }
            if(localDeletedAt!=null&&localDeletedAt>=remoteUpdated){markDeleted(uid,id,localDeletedAt);continue}
            if(localDeletedAt!=null)store.clearCloudDeletion(id)
            if(localRecord!=null&&localRecord.updated>remoteUpdated){
                upload(uid,localRecord)
            }else if(localRecord==null||remoteUpdated>localRecord.updated){
                val payload=downloadPayload(document.reference,document.getLong("chunkCount")?.toInt()?:0)
                store.upsertCloud(CloudConversationRecord(id,document.getString("title").orEmpty(),remoteUpdated,document.getLong("fragments")?.toInt()?:0,document.getLong("questions")?.toInt()?:0,document.getString("folderName"),payload))
                downloaded++
            }
        }
        local.values.filter{it.id !in remoteIds}.forEach{record->
            val deletedAt=localDeletes[record.id]
            if(deletedAt!=null&&deletedAt>record.updated)markDeleted(uid,record.id,deletedAt)else upload(uid,record)
        }
        localDeletes.filterKeys{it !in remoteIds&&it !in local.keys}.forEach{(id,deletedAt)->markDeleted(uid,id,deletedAt)}
        return downloaded+mergeSavedItems(uid,savedStore)
    }

    private suspend fun mergeSavedItems(uid:String,store:SavedStore):Int{
        val remote=savedItems(uid).get().awaitValue()
        val local=store.list().associateBy{it.cloudId}
        val localDeletes=store.cloudDeletions()
        val remoteIds=remote.documents.map{it.id}.toSet()
        var downloaded=0
        for(document in remote.documents){
            val id=document.id
            val localItem=local[id]
            val remoteUpdated=document.getLong("updated")?:0L
            val localDeletedAt=localDeletes[id]
            if(document.getBoolean("deleted")==true){
                if(localDeletedAt!=null&&localDeletedAt>remoteUpdated)deleteSaved(uid,id,localDeletedAt)else store.removeFromCloud(id)
                continue
            }
            if(localDeletedAt!=null&&localDeletedAt>=remoteUpdated){deleteSaved(uid,id,localDeletedAt);continue}
            if(localDeletedAt!=null)store.clearCloudDeletion(id)
            if(localItem!=null&&localItem.updated>remoteUpdated)uploadSaved(uid,localItem)
            else if(localItem==null||remoteUpdated>localItem.updated){
                store.upsertCloud(SavedItem(-1L,document.getString("kind").orEmpty(),document.getString("text").orEmpty(),document.getString("scope").orEmpty(),document.getString("due").orEmpty(),document.getBoolean("done")==true,document.getLong("created")?:remoteUpdated,document.getString("source").orEmpty(),id,remoteUpdated))
                downloaded++
            }
        }
        local.values.filter{it.cloudId !in remoteIds}.forEach{item->
            val deletedAt=localDeletes[item.cloudId]
            if(deletedAt!=null&&deletedAt>item.updated)deleteSaved(uid,item.cloudId,deletedAt)else uploadSaved(uid,item)
        }
        localDeletes.filterKeys{it !in remoteIds&&it !in local.keys}.forEach{(id,deletedAt)->deleteSaved(uid,id,deletedAt)}
        return downloaded
    }

    suspend fun upload(uid:String,record:CloudConversationRecord){
        val root=conversations(uid).document(record.id.toString())
        val chunkCollection=root.collection("chunks")
        val pieces=record.payload.chunked(CHUNK_CHAR_LIMIT)
        val old=root.get().awaitValue()
        val oldHashes=(old.get("chunkHashes") as? List<*>)?.map{it?.toString().orEmpty()}.orEmpty()
        val hashes=pieces.map(::sha256)
        pieces.withIndex().filter{(index,piece)->oldHashes.getOrNull(index)!=hashes[index]}.toList().chunked(MAX_BATCH_WRITES).forEach{group->
            val batch=firestore.batch()
            group.forEach{(index,piece)->batch.set(chunkCollection.document(index.toString().padStart(5,'0')),mapOf("index" to index,"payload" to piece))}
            batch.commit().awaitValue()
        }
        if((old.getLong("chunkCount")?:0L)>pieces.size||(old.getBoolean("deleted")==true))runCatching{
            val old=chunkCollection.get().awaitValue()
            old.documents.filter{(it.getLong("index")?:-1L)>=pieces.size||it.getLong("index")==null}.chunked(MAX_BATCH_WRITES).forEach{group->
                if(group.isNotEmpty()){val batch=firestore.batch();group.forEach{batch.delete(it.reference)};batch.commit().awaitValue()}
            }
        }
        root.set(mapOf("schema" to 1,"title" to record.title,"updated" to record.updated,"fragments" to record.fragments,"questions" to record.questions,"folderName" to record.folderName,"chunkCount" to pieces.size,"chunkHashes" to hashes,"deleted" to false)).awaitValue()
    }

    suspend fun delete(uid:String,id:Long){
        markDeleted(uid,id,System.currentTimeMillis())
    }
    suspend fun uploadSaved(uid:String,item:SavedItem){
        savedItems(uid).document(item.cloudId).set(mapOf("schema" to 1,"kind" to item.kind,"text" to item.text,"scope" to item.scope,"due" to item.due,"done" to item.done,"created" to item.created,"source" to item.source,"updated" to item.updated,"deleted" to false)).awaitValue()
    }
    suspend fun deleteSaved(uid:String,id:String,deletedAt:Long){savedItems(uid).document(id).set(mapOf("schema" to 1,"updated" to deletedAt,"deleted" to true)).awaitValue()}

    private suspend fun markDeleted(uid:String,id:Long,deletedAt:Long){
        val root=conversations(uid).document(id.toString())
        runCatching{
            val chunks=root.collection("chunks").get().awaitValue()
            chunks.documents.chunked(MAX_BATCH_WRITES).forEach{group->
                if(group.isNotEmpty()){val batch=firestore.batch();group.forEach{batch.delete(it.reference)};batch.commit().awaitValue()}
            }
        }
        root.set(mapOf("schema" to 1,"updated" to deletedAt,"deleted" to true,"chunkCount" to 0,"chunkHashes" to emptyList<String>())).awaitValue()
    }

    private suspend fun downloadPayload(root:com.google.firebase.firestore.DocumentReference,count:Int):String {
        require(count in 1..MAX_CHUNKS){"La conversación guardada en la nube está incompleta."}
        return buildString{for(index in 0 until count){
            val chunk=root.collection("chunks").document(index.toString().padStart(5,'0')).get().awaitValue()
            append(chunk.getString("payload")?:error("Falta un fragmento de la conversación en la nube."))
        }}
    }

    private suspend fun <T> Task<T>.awaitValue():T=suspendCancellableCoroutine{continuation->
        addOnSuccessListener{if(continuation.isActive)continuation.resume(it)}
        addOnFailureListener{if(continuation.isActive)continuation.resumeWithException(it)}
        addOnCanceledListener{if(continuation.isActive)continuation.cancel(CancellationException("Operación de Firebase cancelada."))}
    }

    private fun sha256(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}

    private companion object {
        const val CHUNK_CHAR_LIMIT=160_000
        const val MAX_BATCH_WRITES=450
        const val MAX_CHUNKS=10_000
    }
}
