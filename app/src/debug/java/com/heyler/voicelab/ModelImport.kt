package com.heyler.voicelab

import android.content.Context
import android.net.Uri
import java.io.File

/** Imports the optional Qwen model used only by the internal metrics lab. */
object ModelImport {
    fun language(context: Context, uri: Uri) {
        val temporary = File(context.filesDir, "model-import")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 2L * 1024 * 1024 * 1024) { "Modelo demasiado grande." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(temporary.length() > 1024 * 1024) { "El modelo no es válido." }
            val target = File(context.filesDir, "model.litertlm")
            target.delete()
            check(temporary.renameTo(target))
        } finally {
            temporary.delete()
        }
    }
}
