package com.heyler.voicelab

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Real overlay windows, rendered only over an artificial document for the published image. */
class FloatingOverlayTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private fun dp(value:Int)=(value*context.resources.displayMetrics.density).toInt()
    private fun window(description:String)=WindowInspector.getGlobalWindowViews().firstOrNull{it.contentDescription==description}
    private fun find(view:View,description:String):View?{
        if(view.contentDescription==description)return view
        if(view is ViewGroup)for(i in 0 until view.childCount)find(view.getChildAt(i),description)?.let{return it}
        return null
    }
    private fun permission(mode:String){instrumentation.uiAutomation.executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $mode").use{ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}}
    private fun withOverlay(block:(FloatingVoiceControl,MutableList<String?>)->Unit){
        val original=android.provider.Settings.canDrawOverlays(context)
        permission("allow")
        lateinit var control:FloatingVoiceControl
        val opened=mutableListOf<String?>()
        instrumentation.runOnMainSync{control=FloatingVoiceControl(context,{opened.add(it)},{control.close()});control.show(false,false)}
        try{block(control,opened)}finally{instrumentation.runOnMainSync{control.close()};permission(if(original)"allow"else "default")}
    }
    private fun q(id:String)=FloatingQuestion(id,"¿Cómo se mide el tamaño del software?","Se pueden comparar medidas de tamaño según el objetivo del análisis.")
    @Test fun compactTranslucentWindowsOpenTheExactQuestionAndCleanUp(){withOverlay{control,opened->
        instrumentation.runOnMainSync{control.show(true,false,listOf(q("one")))}
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync{
            val bubble=checkNotNull(window("Controles de escucha de V"));val card=checkNotNull(window("Pregunta flotante de V"))
            val bubbleLayout=bubble.layoutParams as WindowManager.LayoutParams
            val cardLayout=card.layoutParams as WindowManager.LayoutParams
            assertEquals(dp(48),bubble.width);assertEquals(dp(48),bubble.height)
            assertEquals(.38f,bubbleLayout.alpha,.001f);assertEquals(.58f,cardLayout.alpha,.001f)
            assertTrue(card.width<=dp(224));assertTrue(card.height<dp(150))
            assertTrue(cardLayout.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE!=0)
            assertTrue(cardLayout.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL!=0)
            capture(bubble,card)
            assertNull(find(card,"Ocultar pregunta flotante"))
            val icon=(bubble as ViewGroup).findViewWithTag<View>("floating-handle")
            val round=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
            val drawable=checkNotNull((icon as android.widget.ImageView).drawable.constantState).newDrawable().mutate()
            drawable.setBounds(0,0,32,32);drawable.draw(Canvas(round))
            assertEquals("Circular icon corners must remain transparent",0,Color.alpha(round.getPixel(3,3)))
            assertTrue(Color.alpha(round.getPixel(16,1))>0);round.recycle()
            gesture(card,0f,0f);assertEquals(listOf("one"),opened)
            control.show(false,false,listOf(q("one")))
            assertNull(window("Pregunta flotante de V"));assertNull(window("Controles de escucha de V"))
            control.show(true,false,listOf(q("one")))
            assertNull("A seen question must not replay",window("Pregunta flotante de V"))
        }
    }}
    @Test fun messagesExpireInOrderAndRepeatedStateUpdatesDoNotResetTheirTimer(){withOverlay{control,opened->
        instrumentation.runOnMainSync{control.show(true,false,listOf(q("one"),q("two")))}
        instrumentation.runOnMainSync{assertEquals("one",window("Pregunta flotante de V")?.tag)}
        SystemClock.sleep(1800)
        instrumentation.runOnMainSync{control.show(true,false,listOf(q("one"),q("two")));assertEquals("one",window("Pregunta flotante de V")?.tag)}
        val deadline=SystemClock.uptimeMillis()+3000
        val seen=mutableListOf<String?>()
        var second=false
        while(SystemClock.uptimeMillis()<deadline&&!second){
            instrumentation.runOnMainSync{val tag=window("Pregunta flotante de V")?.tag as? String;if(seen.lastOrNull()!=tag)seen.add(tag);second=tag=="two"}
            if(!second)SystemClock.sleep(50)
        }
        assertTrue("Second message did not appear; transitions: $seen",second)
        instrumentation.runOnMainSync{checkNotNull(window("Pregunta flotante de V")).performClick();assertEquals(listOf("two"),opened)}
    }}
    @Test fun bubbleCanReopenTheLatestMessageAndStopWithoutStealingFocus(){withOverlay{control,opened->
        instrumentation.runOnMainSync{control.show(true,false,listOf(q("one")))}
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync{
            val card=checkNotNull(window("Pregunta flotante de V"))
            gesture(card,dp(70).toFloat(),0f)
            assertNull(window("Pregunta flotante de V"))
            val bubble=checkNotNull(window("Controles de escucha de V")) as ViewGroup
            bubble.getChildAt(2).performClick()
            assertNotNull(window("Pregunta flotante de V"))
            assertEquals(.8f,(bubble.layoutParams as WindowManager.LayoutParams).alpha,.001f)
            checkNotNull(find(bubble,"Volver a la conversación")).performClick();assertNull(opened.single())
            checkNotNull(find(bubble,"Detener escucha")).performClick()
            assertNull(window("Controles de escucha de V"));assertNull(window("Pregunta flotante de V"))
        }
    }}
    @Test fun draggingDocksToAllFourEdgesAndThePreviewStaysClose(){withOverlay{control,_->
        instrumentation.runOnMainSync{control.show(true,false,listOf(q("one")))}
        instrumentation.waitForIdleSync()
        val metrics=context.getSystemService(WindowManager::class.java).currentWindowMetrics
        val insets=metrics.windowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
        val width=metrics.bounds.width()-insets.left-insets.right
        val height=metrics.bounds.height()-insets.top-insets.bottom
        val size=dp(48)
        val targets=listOf(0 to (height-size)/2,(width-size)/2 to 0,(width-size)/2 to height-size,width-size to (height-size)/2)
        targets.forEach{(x,y)->
            instrumentation.runOnMainSync{
                val bubble=checkNotNull(window("Controles de escucha de V"))
                val layout=bubble.layoutParams as WindowManager.LayoutParams
                gesture(bubble.findViewWithTag("floating-handle"),(x-layout.x).toFloat(),(y-layout.y).toFloat())
            }
            SystemClock.sleep(240);instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync{
                val bubble=checkNotNull(window("Controles de escucha de V"))
                val layout=bubble.layoutParams as WindowManager.LayoutParams
                assertEquals(x.toFloat(),layout.x.toFloat(),2f);assertEquals(y.toFloat(),layout.y.toFloat(),2f)
                val icon=bubble.findViewWithTag<View>("floating-handle")
                val point=IntArray(2);icon.getLocationOnScreen(point)
                val visible=android.graphics.Rect(point[0]+icon.paddingLeft,point[1]+icon.paddingTop,point[0]+icon.width-icon.paddingRight,point[1]+icon.height-icon.paddingBottom)
                val card=checkNotNull(window("Pregunta flotante de V"));card.getLocationOnScreen(point)
                val preview=android.graphics.Rect(point[0],point[1],point[0]+card.width,point[1]+card.height)
                assertFalse("The message must not cover the circle",android.graphics.Rect.intersects(visible,preview))
                val gap=when{preview.right<=visible.left->visible.left-preview.right;preview.left>=visible.right->preview.left-visible.right;preview.bottom<=visible.top->visible.top-preview.bottom;else->preview.top-visible.bottom}
                assertTrue("The message should sit immediately next to the circle",gap<=dp(3))
            }
        }
    }}
    @Test fun swipesInAnyDirectionDismissWithoutOpeningTheAnswer(){withOverlay{control,opened->
        listOf(dp(70).toFloat() to 0f,-dp(70).toFloat() to 0f,0f to dp(70).toFloat(),0f to -dp(70).toFloat()).forEachIndexed{i,(dx,dy)->
            instrumentation.runOnMainSync{control.show(true,false,listOf(q("swipe-$i")))}
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync{gesture(checkNotNull(window("Pregunta flotante de V")),dx,dy);assertNull(window("Pregunta flotante de V"));assertNotNull(window("Controles de escucha de V"));assertTrue(opened.isEmpty())}
        }
    }}
    @Test fun dismissalRemainsAvailableToAccessibilityServices(){withOverlay{control,_->
        instrumentation.runOnMainSync{control.show(true,false,listOf(q("one")))}
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync{
            checkNotNull(window("Pregunta flotante de V")).performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id,null)
            assertNull(window("Pregunta flotante de V"))
        }
    }}
    private fun gesture(view:View,dx:Float,dy:Float){
        val now=SystemClock.uptimeMillis()
        listOf(Triple(android.view.MotionEvent.ACTION_DOWN,20f to 20f,now),Triple(android.view.MotionEvent.ACTION_MOVE,(20f+dx) to (20f+dy),now+100),Triple(android.view.MotionEvent.ACTION_UP,(20f+dx) to (20f+dy),now+200)).forEach{(action,point,time)->
            val event=android.view.MotionEvent.obtain(now,time,action,point.first,point.second,0);view.dispatchTouchEvent(event);event.recycle()
        }
    }
    private fun capture(bubble:View,card:View){
        val bounds=context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        val bitmap=Bitmap.createBitmap(bounds.width(),bounds.height(),Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.drawColor(Color.rgb(241,242,246))
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(51,59,75);textSize=dp(21).toFloat()}
        canvas.drawText("Documento de prueba",dp(24).toFloat(),dp(94).toFloat(),paint)
        paint.textSize=dp(14).toFloat()
        listOf("Contenido ficticio para revisar la burbuja.","La lectura continúa en esta pantalla.","La pregunta aparece como una vista breve.").forEachIndexed{i,text->canvas.drawText(text,dp(24).toFloat(),dp(132+i*26).toFloat(),paint)}
        paint.color=Color.rgb(220,224,232)
        for(i in 0..10)canvas.drawRoundRect(dp(24).toFloat(),dp(280+i*31).toFloat(),(bounds.width()-dp(if(i%3==0)90 else 24)).toFloat(),dp(287+i*31).toFloat(),dp(3).toFloat(),dp(3).toFloat(),paint)
        listOf(card,bubble).forEach{view->
            val position=IntArray(2);view.getLocationOnScreen(position)
            val layout=view.layoutParams as WindowManager.LayoutParams
            canvas.save();canvas.translate(position[0].toFloat(),position[1].toFloat())
            canvas.saveLayerAlpha(0f,0f,view.width.toFloat(),view.height.toFloat(),(layout.alpha*255).toInt());view.draw(canvas);canvas.restore();canvas.restore()
        }
        val file=java.io.File(context.getExternalFilesDir(null),"ui-tests/v-edge-floating-question-synthetic.png");file.parentFile!!.mkdirs()
        file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
