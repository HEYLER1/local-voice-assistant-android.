package com.heyler.voicelab
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.json.*
import java.io.File
import kotlin.math.*
class NumericRecognitionBenchmarkTest {
    @Test fun cleanAndSyntheticInterferenceReport(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val engines=LocalEngines(context);val rows=JSONArray()
        val references=listOf("El precio es de setecientos cincuenta euros.","En mil novecientos noventa y cinco ocurrió este evento.","La medida es tres coma cinco metros y el descuento es doce por ciento.")
        val expected=listOf(listOf(listOf("750","setecientos cincuenta")),listOf(listOf("1995","mil novecientos noventa y cinco")),listOf(listOf("3 5","tres coma cinco"),listOf("12","doce")))
        try{engines.loadSpeech();for(i in 1..3){val bytes=context.assets.open("fixtures/numbers-$i.wav").use{it.readBytes()};val clean=Wav16.read(bytes);bytes.fill(0)
            for(noisy in listOf(false,true)){val audio=clean.copyOf();if(noisy){val rms=sqrt(clean.sumOf{(it*it).toDouble()}/clean.size);val amplitude=rms/sqrt(10.0)*sqrt(2.0/3.0);audio.indices.forEach{n->val t=n/16000.0;audio[n]=(audio[n]+amplitude*(sin(2*PI*440*t)+sin(2*PI*1000*t)+sin(2*PI*2600*t))).coerceIn(-1.0,1.0).toFloat()}}
                val start=android.os.SystemClock.elapsedRealtime();val text=engines.transcribe(audio);audio.fill(0f);val normalized=" ${ConversationRules.key(text)} ";val hits=expected[i-1].count{alternatives->alternatives.any{normalized.contains(" $it ")}}
                rows.put(JSONObject().put("fixture",i).put("reference",references[i-1]).put("condition",if(noisy)"three-tones-10dB-SNR"else "clean").put("recognized",text).put("numeric_mentions_matched",hits).put("numeric_mentions_expected",expected[i-1].size).put("asr_ms",android.os.SystemClock.elapsedRealtime()-start));assertTrue("Native ASR produced empty output",text.isNotBlank())
            };clean.fill(0f)
        }}finally{engines.close()}
        val file=File(context.getExternalFilesDir("tests"),"numeric-recognition-synthetic.json");file.writeText(JSONObject().put("scope","Synthetic Piper speech; additive tones are not music; emulator does not validate the A54 microphone").put("rows",rows).toString(2));assertEquals(6,rows.length())
    }
}
