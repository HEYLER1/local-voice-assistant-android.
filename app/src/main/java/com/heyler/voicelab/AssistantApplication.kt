package com.heyler.voicelab

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** One conversation session shared by the activity and the recording service. No activity references. */
class AssistantApplication:Application(),ViewModelStoreOwner {
    internal val puter by lazy{PuterRuntime(this)}
    override val viewModelStore=ViewModelStore()
    val assistant:AssistantViewModel by lazy {
        ViewModelProvider(this,ViewModelProvider.AndroidViewModelFactory.getInstance(this))[AssistantViewModel::class.java]
    }
}
