package com.heyler.voicelab

/** Highlights literal mentions for review; never infers or replaces a quantity. */
object NumericMentions {
    private val pattern=Regex("(?<![\\p{L}\\d])(?:\\d+(?:[.,]\\d+)*(?:\\s*%)?|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|dieciséis|dieciseis|diecisiete|dieciocho|diecinueve|veinte|treinta|cuarenta|cincuenta|sesenta|setenta|ochenta|noventa|cien|ciento|doscientos|trescientos|cuatrocientos|quinientos|seiscientos|setecientos|ochocientos|novecientos|mil|millón|millon|millones)(?![\\p{L}\\d])",RegexOption.IGNORE_CASE)
    fun spans(text:String)=pattern.findAll(text).map{it.range}.toList()
}
