package com.heyler.voicelab
import org.junit.Assert.*
import org.junit.Test
class NumericMentionsTest {
    @Test fun keepsLiteralAmountsDecimalsAndYears(){val text="750 euros, 3,5 metros, 12 % y 1995.";assertEquals(listOf("750","3,5","12 %","1995"),NumericMentions.spans(text).map{text.substring(it)})}
    @Test fun highlightsWordsWithoutInventingDigits(){val text="setecientos cincuenta euros";assertEquals(listOf("setecientos","cincuenta"),NumericMentions.spans(text).map{text.substring(it)});assertTrue(NumericMentions.spans("datos y amigos").isEmpty())}
}
