package com.heyler.voicelab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Floating controls share the reading surface; the transcript scrolls beneath their soft edges. */
@Composable internal fun ConversationStage(
    header:@Composable ()->Unit,
    composer:@Composable ()->Unit,
    content:@Composable (PaddingValues)->Unit
){
    var top by remember{mutableIntStateOf(0)}
    var bottom by remember{mutableIntStateOf(0)}
    val density=LocalDensity.current
    Box(Modifier.fillMaxSize().background(VoiceBackground)){
        content(PaddingValues(top=with(density){top.toDp()},bottom=with(density){bottom.toDp()}))
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged{top=it.height}
            .background(Brush.verticalGradient(0f to VoiceBackground,.75f to VoiceBackground.copy(alpha=.98f),1f to Color.Transparent))
            .statusBarsPadding().padding(bottom=16.dp)){header()}
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged{bottom=it.height}
            .background(Brush.verticalGradient(0f to Color.Transparent,.2f to VoiceBackground.copy(alpha=.98f),1f to VoiceBackground))
            .padding(top=16.dp)){composer()}
    }
}
