package com.heyler.voicelab
import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream

object ModelImport {
    private val required=setOf("adapter.ort","cross_kv.ort","decoder_kv.ort","encoder.ort","frontend.model.ort","frontend.weights.ort","streaming_config.json","tokenizer.bin")
    fun speech(context:Context,uri:Uri){
        val temporary=File(context.filesDir,"speech-import").apply{deleteRecursively();mkdirs()}
        try {
            var total=0L; val seen=mutableSetOf<String>()
            ZipInputStream(checkNotNull(context.contentResolver.openInputStream(uri))).use { input ->
                while(true){val entry=input.nextEntry?:break
                    require(entry.name in required && !entry.isDirectory && seen.add(entry.name)) {"ZIP inválido: solo se admiten los ocho archivos del modelo español."}
                    File(temporary,entry.name).outputStream().use { output ->
                        val buffer=ByteArray(65536)
                        while(true){val count=input.read(buffer);if(count<0)break;total+=count;require(total<=256L*1024*1024){"ZIP demasiado grande."};output.write(buffer,0,count)}
                    }
                }
            }
            require(seen==required){"Faltan archivos del modelo español."}
            val target=File(context.filesDir,"speech");target.deleteRecursively();check(temporary.renameTo(target))
        } finally {temporary.deleteRecursively()}
    }
    fun language(context:Context,uri:Uri){
        val temporary=File(context.filesDir,"model-import")
        try {
            context.contentResolver.openInputStream(uri).use {input ->
                requireNotNull(input)
                temporary.outputStream().use{output ->val buffer=ByteArray(1024*1024);var total=0L;while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=2L*1024*1024*1024){"Modelo demasiado grande."};output.write(buffer,0,n)}}
            }
            require(temporary.length()>1024*1024){"El modelo no es válido."}
            val target=File(context.filesDir,"model.litertlm");target.delete();check(temporary.renameTo(target))
        } finally {temporary.delete()}
    }
}
