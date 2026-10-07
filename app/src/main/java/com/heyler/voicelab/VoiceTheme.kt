package com.heyler.voicelab

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val VoiceBackground=Color(0xFF0B0E18)
val VoiceAccent=Color(0xFFAE99FF)
val VoiceGreen=Color(0xFF67E2BA)
private val VoiceColors=darkColorScheme(
    primary=VoiceAccent,onPrimary=Color(0xFF201337),primaryContainer=Color(0xFF35264D),onPrimaryContainer=Color(0xFFEDE4FF),
    secondary=VoiceGreen,onSecondary=Color(0xFF062C23),background=VoiceBackground,onBackground=Color(0xFFF3F2F9),
    surface=Color(0xFF141827),onSurface=Color(0xFFF3F2F9),surfaceVariant=Color(0xFF1D2335),onSurfaceVariant=Color(0xFFA6AABD),
    outline=Color(0xFF394055),outlineVariant=Color(0xFF252B3C))
@Composable fun VoiceTheme(content: @Composable () -> Unit){MaterialTheme(colorScheme=VoiceColors,shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(28.dp)),content=content)}

@Composable fun VoiceHeader(listening:Boolean,compact:Boolean=false,starting:Boolean=false){
    Row(Modifier.fillMaxWidth().padding(top=if(compact)4.dp else 12.dp,bottom=if(compact)6.dp else 14.dp),verticalAlignment=Alignment.CenterVertically){
        Image(painterResource(R.drawable.brand_mark),contentDescription="V · voz y conocimiento",modifier=Modifier.size(if(compact)32.dp else 42.dp))
        Column(Modifier.weight(1f).padding(start=12.dp)){Text("Voz local",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);if(!compact)Text("Tu voz, en claro.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Row(Modifier.background(if(listening)VoiceGreen.copy(alpha=.12f) else VoiceAccent.copy(alpha=.12f),RoundedCornerShape(50)).padding(horizontal=12.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(6.dp).background(if(listening)VoiceGreen else VoiceAccent,CircleShape));Spacer(Modifier.width(6.dp))
            Text(if(starting)"PREPARANDO"else if(listening)"EN VIVO" else "LOCAL",style=MaterialTheme.typography.labelSmall,color=if(listening)VoiceGreen else VoiceAccent,fontWeight=FontWeight.Bold)
        }
    }
}
@Composable fun VoiceTabs(selected:Int,onSelect:(Int)->Unit){
    Row(Modifier.fillMaxWidth().background(Color(0xFF141827),RoundedCornerShape(18.dp)).padding(5.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){
        listOf("En vivo","Guardado","Ajustes").forEachIndexed{i,label->Box(Modifier.weight(1f).background(if(selected==i)Color(0xFF302642)else Color.Transparent,RoundedCornerShape(14.dp)).clickable{onSelect(i)}.semantics{this.selected=selected==i;role=Role.Tab}.padding(vertical=12.dp),contentAlignment=Alignment.Center){Text(label,color=if(selected==i)VoiceAccent else MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelLarge,fontWeight=if(selected==i)FontWeight.Bold else FontWeight.Normal)}}
    }
}
@Composable fun VoiceDock(listening:Boolean,level:Float,enabled:Boolean,onPress:()->Unit,compact:Boolean=false){
    val audio by animateFloatAsState(if(listening)level.coerceIn(0f,1f)else 0f,label="nivel real de audio")
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(VoiceBackground.copy(alpha=.92f),Color(0xFF151426))),RoundedCornerShape(topStart=28.dp,topEnd=28.dp)).padding(top=if(compact)2.dp else 10.dp,bottom=if(compact)2.dp else 12.dp),horizontalAlignment=Alignment.CenterHorizontally){
        if(!compact)Canvas(Modifier.width(112.dp).height(16.dp)){
            val count=13;val gap=size.width/count
            for(i in 0 until count){val scale=1f-kotlin.math.abs(i-count/2f)/(count/2f+1);val height=2.dp.toPx()+audio*scale*12.dp.toPx();drawLine(if(listening)VoiceGreen else Color(0xFF414458),Offset(gap*(i+.5f),size.height/2-height/2),Offset(gap*(i+.5f),size.height/2+height/2),strokeWidth=3.dp.toPx(),cap=StrokeCap.Round)}
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(if(compact)60.dp else 96.dp).border((1+audio*3).dp,if(listening)VoiceGreen.copy(alpha=.45f)else VoiceAccent.copy(alpha=.18f),CircleShape).padding(7.dp),contentAlignment=Alignment.Center){
            val action=if(listening)"Pausar escucha" else "Iniciar escucha"
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(if(listening)listOf(Color(0xFF62D8B4),Color(0xFF279B92))else listOf(Color(0xFFC5ACFF),Color(0xFF8360E9))),CircleShape).clickable(enabled=enabled,onClick=onPress).semantics{contentDescription=action;role=Role.Button},contentAlignment=Alignment.Center){
                Canvas(Modifier.size(36.dp)){
                    val ink=if(listening)Color(0xFF072B25)else Color(0xFF241437)
                    if(listening){drawRoundRect(ink,Offset(size.width*.24f,size.height*.2f),Size(size.width*.17f,size.height*.6f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()));drawRoundRect(ink,Offset(size.width*.59f,size.height*.2f),Size(size.width*.17f,size.height*.6f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))}
                    else{drawRoundRect(ink,Offset(size.width*.36f,size.height*.1f),Size(size.width*.28f,size.height*.48f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(size.width*.14f));drawArc(ink,0f,180f,false,Offset(size.width*.22f,size.height*.26f),Size(size.width*.56f,size.height*.43f),style=Stroke(width=2.5.dp.toPx(),cap=StrokeCap.Round));drawLine(ink,Offset(size.width*.5f,size.height*.69f),Offset(size.width*.5f,size.height*.86f),strokeWidth=2.5.dp.toPx(),cap=StrokeCap.Round);drawLine(ink,Offset(size.width*.36f,size.height*.88f),Offset(size.width*.64f,size.height*.88f),strokeWidth=2.5.dp.toPx(),cap=StrokeCap.Round)}
                }
            }
        }
        if(!compact)Text(if(listening)"Toca para pausar"else if(enabled)"Toca para hablar"else "Preparando…",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=5.dp))
    }
}

