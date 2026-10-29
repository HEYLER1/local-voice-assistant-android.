package com.heyler.voicelab

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/** Two bounded non-focusable windows: a dockable handle and a swipe-to-dismiss preview. */
internal class FloatingVoiceControl(private val context:Context,private val open:(String?)->Unit,private val stop:()->Unit){
    private val windows=context.getSystemService(WindowManager::class.java)
    private val handler=Handler(Looper.getMainLooper())
    private var questions=FloatingQuestionQueue()
    private var session:Long?=null
    private var root:LinearLayout?=null
    private var message:LinearLayout?=null
    private var handle:ImageView?=null
    private var openButton:TextView?=null
    private var stopButton:TextView?=null
    private var expanded=false
    private var pinned=false
    private var menuBefore=true
    private var shownId:String?=null
    private var bubbleParams:WindowManager.LayoutParams?=null
    private var messageParams:WindowManager.LayoutParams?=null
    private var position=FloatingPosition(Int.MAX_VALUE,dp(180),FloatingEdge.RIGHT)
    private var docking:ValueAnimator?=null
    private var messageDeadline=0L
    private var swiping=false
    private val expire=Runnable{if(pinned){dismissMessage()}else{questions.advance();renderMessage()}}
    private val fade=Runnable{if(!expanded)setBubbleAlpha(IDLE_ALPHA)}
    private fun dp(value:Int)=(value*context.resources.displayMetrics.density).toInt()
    private fun usableSize():Pair<Int,Int>{
        val metrics=windows.currentWindowMetrics
        val bars=metrics.windowInsets.getInsetsIgnoringVisibility(FIT_TYPES)
        return (metrics.bounds.width()-bars.left-bars.right).coerceAtLeast(dp(48)) to (metrics.bounds.height()-bars.top-bars.bottom).coerceAtLeast(dp(48))
    }
    private fun background(color:Int,radius:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat();setStroke(dp(1),0xFF62577C.toInt())}
    private fun params(width:Int,height:Int,opacity:Float)=WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,android.graphics.PixelFormat.TRANSLUCENT).apply{
        gravity=Gravity.TOP or Gravity.LEFT;alpha=opacity;setFitInsetsTypes(FIT_TYPES);setFitInsetsIgnoringVisibility(true)
    }
    private fun chip(label:String,description:String,action:()->Unit)=TextView(context).apply{
        text=label;contentDescription=description;setTextColor(0xFFE9E2F7.toInt());textSize=18f;gravity=Gravity.CENTER
        layoutParams=LinearLayout.LayoutParams(dp(40),dp(48));setOnClickListener{action()}
    }
    fun show(visible:Boolean,preparing:Boolean,items:List<FloatingQuestion> = emptyList(),conversation:Long=0){
        if(session!=conversation){questions=FloatingQuestionQueue();session=conversation;close()}
        val allowed=visible&&Settings.canDrawOverlays(context)
        questions.observe(items,allowed)
        if(!allowed){close();return}
        if(root==null)createBubble()
        handle?.contentDescription=if(preparing)"V: preparando. Toca para abrir controles; arrastra a cualquier borde."else "V: escuchando. Toca para abrir controles; arrastra a cualquier borde."
        renderMessage()
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun createBubble(){
        val (width,height)=usableSize()
        position=FloatingDocking.onEdge(width,height,dp(48),position.x,position.y,position.edge)
        val panel=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;contentDescription="Controles de escucha de V"}
        val layout=params(dp(48),dp(48),IDLE_ALPHA).apply{x=position.x;y=position.y}
        val go=chip("↗","Volver a la conversación"){open(null)}
        val pause=chip("Ⅱ","Detener escucha"){stop()}.apply{setTextColor(0xFF67E2BA.toInt())}
        val icon=ImageView(context).apply{
            tag="floating-handle";layoutParams=LinearLayout.LayoutParams(dp(48),dp(48));scaleType=ImageView.ScaleType.FIT_CENTER
            setImageResource(R.drawable.brand_floating)
            setOnClickListener{
                expanded=!expanded;pinned=expanded
                updateMenuDirection();applyBubblePosition()
                setBubbleAlpha(if(expanded)ACTIVE_ALPHA else IDLE_ALPHA)
                if(!expanded)questions.dismiss()
                renderMessage()
                if(message!=null)scheduleExpiry(MESSAGE_MILLIS)
            }
        }
        go.visibility=View.GONE;pause.visibility=View.GONE
        panel.addView(go);panel.addView(pause);panel.addView(icon)
        panel.addOnLayoutChangeListener{_,_,_,_,_,_,_,_,_->positionMessage()}
        var downX=0f;var downY=0f;var startX=0;var startY=0;var moved=false
        icon.setOnTouchListener{view,event->when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{docking?.cancel();handler.removeCallbacks(fade);setBubbleAlpha(ACTIVE_ALPHA);downX=event.rawX;downY=event.rawY;startX=position.x;startY=position.y;moved=false;true}
            MotionEvent.ACTION_MOVE->{val dx=event.rawX-downX;val dy=event.rawY-downY;if(abs(dx)>dp(6)||abs(dy)>dp(6))moved=true
                if(moved){val size=usableSize();position=position.copy(x=(startX+dx.toInt()).coerceIn(0,(size.first-dp(48)).coerceAtLeast(0)),y=(startY+dy.toInt()).coerceIn(0,(size.second-dp(48)).coerceAtLeast(0)));applyBubblePosition()};true}
            MotionEvent.ACTION_UP->{if(moved)snapToEdge()else view.performClick();if(!expanded)handler.postDelayed(fade,500);true}
            MotionEvent.ACTION_CANCEL->{if(moved)snapToEdge();if(!expanded)handler.postDelayed(fade,500);true}
            else->false
        }}
        runCatching{windows.addView(panel,layout);root=panel;handle=icon;openButton=go;stopButton=pause;bubbleParams=layout;applyBubblePosition()}.onFailure{root=null;bubbleParams=null}
    }
    private fun updateMenuDirection(){menuBefore=position.edge==FloatingEdge.RIGHT||(position.edge!=FloatingEdge.LEFT&&position.x>usableSize().first/2)}
    private fun setBubbleAlpha(opacity:Float){bubbleParams?.let{it.alpha=opacity;root?.let{view->runCatching{windows.updateViewLayout(view,it)}}}}
    private fun applyBubblePosition(){
        val panel=root?:return;val layout=bubbleParams?:return;val icon=handle?:return;val go=openButton?:return;val pause=stopButton?:return
        val size=usableSize();val before=!expanded||menuBefore
        val first=if(before)go else icon
        if(panel.getChildAt(0)!==first){panel.removeAllViews();if(before){panel.addView(go);panel.addView(pause);panel.addView(icon)}else{panel.addView(icon);panel.addView(go);panel.addView(pause)}}
        go.visibility=if(expanded)View.VISIBLE else View.GONE;pause.visibility=go.visibility
        panel.background=if(expanded)background(0xFF222735.toInt(),24)else null
        val pad=dp(8);val large=dp(16)
        when(position.edge){
            FloatingEdge.LEFT->icon.setPadding(0,pad,large,pad)
            FloatingEdge.RIGHT->icon.setPadding(large,pad,0,pad)
            FloatingEdge.TOP->icon.setPadding(pad,0,pad,large)
            FloatingEdge.BOTTOM->icon.setPadding(pad,large,pad,0)
        }
        layout.width=dp(if(expanded)128 else 48)
        layout.x=(position.x-if(expanded&&menuBefore)dp(80)else 0).coerceIn(0,(size.first-layout.width).coerceAtLeast(0))
        layout.y=position.y.coerceIn(0,(size.second-dp(48)).coerceAtLeast(0))
        runCatching{windows.updateViewLayout(panel,layout)};positionMessage()
    }
    private fun snapToEdge(){
        val size=usableSize();val from=position
        val target=FloatingDocking.nearest(size.first,size.second,dp(48),position.x,position.y,position.edge)
        position=position.copy(edge=target.edge);updateMenuDirection()
        docking?.cancel()
        docking=ValueAnimator.ofFloat(0f,1f).apply{duration=180;addUpdateListener{frame->
            val t=frame.animatedValue as Float;position=target.copy(x=(from.x+(target.x-from.x)*t).toInt(),y=(from.y+(target.y-from.y)*t).toInt());applyBubblePosition()
        };start()}
    }
    fun onBoundsChanged(){
        docking?.cancel();val size=usableSize();position=FloatingDocking.onEdge(size.first,size.second,dp(48),position.x,position.y,position.edge);updateMenuDirection();applyBubblePosition()
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun renderMessage(){
        val item=if(pinned)questions.current?:questions.latest else questions.current
        if(item==null||root==null){removeMessage();return}
        val changed=shownId!=item.id
        if(message==null){
            val card=LinearLayout(context).apply{
                orientation=LinearLayout.VERTICAL;background=background(0xFF222735.toInt(),16);elevation=dp(2).toFloat();setPadding(dp(11),dp(9),dp(11),dp(9))
                contentDescription="Pregunta flotante de V";accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
            card.addView(TextView(context).apply{tag="question";textSize=13f;setTextColor(0xFFEDE7FF.toInt());typeface=Typeface.DEFAULT_BOLD;maxLines=2;ellipsize=TextUtils.TruncateAt.END})
            card.addView(TextView(context).apply{tag="answer";textSize=12f;setTextColor(0xFFE2E4F0.toInt());maxLines=2;ellipsize=TextUtils.TruncateAt.END;setPadding(0,dp(3),0,0)})
            card.setOnClickListener{(if(pinned)questions.current?:questions.latest else questions.current)?.let{open(it.id)}}
            card.accessibilityDelegate=object:View.AccessibilityDelegate(){
                override fun onInitializeAccessibilityNodeInfo(host:View,info:AccessibilityNodeInfo){super.onInitializeAccessibilityNodeInfo(host,info);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS)}
                override fun performAccessibilityAction(host:View,action:Int,args:Bundle?):Boolean{if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id){dismissMessage();return true};return super.performAccessibilityAction(host,action,args)}
            }
            var downX=0f;var downY=0f;var moved=false;var remaining=MESSAGE_MILLIS
            card.setOnTouchListener{view,event->when(event.actionMasked){
                MotionEvent.ACTION_DOWN->{downX=event.rawX;downY=event.rawY;moved=false;swiping=true;remaining=(messageDeadline-SystemClock.uptimeMillis()).coerceAtLeast(1L);handler.removeCallbacks(expire);true}
                MotionEvent.ACTION_MOVE->{val dx=event.rawX-downX;val dy=event.rawY-downY;if(abs(dx)>dp(6)||abs(dy)>dp(6))moved=true
                    if(moved){view.translationX=dx;view.translationY=dy;view.alpha=(1f-maxOf(abs(dx),abs(dy))/dp(160)).coerceIn(.3f,1f)};true}
                MotionEvent.ACTION_UP->{swiping=false;val distance=maxOf(abs(event.rawX-downX),abs(event.rawY-downY))
                    if(moved&&distance>=dp(28)){dismissMessage()}else{view.translationX=0f;view.translationY=0f;view.alpha=1f;if(!moved)view.performClick();if(message!=null)scheduleExpiry(remaining)};true}
                MotionEvent.ACTION_CANCEL->{swiping=false;view.translationX=0f;view.translationY=0f;view.alpha=1f;if(message!=null)scheduleExpiry(remaining);true}
                else->false
            }}
            val width=minOf(dp(224),usableSize().first-dp(36)).coerceAtLeast(dp(80))
            val layout=params(width,WindowManager.LayoutParams.WRAP_CONTENT,MESSAGE_ALPHA)
            runCatching{windows.addView(card,layout);message=card;messageParams=layout;card.addOnLayoutChangeListener{_,_,_,_,_,_,_,_,_->positionMessage()}}.onFailure{message=null;messageParams=null}
        }
        message?.findViewWithTag<TextView>("question")?.text=item.question
        message?.findViewWithTag<TextView>("answer")?.text=ReadableAnswer.format(item.answer).replace(Regex("\\s+")," ")
        messageParams?.let{it.alpha=if(pinned)ACTIVE_ALPHA else MESSAGE_ALPHA;message?.let{card->runCatching{windows.updateViewLayout(card,it)}}}
        message?.tag=item.id;shownId=item.id;positionMessage()
        if(changed&&!swiping)scheduleExpiry(MESSAGE_MILLIS)
    }
    private fun scheduleExpiry(duration:Long){handler.removeCallbacks(expire);messageDeadline=SystemClock.uptimeMillis()+duration;handler.postDelayed(expire,duration)}
    private fun dismissMessage(){pinned=false;questions.dismiss();removeMessage()}
    private fun positionMessage(){
        val card=message?:return;val layout=messageParams?:return;val bubble=bubbleParams?:return
        val size=usableSize();val offset=if(expanded&&menuBefore)dp(80)else 0
        val actual=position.copy(x=bubble.x+offset,y=bubble.y)
        val anchor=if(expanded)FloatingBox(bubble.x,bubble.y,bubble.width,dp(48))else FloatingDocking.icon(actual,dp(48),dp(32))
        val box=FloatingDocking.message(size.first,size.second,anchor,layout.width,maxOf(card.height,dp(76)),dp(2),position.edge)
        layout.x=box.left;layout.y=box.top
        runCatching{windows.updateViewLayout(card,layout)}
    }
    private fun removeMessage(){handler.removeCallbacks(expire);message?.let{runCatching{windows.removeViewImmediate(it)}};message=null;messageParams=null;shownId=null;swiping=false}
    fun close(){
        docking?.cancel();docking=null;handler.removeCallbacks(fade);removeMessage();root?.let{runCatching{windows.removeViewImmediate(it)}}
        root=null;handle=null;openButton=null;stopButton=null;bubbleParams=null;expanded=false;pinned=false
    }
    companion object{
        private val FIT_TYPES=WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        internal const val IDLE_ALPHA=.38f
        internal const val MESSAGE_ALPHA=.58f
        internal const val ACTIVE_ALPHA=.8f
        internal const val MESSAGE_MILLIS=4000L
    }
}
