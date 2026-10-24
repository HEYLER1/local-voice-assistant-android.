package com.heyler.voicelab

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/** Small, non-focusable overlay: never steals the keyboard or touches outside its own bounds. */
internal class FloatingVoiceControl(private val context:Context,private val open:()->Unit,private val stop:()->Unit){
    private val windows=context.getSystemService(WindowManager::class.java)
    private var root:LinearLayout?=null
    private var expanded=false
    private var preparing=false
    private var x=16
    private var y=220
    private fun dp(value:Int)=(value*context.resources.displayMetrics.density).toInt()
    private fun background(color:Int,radius:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat();setStroke(dp(1),0xFF635E86.toInt())}
    private fun chip(label:String,description:String,action:()->Unit)=TextView(context).apply{
        text=label;contentDescription=description;setTextColor(Color.WHITE);textSize=15f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER
        setPadding(dp(16),0,dp(16),0);minimumHeight=dp(56);setOnClickListener{action()}
    }
    @SuppressLint("ClickableViewAccessibility")
    fun show(visible:Boolean,starting:Boolean){
        preparing=starting
        if(!visible||!Settings.canDrawOverlays(context)){close();return}
        if(root!=null){(root?.getChildAt(0) as? TextView)?.text=if(preparing)"···"else "V ●";return}
        val panel=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL;background=background(0xFF252238.toInt(),28);elevation=dp(8).toFloat();contentDescription="Controles de escucha de V"}
        val params=WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,dp(56),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,android.graphics.PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.END;x=this@FloatingVoiceControl.x;y=this@FloatingVoiceControl.y}
        val handle=chip(if(starting)"···"else "V ●","V: escuchando. Toca para abrir controles; arrastra para mover."){}
        handle.setTextColor(0xFFDDD3FF.toInt());handle.minWidth=dp(64)
        val openButton=chip("Abrir","Volver a la conversación"){open()}
        val stopButton=chip("■","Detener escucha"){stop()}.apply{setTextColor(0xFF80D9BA.toInt())}
        panel.addView(handle);panel.addView(openButton);panel.addView(stopButton)
        fun collapse(){openButton.visibility=if(expanded)View.VISIBLE else View.GONE;stopButton.visibility=if(expanded)View.VISIBLE else View.GONE}
        expanded=false;collapse()
        handle.setOnClickListener{expanded=!expanded;collapse()}
        var downX=0f;var downY=0f;var startX=0;var startY=0;var moved=false
        handle.setOnTouchListener{view,event->when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{downX=event.rawX;downY=event.rawY;startX=params.x;startY=params.y;moved=false;true}
            MotionEvent.ACTION_MOVE->{val dx=event.rawX-downX;val dy=event.rawY-downY;if(abs(dx)>dp(6)||abs(dy)>dp(6))moved=true
                if(moved){val bounds=windows.currentWindowMetrics.bounds;params.x=(startX-dx.toInt()).coerceIn(0,maxOf(0,bounds.width()-panel.width));params.y=(startY+dy.toInt()).coerceIn(dp(30),maxOf(dp(30),bounds.height()-dp(100)));x=params.x;y=params.y;runCatching{windows.updateViewLayout(panel,params)}};true}
            MotionEvent.ACTION_UP->{if(!moved)view.performClick();true}
            MotionEvent.ACTION_CANCEL->true
            else->false
        }}
        runCatching{windows.addView(panel,params);root=panel}.onFailure{root=null}
    }
    fun close(){root?.let{runCatching{windows.removeViewImmediate(it)}};root=null;expanded=false}
}