@Composable fun VoiceGlyph(kind:String,color:Color,modifier:Modifier=Modifier){
    Canvas(modifier){
        val w=size.width;val h=size.height;val stroke=Stroke(width=2.dp.toPx(),cap=StrokeCap.Round)
        when(kind){
            "close"->{drawLine(color,Offset(w*.25f,h*.25f),Offset(w*.75f,h*.75f),strokeWidth=2.dp.toPx(),cap=StrokeCap.Round);drawLine(color,Offset(w*.75f,h*.25f),Offset(w*.25f,h*.75f),strokeWidth=2.dp.toPx(),cap=StrokeCap.Round)}
            "add"->{drawLine(color,Offset(w*.2f,h*.5f),Offset(w*.8f,h*.5f),strokeWidth=2.dp.toPx(),cap=StrokeCap.Round);drawLine(color,Offset(w*.5f,h*.2f),Offset(w*.5f,h*.8f),strokeWidth=2.dp.toPx(),cap=StrokeCap.Round)}
            "search"->{drawCircle(color,w*.27f,Offset(w*.42f,h*.42f),style=stroke);drawLine(color,Offset(w*.63f,h*.63f),Offset(w*.85f,h*.85f),strokeWidth=2.dp.toPx(),cap=StrokeCap.Round)}
            "chat"->{drawRoundRect(color,Offset(w*.12f,h*.15f),Size(w*.76f,h*.58f),androidx.compose.ui.geometry.CornerRadius(w*.14f),style=stroke);drawLine(color,Offset(w*.25f,h*.73f),Offset(w*.25f,h*.9f),strokeWidth=2.dp.toPx());drawLine(color,Offset(w*.25f,h*.9f),Offset(w*.45f,h*.73f),strokeWidth=2.dp.toPx())}
            "save"->{val path=androidx.compose.ui.graphics.Path().apply{moveTo(w*.25f,h*.13f);lineTo(w*.75f,h*.13f);lineTo(w*.75f,h*.88f);lineTo(w*.5f,h*.7f);lineTo(w*.25f,h*.88f);close()};drawPath(path,color,style=stroke)}
            "settings"->{drawCircle(color,w*.18f,Offset(w/2,h/2),style=stroke);for(i in 0 until 8){val angle=i*Math.PI/4;val x=kotlin.math.cos(angle).toFloat();val y=kotlin.math.sin(angle).toFloat();drawLine(color,Offset(w/2+x*w*.31f,h/2+y*h*.31f),Offset(w/2+x*w*.42f,h/2+y*h*.42f),strokeWidth=2.dp.toPx(),cap=StrokeCap.Round)}}
            "spark"->{val path=androidx.compose.ui.graphics.Path().apply{moveTo(w*.5f,h*.08f);lineTo(w*.62f,h*.38f);lineTo(w*.92f,h*.5f);lineTo(w*.62f,h*.62f);lineTo(w*.5f,h*.92f);lineTo(w*.38f,h*.62f);lineTo(w*.08f,h*.5f);lineTo(w*.38f,h*.38f);close()};drawPath(path,color,style=stroke)}
            "pause"->{drawRoundRect(color,Offset(w*.24f,h*.18f),Size(w*.18f,h*.64f),androidx.compose.ui.geometry.CornerRadius(w*.07f));drawRoundRect(color,Offset(w*.58f,h*.18f),Size(w*.18f,h*.64f),androidx.compose.ui.geometry.CornerRadius(w*.07f))}
            else->{drawRoundRect(color,Offset(w*.36f,h*.08f),Size(w*.28f,h*.5f),androidx.compose.ui.geometry.CornerRadius(w*.14f));drawArc(color,0f,180f,false,Offset(w*.2f,h*.24f),Size(w*.6f,h*.46f),style=stroke);drawLine(color,Offset(w*.5f,h*.7f),Offset(w*.5f,h*.88f),strokeWidth=2.dp.toPx(),cap=StrokeCap.Round);drawLine(color,Offset(w*.35f,h*.9f),Offset(w*.65f,h*.9f),strokeWidth=2.dp.toPx(),cap=StrokeCap.Round)}
        }
    }
}
@Composable fun VoiceNavigation(selected:Int,compact:Boolean=false,embedded:Boolean=false,onSelect:(Int)->Unit){
    Row(Modifier.fillMaxWidth().background(Color(0xFF111621)).then(if(embedded)Modifier else Modifier.navigationBarsPadding()).padding(horizontal=18.dp,vertical=if(compact)4.dp else 9.dp)){
        listOf("Conversar" to "chat","Guardado" to "save","Ajustes" to "settings").forEachIndexed{i,(title,icon)->
            Column(Modifier.weight(1f).clickable{onSelect(i)}.semantics{this.selected=selected==i;role=Role.Tab}.padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)){
                VoiceGlyph(icon,if(selected==i)VoiceAccent else Color(0xFF7D899E),Modifier.size(22.dp));Text(title,style=MaterialTheme.typography.labelSmall,color=if(selected==i)VoiceAccent else Color(0xFF7D899E))
            }
        }
    }
}
@Composable fun FloatingVoiceTools(listening:Boolean,level:Float,enabled:Boolean,onVoice:()->Unit,onWrite:()->Unit,onSummary:()->Unit,showTools:Boolean,compact:Boolean=false,starting:Boolean=false){
    Row(Modifier.fillMaxWidth().padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.weight(1f),contentAlignment=Alignment.Center){if(showTools)FloatingTool("Escribir","chat",onWrite)}
        Column(horizontalAlignment=Alignment.CenterHorizontally){
            val action=if(starting)"Cancelar preparación"else if(listening)"Pausar escucha"else "Iniciar escucha"
            Box(Modifier.size(if(compact)62.dp else 82.dp).border((1+level.coerceIn(0f,1f)*3).dp,if(listening)VoiceGreen.copy(alpha=.55f)else VoiceAccent.copy(alpha=.4f),CircleShape).padding(5.dp),contentAlignment=Alignment.Center){
                Surface(onClick=onVoice,enabled=enabled,shape=CircleShape,color=if(listening)VoiceGreen else Color(0xFFB59BFF),shadowElevation=18.dp,modifier=Modifier.fillMaxSize().semantics{contentDescription=action;role=Role.Button}){Box(contentAlignment=Alignment.Center){VoiceGlyph(if(listening)"pause"else "mic",Color(0xFF211334),Modifier.size(32.dp))}}
            }
            Text(if(starting)"Preparando…"else if(listening)"Pausar"else "Hablar",style=MaterialTheme.typography.labelSmall,color=Color(0xFFD7D4E6),modifier=Modifier.padding(top=5.dp))
        }
        Box(Modifier.weight(1f),contentAlignment=Alignment.Center){if(showTools)FloatingTool("Mapa","spark",onSummary)}
    }
}
@Composable private fun FloatingTool(label:String,icon:String,onClick:()->Unit){Column(horizontalAlignment=Alignment.CenterHorizontally){Surface(onClick=onClick,color=Color(0xFF273043),shape=RoundedCornerShape(17.dp),shadowElevation=8.dp,modifier=Modifier.size(46.dp)){Box(contentAlignment=Alignment.Center){VoiceGlyph(icon,Color(0xFFCFD5E3),Modifier.size(21.dp))}};Text(label,style=MaterialTheme.typography.labelSmall,color=Color(0xFFABB5C7),modifier=Modifier.padding(top=6.dp))}}

