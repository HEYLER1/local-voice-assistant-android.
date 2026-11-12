package com.heyler.voicelab

import android.app.Activity
import android.content.Context
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class GoogleAccountInfo(val uid:String,val displayName:String,val email:String)
internal enum class GoogleSignInEntry { GOOGLE_BUTTON, ACCOUNT_PICKER }
internal sealed interface GoogleAuthState {
    data object ConfigurationRequired:GoogleAuthState
    data object SignedOut:GoogleAuthState
    data class SignedIn(val account:GoogleAccountInfo):GoogleAuthState
}

/** Google identity is exchanged for a Firebase session; no Google access token is stored by the app. */
internal class GoogleAuthRepository(context:Context):AutoCloseable {
    private val appContext=context.applicationContext
    private val credentialManager=CredentialManager.create(context)
    private val auth:FirebaseAuth?=runCatching{
        FirebaseApp.getApps(appContext).firstOrNull{it.name==FirebaseApp.DEFAULT_APP_NAME}?.let{FirebaseAuth.getInstance(it)}
    }.getOrNull()
    private val webClientId:String?=runCatching{
        val id=appContext.resources.getIdentifier("default_web_client_id","string",appContext.packageName)
        if(id==0)null else appContext.getString(id).takeIf{it.isNotBlank()}
    }.getOrNull()
    private val _state=MutableStateFlow<GoogleAuthState>(initialState())
    val state:StateFlow<GoogleAuthState> = _state
    private val listener=FirebaseAuth.AuthStateListener{firebase->
        _state.value=if(isConfigured())firebase.currentUser?.let{GoogleAuthState.SignedIn(GoogleAccountInfo(it.uid,it.displayName.orEmpty(),it.email.orEmpty()))}?:GoogleAuthState.SignedOut else GoogleAuthState.ConfigurationRequired
    }

    init { if(isConfigured())auth?.addAuthStateListener(listener) }
    private fun isConfigured()=auth!=null&&!webClientId.isNullOrBlank()
    private fun initialState():GoogleAuthState=if(!isConfigured())GoogleAuthState.ConfigurationRequired else auth?.currentUser?.let{GoogleAuthState.SignedIn(GoogleAccountInfo(it.uid,it.displayName.orEmpty(),it.email.orEmpty()))}?:GoogleAuthState.SignedOut

    suspend fun signIn(activity:Activity,entry:GoogleSignInEntry){
        val configuredAuth=auth
        val clientId=webClientId
        check(configuredAuth!=null&&!clientId.isNullOrBlank()){"Falta configurar Firebase y el cliente OAuth web."}
        val nonce=nonce()
        val option=when(entry){
            GoogleSignInEntry.GOOGLE_BUTTON->GetSignInWithGoogleOption.Builder(clientId).setNonce(nonce).build()
            GoogleSignInEntry.ACCOUNT_PICKER->GetGoogleIdOption.Builder().setServerClientId(clientId).setFilterByAuthorizedAccounts(false).setNonce(nonce).build()
        }
        val request=GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential=credentialManager.getCredential(activity,request).credential as? CustomCredential
            ?:error("Google no devolvió una credencial compatible.")
        require(credential.type==GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL){"Google no devolvió un token de identidad."}
        val token=GoogleIdTokenCredential.createFrom(credential.data).idToken
        require(token.isNotBlank()){"Google devolvió un token vacío."}
        val firebaseCredential=GoogleAuthProvider.getCredential(token,null)
        configuredAuth.signInWithCredential(firebaseCredential).awaitResult()
    }

    suspend fun signOut(){
        auth?.signOut()
        runCatching{credentialManager.clearCredentialState(androidx.credentials.ClearCredentialStateRequest())}
    }

    override fun close(){auth?.removeAuthStateListener(listener)}

    fun message(error:Throwable):String=when(error){
        is GetCredentialCancellationException->""
        is NoCredentialException->"No se encontró una cuenta disponible. Añade una cuenta de Google al teléfono e inténtalo de nuevo."
        is FirebaseNetworkException->"No se pudo conectar con Google. Revisa Internet e inténtalo de nuevo."
        is FirebaseAuthInvalidCredentialsException->"Firebase rechazó la credencial. Revisa el cliente OAuth y la huella SHA-1 configurados."
        is FirebaseAuthInvalidUserException->"La cuenta no está disponible para esta aplicación."
        is FirebaseAuthException->"Firebase no pudo completar el inicio de sesión. Revisa la configuración del proveedor Google."
        is CancellationException->""
        else->"No se pudo iniciar sesión con Google. Comprueba la configuración y vuelve a intentarlo."
    }

    private fun nonce():String=ByteArray(32).also{SecureRandom().nextBytes(it)}.let{Base64.encodeToString(it,Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)}

    private suspend fun <T> Task<T>.awaitResult():T=suspendCancellableCoroutine{continuation->
        addOnSuccessListener{if(continuation.isActive)continuation.resume(it)}
        addOnFailureListener{if(continuation.isActive)continuation.resumeWithException(it)}
        addOnCanceledListener{if(continuation.isActive)continuation.cancel(CancellationException("La autenticación fue cancelada."))}
    }
}
