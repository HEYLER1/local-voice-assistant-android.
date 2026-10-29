package com.heyler.voicelab

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class PuterConnectionActivity:ComponentActivity(){
    private val runtime get()=(application as AssistantApplication).puter
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val frame=FrameLayout(this);setContentView(frame);lifecycleScope.launch{runtime.show(this@PuterConnectionActivity,frame)}}
    override fun onDestroy(){runtime.detach(this);super.onDestroy()}
}
