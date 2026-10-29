package com.heyler.voicelab

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.os.*
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PuterConnection(val connected:Boolean=false,val message:String="Conecta Puter para usar GPT-5.6 Luna.",val loaded:Boolean=false)
internal interface PuterChatTransport { suspend fun generate(messages:JSONArray,maxTokens:Int,onText:(String)->Unit) }

/** The SDK owns its session in WebView storage. No auth token reaches Kotlin or the transcript DB. */
internal class PuterRuntime(private val context:Context):PuterChatTransport{
    val connection=MutableStateFlow(PuterConnection())
    private val main=Handler(Looper.getMainLooper())
    private val nonce=UUID.randomUUID().toString()
    private var server:ServerSocket?=null
    private var origin=""
    private var view:WebView?=null
    private var host:Activity?=null
    private var popup:Dialog?=null
    private data class Pending(val id:String,val continuation:CancellableContinuation<Unit>,val output:(String)->Unit,var size:Int=0)
    private var pending:Pending?=null

    @Synchronized private fun startServer(){
        if(server!=null)return
        val prefs=context.getSharedPreferences("puter-runtime",0)
        val stored=prefs.getInt("port",0)
        val socket=runCatching{ServerSocket(stored,4,InetAddress.getByName("127.0.0.1"))}.getOrElse{ServerSocket(0,4,InetAddress.getByName("127.0.0.1"))}
        server=socket;prefs.edit().putInt("port",socket.localPort).apply();origin="http://localhost:${socket.localPort}"
        val html=context.assets.open("puter-assistant.html").bufferedReader().use{it.readText()}.replace("__BRIDGE_NONCE__",JSONObject.quote(nonce)).toByteArray()
        Thread({while(!socket.isClosed){runCatching{socket.accept().use{client->
            client.soTimeout=3000
            val reader=client.getInputStream().bufferedReader();val request=reader.readLine().orEmpty().take(4096)
            var total=0
            while(total<8192){val line=reader.readLine()?:break;if(line.isBlank())break;total+=line.length}
            val valid=request=="GET /$nonce/index.html HTTP/1.1"&&total<8192
            val bytes=if(valid)html else "Not found".toByteArray()
            client.getOutputStream().apply{write(("HTTP/1.1 ${if(valid)"200 OK"else "404 Not Found"}\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray());write(bytes);flush()}
        }}}},"V-Puter-local").apply{isDaemon=true;start()}
    }
    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(web:WebView,bridge:Boolean){
        web.settings.apply{javaScriptEnabled=true;domStorageEnabled=true;allowFileAccess=false;allowContentAccess=false;setSupportMultipleWindows(bridge);javaScriptCanOpenWindowsAutomatically=false;mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW}
        web.setBackgroundColor(0xFF111521.toInt())
        web.webViewClient=object:WebViewClient(){
            override fun shouldOverrideUrlLoading(w:WebView,request:WebResourceRequest):Boolean{
                val uri=request.url
                return if(bridge)request.isForMainFrame&&!(uri.scheme=="http"&&uri.host=="localhost"&&uri.port==server?.localPort)
                else uri.toString()!="about:blank"&&(uri.scheme!="https"||!(uri.host=="puter.com"||uri.host?.endsWith(".puter.com")==true))
            }
        }
        web.webChromeClient=object:WebChromeClient(){
            override fun onPermissionRequest(request:PermissionRequest){request.deny()}
            override fun onCreateWindow(w:WebView,isDialog:Boolean,isUserGesture:Boolean,result:Message):Boolean{
                val activity=host?:return false
                if(!bridge||!isUserGesture||activity.isFinishing)return false
                val child=WebView(activity);configure(child,false)
                val dialog=Dialog(activity).apply{setContentView(child);setOnDismissListener{child.destroy();popup=null};show();window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)}
                popup=dialog;(result.obj as WebView.WebViewTransport).webView=child;result.sendToTarget();return true
            }
            override fun onCloseWindow(window:WebView){if(!bridge)popup?.dismiss()}
        }
        if(bridge)web.addJavascriptInterface(object{
            @JavascriptInterface fun message(secret:String,event:String){if(secret==nonce&&event.length<=65536)main.post{receive(event)}}
        },"VBridge")
    }
    private suspend fun load()=withContext(Dispatchers.Main){
        if(view==null){withContext(Dispatchers.IO){startServer()};if(view==null)view=WebView(context).also{configure(it,true);it.loadUrl("$origin/$nonce/index.html")}}
    }
    suspend fun show(activity:Activity,container:FrameLayout){
        load();withContext(Dispatchers.Main){host=activity;val web=checkNotNull(view);(web.parent as? ViewGroup)?.removeView(web);container.addView(web,FrameLayout.LayoutParams(-1,-1))}
    }
    fun detach(activity:Activity){if(host===activity){(view?.parent as? ViewGroup)?.removeView(view);host=null;popup?.dismiss()}}
    override suspend fun generate(messages:JSONArray,maxTokens:Int,onText:(String)->Unit){
        load()
        withTimeout(20000){connection.first{it.loaded}}
        withTimeout(120000){withContext(Dispatchers.Main){suspendCancellableCoroutine<Unit>{continuation->
            if(!connection.value.connected){continuation.resumeWithException(IOException("Conecta Puter en Ajustes para usar GPT-5.6 Luna."));return@suspendCancellableCoroutine}
            if(pending!=null){continuation.resumeWithException(IOException("Hay una consulta a Puter en curso."));return@suspendCancellableCoroutine}
            val id=UUID.randomUUID().toString();pending=Pending(id,continuation,onText)
            continuation.invokeOnCancellation{main.post{if(pending?.id==id)pending=null;view?.evaluateJavascript("window.VPuter?.cancel(${JSONObject.quote(id)})",null)}}
            view?.evaluateJavascript("window.VPuter.run(${JSONObject.quote(id)},$messages,$maxTokens)",null)
        }}}
    }
    private fun receive(raw:String){
        val event=runCatching{JSONObject(raw)}.getOrNull()?:return
        if(event.optString("type")=="status"){
            val ready=event.optBoolean("connected")
            connection.value=PuterConnection(ready,if(ready)"GPT-5.6 Luna conectado"else if(event.optBoolean("unavailable"))"GPT-5.6 Luna no está disponible en esta sesión."else "Conecta Puter para continuar.",true)
            return
        }
        if(event.optString("type")=="error"&&event.optString("id").isBlank()){
            connection.value=PuterConnection(false,"No se pudo conectar con Puter. Revisa Internet y vuelve a abrir la conexión.",true);return
        }
        val task=pending?.takeIf{it.id==event.optString("id")}?:return
        if(!task.continuation.isActive){pending=null;return}
        when(event.optString("type")){
            "delta"->{val delta=event.optString("text");task.size+=delta.length;if(task.size>35000){pending=null;task.continuation.resumeWithException(IOException("Puter superó el tamaño permitido."))}else task.output(delta)}
            "done"->{pending=null;if(task.size==0)task.continuation.resumeWithException(IOException("Puter no devolvió texto."))else task.continuation.resume(Unit)}
            "error"->{pending=null;val code=event.optString("code");task.continuation.resumeWithException(if(code.contains("funds")||code.contains("429")||code.contains("too_many"))OnlineQuotaException(60000,"Puter")else IOException(if(code=="auth_required")"Conecta Puter de nuevo en Ajustes."else "Puter no completó la consulta. Revisa tu conexión y sesión."))}
        }
    }
}

internal class PuterAssistant(private val runtime:PuterChatTransport):OnlineAssistant(){
    override suspend fun generate(prompt:String,summary:Boolean,analysis:Boolean,map:Boolean,onText:(String)->Unit){
        val messages=JSONArray().put(JSONObject().put("role","system").put("content",instruction(summary,analysis,map)))
            .put(JSONObject().put("role","user").put("content",prompt.take(if(map)24000 else if(analysis)16000 else 12000)))
        try{runtime.generate(messages,if(map)5000 else if(analysis)4096 else 1536,onText)}
        catch(e:TimeoutCancellationException){currentCoroutineContext().ensureActive();throw IOException("Puter tardó demasiado en responder. El contenido anterior se conservó.")}
    }
}
