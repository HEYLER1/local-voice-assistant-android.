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
    fun soniqo(context:Context,uri:Uri){
        val temp=File(context.filesDir,"soniqo-import").apply{deleteRecursively();mkdirs()}
        val mandatory=setOf("parakeet-encoder-int8.onnx","parakeet-decoder-joint-int8.onnx","vocab.json","silero-vad.onnx","kokoro-e2e-realtime.onnx","kokoro-e2e.onnx.data","vocab_index.json")
        try{var total=0L;val seen=mutableSetOf<String>();ZipInputStream(checkNotNull(context.contentResolver.openInputStream(uri))).use{input->while(true){val entry=input.nextEntry?:break;require(!entry.isDirectory&&entry.name.length<150&&!entry.name.startsWith("/")&&!entry.name.contains("..")&&seen.add(entry.name)){"Ruta ZIP inválida"};require(seen.size<=64){"Demasiados archivos"};val file=File(temp,entry.name);require(file.canonicalPath.startsWith(temp.canonicalPath+File.separator));file.parentFile?.mkdirs();file.outputStream().use{out->val buffer=ByteArray(65536);while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=2048L*1024*1024){"ZIP supera 2 GB"};out.write(buffer,0,n)}}}}
            require(seen.containsAll(mandatory)){"Faltan archivos del paquete Soniqo"}
            val target=File(context.filesDir,"soniqo");val backup=File(context.filesDir,"soniqo-backup");backup.deleteRecursively();if(target.exists())check(target.renameTo(backup));if(!temp.renameTo(target)){backup.renameTo(target);error("No se pudo instalar el paquete")};backup.deleteRecursively()
        }finally{temp.deleteRecursively()}
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
