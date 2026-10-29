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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/** A view of existing notes and source-backed excerpts, not inferred causal relationships. */
internal data class SummaryCanvasNode(
    val id:String,val title:String,val detail:String,
    val note:OrganizedSection?=null,val category:Int?=null,val branch:MapBranch?=null
)

@Composable internal fun SummaryCanvas(topic:String,nodes:List<SummaryCanvasNode>,onOpen:(SummaryCanvasNode?)->Unit,modifier:Modifier,tools:@Composable ()->Unit,preview:String=""){
    if(nodes.any{it.branch!=null}){
        ConceptMapCanvas(topic,nodes,onOpen,modifier,tools,preview)
        return
    }
    var enlarged by rememberSaveable{mutableStateOf(false)}
    CanvasViewport(topic,nodes,onOpen,modifier,{enlarged=true},tools=tools,preview=preview)
    if(enlarged)Dialog(onDismissRequest={enlarged=false},properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize(),color=VoiceBackground){
            CanvasViewport(topic,nodes,onOpen,Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),onClose={enlarged=false},tools=tools,preview=preview)
        }
    }
}

@Composable private fun CanvasViewport(topic:String,nodes:List<SummaryCanvasNode>,onOpen:(SummaryCanvasNode?)->Unit,modifier:Modifier,onEnlarge:(()->Unit)?=null,onClose:(()->Unit)?=null,tools:@Composable ()->Unit,preview:String){
    var zoom by rememberSaveable{mutableFloatStateOf(1f)}
    var pan by remember{mutableStateOf(Offset.Zero)}
    val percent=(zoom*100).roundToInt()
    val density=LocalDensity.current
    Surface(modifier,color=VoiceBackground){
        BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().semantics{contentDescription="Lienzo del resumen";stateDescription="Zoom $percent%"}){
            val worldWidth=maxWidth.coerceAtLeast(320.dp)
            val rootExtra=if(preview.isBlank())0 else 48
            val rootWidth=(worldWidth-48.dp).coerceAtMost(280.dp)
            val worldHeight=(196+rootExtra+((nodes.size+1)/2-1).coerceAtLeast(0)*96).dp
            val viewWidth=with(density){maxWidth.toPx()}
            val viewHeight=with(density){maxHeight.toPx()}
            val baseY=with(density){48.dp.toPx()}+((viewHeight-with(density){(worldHeight+96.dp).toPx()})/2).coerceAtLeast(0f)
            fun bounded(value:Offset,scale:Float)=Offset(value.x.coerceIn(-viewWidth*scale,viewWidth*scale),value.y.coerceIn(-viewHeight*scale,viewHeight*scale))
            fun changeZoom(value:Float){
                val next=value.coerceIn(.75f,2.5f)
                val center=Offset(viewWidth/2,viewHeight/2-baseY)
                pan=bounded(center-(center-pan)*(next/zoom),next);zoom=next
            }
            Canvas(Modifier.fillMaxSize()){
                drawRect(Brush.radialGradient(listOf(VoiceAccent.copy(alpha=.035f),Color.Transparent),center=Offset(size.width*.5f,size.height*.25f),radius=size.width*.9f))
                val step=28.dp.toPx()
                var x=12.dp.toPx()
                while(x<size.width){var y=12.dp.toPx();while(y<size.height){drawCircle(Color(0xFF66708D).copy(alpha=.10f),.5.dp.toPx(),Offset(x,y));y+=step};x+=step}
            }
            Box(Modifier.fillMaxSize().pointerInput(viewWidth,viewHeight){
                detectTransformGestures{centroid,delta,scale,_->
                    val next=(zoom*scale).coerceIn(.75f,2.5f)
                    val local=centroid-Offset(0f,baseY)
                    pan=bounded(local-(local-pan)*(next/zoom)+delta,next);zoom=next
                }
            }){
                Box(Modifier.wrapContentSize(Alignment.TopStart,unbounded=true).requiredSize(worldWidth,worldHeight).graphicsLayer{
                    scaleX=zoom;scaleY=zoom;translationX=pan.x;translationY=pan.y+baseY;transformOrigin=TransformOrigin(0f,0f)
                }){
                    Canvas(Modifier.fillMaxSize()){
                        val root=Offset(size.width/2,(96+rootExtra).dp.toPx())
                        if(nodes.isNotEmpty())drawLine(VoiceAccent.copy(alpha=.24f),root,Offset(root.x,(158+rootExtra+(nodes.size-1)/2*96).dp.toPx()),strokeWidth=1.2.dp.toPx())
                        nodes.forEachIndexed{index,node->
                            val target=Offset(size.width/2+if(index%2==0)(-8).dp.toPx()else 8.dp.toPx(),(158+rootExtra+index/2*96).dp.toPx())
                            val path=Path().apply{moveTo(root.x,target.y-10.dp.toPx());quadraticTo(root.x,target.y,target.x,target.y)}
                            drawPath(path,if(node.category==4||node.category==5)VoiceGreen.copy(alpha=.4f)else VoiceAccent.copy(alpha=.35f),style=Stroke(1.4.dp.toPx()))
                        }
                    }
                    Surface(onClick={onOpen(null)},modifier=Modifier.width(rootWidth).height((80+rootExtra).dp).offset(x=(worldWidth-rootWidth)/2,y=16.dp).semantics{contentDescription="Abrir síntesis del resumen"},shape=RoundedCornerShape(20.dp),color=Color(0xFF2D2643),border=androidx.compose.foundation.BorderStroke(1.dp,VoiceAccent.copy(alpha=.55f))){
                        Column(Modifier.padding(horizontal=14.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp,Alignment.CenterVertically),horizontalAlignment=Alignment.CenterHorizontally){
                            Text(topic,fontSize=18.sp,lineHeight=23.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                            if(preview.isNotBlank())Text(preview,fontSize=12.sp,lineHeight=17.sp,maxLines=2,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    nodes.forEachIndexed{index,node->
                        val tint=if(node.category==4||node.category==5)VoiceGreen else VoiceAccent
                        Surface(onClick={onOpen(node)},modifier=Modifier.offset(x=if(index%2==0)16.dp else worldWidth/2+8.dp,y=(120+rootExtra+index/2*96).dp).width(worldWidth/2-24.dp).height(76.dp).semantics{contentDescription="Abrir ${node.title}"},shape=RoundedCornerShape(16.dp),color=Color(0xFF1B2030),border=androidx.compose.foundation.BorderStroke(1.dp,tint.copy(alpha=.23f))){
                            Column(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
                                Text(node.title,fontSize=15.sp,lineHeight=19.sp,fontWeight=FontWeight.Medium,maxLines=if(node.category==null)3 else 2,overflow=TextOverflow.Ellipsis)
                                if(node.category!=null)Text(node.detail,fontSize=11.sp,lineHeight=13.sp,color=tint,maxLines=1,overflow=TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Row(Modifier.align(Alignment.TopStart).padding(start=8.dp,top=4.dp),verticalAlignment=Alignment.CenterVertically){tools()}
            if(onClose!=null)IconButton(onClick=onClose,modifier=Modifier.align(Alignment.TopEnd).padding(4.dp).size(40.dp).semantics{contentDescription="Cerrar lienzo ampliado"}){VoiceGlyph("close",MaterialTheme.colorScheme.onSurfaceVariant,Modifier.size(19.dp))}
            if(onEnlarge!=null)IconButton(onClick=onEnlarge,modifier=Modifier.align(Alignment.TopEnd).padding(4.dp).size(36.dp).semantics{contentDescription="Ampliar lienzo del resumen"}){VoiceGlyph("expand",MaterialTheme.colorScheme.onSurfaceVariant,Modifier.size(20.dp))}
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp){
            Row(Modifier.align(Alignment.BottomEnd).padding(8.dp),verticalAlignment=Alignment.CenterVertically){
                Surface(color=Color(0xFF202538),shape=RoundedCornerShape(50)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        IconButton(onClick={changeZoom(zoom-.25f)},enabled=zoom>.75f,modifier=Modifier.size(32.dp).semantics{contentDescription="Alejar resumen"}){VoiceGlyph("minus",MaterialTheme.colorScheme.onSurfaceVariant,Modifier.size(16.dp))}
                        IconButton(onClick={zoom=1f;pan=Offset.Zero},modifier=Modifier.size(32.dp).semantics{contentDescription="Restablecer vista del resumen"}){VoiceGlyph("fit",MaterialTheme.colorScheme.onSurfaceVariant,Modifier.size(16.dp))}
                        IconButton(onClick={changeZoom(zoom+.25f)},enabled=zoom<2.5f,modifier=Modifier.size(32.dp).semantics{contentDescription="Acercar resumen"}){VoiceGlyph("add",MaterialTheme.colorScheme.onSurfaceVariant,Modifier.size(16.dp))}
                    }
                }
            }
            }
        }
    }
}