@Composable fun ChatVoiceComposer(listening:Boolean,starting:Boolean,level:Float,enabled:Boolean,onVoice:()->Unit,onWrite:()->Unit){
    Column(Modifier.fillMaxWidth().background(VoiceBackground).navigationBarsPadding().padding(horizontal=16.dp,vertical=10.dp)){
        Surface(color=Color(0xFF222735),shape=RoundedCornerShape(30.dp),modifier=Modifier.fillMaxWidth()){
            Row(Modifier.padding(horizontal=8.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                Surface(onClick=onWrite,color=Color.Transparent,modifier=Modifier.weight(1f),shape=RoundedCornerShape(24.dp)){Text("Escribe o habla con V",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(14.dp))}
                Surface(onClick=onVoice,enabled=enabled,color=if(listening)VoiceGreen else Color(0xFFF2F1F7),shape=CircleShape,modifier=Modifier.size(48.dp).semantics{contentDescription=if(starting)"Cancelar preparación"else if(listening)"Pausar escucha"else "Iniciar escucha";role=Role.Button}){
                    Box(contentAlignment=Alignment.Center){VoiceGlyph(if(listening)"pause"else "mic",Color(0xFF171921),Modifier.size(23.dp))}
                }
            }
        }
        if(listening||starting)Row(Modifier.fillMaxWidth().padding(top=6.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
            Text(if(starting)"Preparando…"else "Escuchando · toca para pausar",style=MaterialTheme.typography.labelSmall,color=VoiceGreen)
            if(listening)Canvas(Modifier.padding(start=8.dp).width(40.dp).height(12.dp)){for(i in 0..4){val h=2.dp.toPx()+level.coerceIn(0f,1f)*(if(i==2)10 else 6).dp.toPx();drawLine(VoiceGreen,Offset(size.width*(i+1)/6,size.height/2-h/2),Offset(size.width*(i+1)/6,size.height/2+h/2),strokeWidth=2.dp.toPx(),cap=StrokeCap.Round)}}
        }
    }
}
