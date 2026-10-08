package com.heyler.voicelab

import audio.soniqo.speech.*
import kotlinx.coroutines.*
import java.io.File

/** CPU baseline; no SDK downloads and no echo synthesis. */
internal class SoniqoSpeech(directory:File,scope:CoroutineScope,private val onText:(Long,String,Boolean)->Unit,private val onError:(String)->Unit):AutoCloseable{
    private val pipeline=SpeechPipeline(SpeechConfig(modelDir=directory.path,useNnapi=false,sttModel=SttModel.PARAKEET,ttsModel=TtsModel.KOKORO_SHORT_TURN,pipelineMode=PipelineMode.TRANSCRIBE_ONLY,language="auto",enableEnhancer=false,emitPartialTranscriptions=true,partialTranscriptionInterval=.4f,endOfSpeechSilenceSec=1f))
    private var id=System.nanoTime()
    private var latest=""
    private var closed=false
    private val collector=scope.launch(start=CoroutineStart.UNDISPATCHED){pipeline.events.collect{event->if(!closed)when(event){
        is SpeechEvent.PartialTranscription->{latest=event.text;if(latest.isNotBlank())onText(id,latest,false)}
        is SpeechEvent.TranscriptionCompleted->{latest=event.text;if(latest.isNotBlank())onText(id,latest,true);latest="";id++;pipeline.resumeListening()}
        is SpeechEvent.Error->onError(event.message)
        else->Unit
    }}}
    fun start(){pipeline.start()}
    fun addAudio(samples:FloatArray){pipeline.pushAudio(samples)}
    override fun close(){if(closed)return;closed=true;pipeline.stop();collector.cancel();if(latest.isNotBlank())onText(id,latest,true);pipeline.close()}
}
