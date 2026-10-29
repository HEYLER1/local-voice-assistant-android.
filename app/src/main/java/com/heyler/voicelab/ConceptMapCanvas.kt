package com.heyler.voicelab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/** The map contains readable concepts and relationships; every branch remains a touch target. */
@Composable internal fun ConceptMapCanvas(topic:String,nodes:List<SummaryCanvasNode>,onOpen:(SummaryCanvasNode?)->Unit,modifier:Modifier,tools:@Composable ()->Unit,preview:String){
    var enlarged by rememberSaveable{mutableStateOf(false)}
    ConceptViewport(topic,nodes,onOpen,modifier,tools,preview,onExpand={enlarged=true})
    if(enlarged)Dialog(onDismissRequest={enlarged=false},properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(color=VoiceBackground,modifier=Modifier.fillMaxSize()){
            ConceptViewport(topic,nodes,onOpen,Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),tools,preview,onClose={enlarged=false})
        }
    }
}

@Composable private fun ConceptViewport(topic:String,nodes:List<SummaryCanvasNode>,onOpen:(SummaryCanvasNode?)->Unit,modifier:Modifier,tools:@Composable ()->Unit,preview:String,onExpand:(()->Unit)?=null,onClose:(()->Unit)?=null){
    val density=LocalDensity.current
    val measurer=rememberTextMeasurer()
    val headingStyle=TextStyle(fontSize=18.sp,lineHeight=23.sp,fontWeight=FontWeight.SemiBold)
    val subtitleStyle=TextStyle(fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.SemiBold)
    val bodyStyle=TextStyle(fontSize=14.sp,lineHeight=20.sp)
    val cardWidth=388.dp
    fun textHeight(text:String,style:TextStyle,width:Int)=with(density){measurer.measure(AnnotatedString(text),style,constraints=Constraints(maxWidth=width)).size.height.toDp()}
    val textWidth=with(density){(cardWidth-32.dp).roundToPx()}
    val heights=nodes.map{node->
        var height=32.dp+textHeight(node.title,headingStyle,textWidth)+14.dp
        node.branch?.points?.forEach{point->
            height+=textHeight(point.subtitle,subtitleStyle,textWidth-with(density){58.dp.roundToPx()})+
                textHeight(point.text,bodyStyle,textWidth)+14.dp
        }
        height.coerceAtLeast(160.dp)
    }
    val rowTop=buildList{var y=208.dp;for(row in 0 until (nodes.size+1)/2){add(y);y+=maxOf(heights[row*2],heights.getOrNull(row*2+1)?:0.dp)+24.dp}}
    val worldHeight=(rowTop.lastOrNull()?:208.dp)+(heights.takeLast(if(nodes.size%2==0)2 else 1).maxOrNull()?:0.dp)+24.dp
    val worldWidth=860.dp
    var zoom by rememberSaveable{mutableFloatStateOf(1f)}
    var pan by remember{mutableStateOf(Offset.Zero)}
    Surface(modifier,color=VoiceBackground){BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().semantics{contentDescription="Lienzo del resumen";stateDescription="Zoom ${(zoom*100).roundToInt()}%"}){
        val width=with(density){maxWidth.toPx()};val height=with(density){maxHeight.toPx()}
        val fit=(maxWidth/worldWidth).coerceAtMost(1f)
        val scale=fit*zoom
        val base=Offset((width-with(density){worldWidth.toPx()}*fit)/2,with(density){56.dp.toPx()})
        fun bounded(value:Offset,nextZoom:Float=zoom)=Offset(value.x.coerceIn(-width*3,width*3),value.y.coerceIn(-with(density){worldHeight.toPx()}*fit*nextZoom,height*.8f))
        fun change(nextValue:Float){val next=nextValue.coerceIn(.5f,3.5f);val center=Offset(width/2,height/2)-base;pan=bounded(center-(center-pan)*(next/zoom),next);zoom=next}
        Canvas(Modifier.fillMaxSize()){
            val step=28.dp.toPx();var x=12.dp.toPx()
            while(x<size.width){var y=12.dp.toPx();while(y<size.height){drawCircle(VoiceAccent.copy(alpha=.08f),.6.dp.toPx(),Offset(x,y));y+=step};x+=step}
        }
        Box(Modifier.fillMaxSize().pointerInput(width,height){detectTransformGestures{centroid,delta,factor,_->
            val next=(zoom*factor).coerceIn(.5f,3.5f);val local=centroid-base
            pan=bounded(local-(local-pan)*(next/zoom)+delta,next);zoom=next
        }}){
            Box(Modifier.wrapContentSize(Alignment.TopStart,unbounded=true).requiredSize(worldWidth,worldHeight).graphicsLayer{
                scaleX=scale;scaleY=scale;translationX=base.x+pan.x;translationY=base.y+pan.y;transformOrigin=TransformOrigin(0f,0f)
            }){
                Canvas(Modifier.fillMaxSize()){
                    val root=Offset(size.width/2,168.dp.toPx())
                    nodes.forEachIndexed{index,_->
                        val y=(rowTop[index/2]+26.dp).toPx()
                        val side=if(index%2==0)412.dp.toPx()else 448.dp.toPx()
                        val path=Path().apply{moveTo(root.x,root.y);cubicTo(root.x,y,root.x,y,side,y)}
                        drawPath(path,if(index%2==0)VoiceAccent.copy(alpha=.6f)else VoiceGreen.copy(alpha=.55f),style=Stroke(3.dp.toPx()))
                    }
                }
                Surface(onClick={onOpen(null)},shape=RoundedCornerShape(28.dp),color=Color(0xFF2D2643),border=androidx.compose.foundation.BorderStroke(1.5.dp,VoiceAccent.copy(alpha=.7f)),modifier=Modifier.offset(220.dp,16.dp).width(420.dp).height(152.dp).semantics{contentDescription="Abrir síntesis del resumen"}){
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp,Alignment.CenterVertically),horizontalAlignment=Alignment.CenterHorizontally){
                        Text(topic,style=TextStyle(fontSize=24.sp,lineHeight=29.sp,fontWeight=FontWeight.Bold),textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=2)
                        Text(preview,style=TextStyle(fontSize=13.sp,lineHeight=18.sp),color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=3,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                nodes.forEachIndexed{index,node->
                    val tint=if(index%2==0)VoiceAccent else VoiceGreen
                    Surface(onClick={onOpen(node)},shape=RoundedCornerShape(20.dp),color=Color(0xFF1B2030),border=androidx.compose.foundation.BorderStroke(1.2.dp,tint.copy(alpha=.45f)),modifier=Modifier.offset(if(index%2==0)24.dp else 448.dp,rowTop[index/2]).width(cardWidth).height(heights[index]).semantics{contentDescription="Abrir ${node.title}"}){
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                            Text(node.title,style=headingStyle,color=tint)
                            node.branch?.points?.forEach{point->Column(verticalArrangement=Arrangement.spacedBy(3.dp)){
                                Row(verticalAlignment=Alignment.CenterVertically){
                                    Text(point.subtitle,style=subtitleStyle,modifier=Modifier.weight(1f))
                                    Text(when(point.origin){"research"->"FUENTE";"model"->"IA";else->"AUDIO"},fontSize=9.sp,color=tint,modifier=Modifier.padding(start=8.dp))
                                }
                                Text(point.text,style=bodyStyle,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }}
                        }
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.TopStart).padding(4.dp),verticalAlignment=Alignment.CenterVertically){tools()}
        IconButton(onClick={if(onClose!=null)onClose()else onExpand?.invoke()},modifier=Modifier.align(Alignment.TopEnd).padding(4.dp).semantics{contentDescription=if(onClose!=null)"Cerrar lienzo ampliado"else "Ampliar lienzo del resumen"}){VoiceGlyph(if(onClose!=null)"close"else "expand",MaterialTheme.colorScheme.onSurfaceVariant,Modifier.size(20.dp))}
        Surface(Modifier.align(Alignment.BottomEnd).padding(8.dp),shape=RoundedCornerShape(50),color=Color(0xFF202538)){
            Row(verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick={change(zoom-.25f)},enabled=zoom>.5f,modifier=Modifier.semantics{contentDescription="Alejar resumen"}){VoiceGlyph("minus",VoiceAccent,Modifier.size(17.dp))}
                IconButton(onClick={zoom=1f;pan=Offset.Zero},modifier=Modifier.semantics{contentDescription="Restablecer vista del resumen"}){VoiceGlyph("fit",VoiceAccent,Modifier.size(17.dp))}
                IconButton(onClick={change(zoom+.25f)},enabled=zoom<3.5f,modifier=Modifier.semantics{contentDescription="Acercar resumen"}){VoiceGlyph("add",VoiceAccent,Modifier.size(17.dp))}
            }
        }
    }}
}
