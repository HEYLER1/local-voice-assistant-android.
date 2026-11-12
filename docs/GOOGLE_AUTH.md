# Configurar el inicio de sesión con Google

La integración usa **Android Credential Manager + Sign in with Google + Firebase Authentication**. Credential Manager muestra el selector nativo; Firebase valida el token de identidad y conserva la sesión entre aperturas. No se guarda un access token de Google en la app.

## Configuración que debes hacer una sola vez

1. Crea o abre un proyecto en [Firebase Console](https://console.firebase.google.com/).
2. Registra una app Android con el identificador exacto `com.heyler.voicelab`.
3. En Firebase Console abre **Authentication → Proveedores de acceso** y habilita **Google**. Configura el correo de asistencia solicitado por Firebase.
4. En los ajustes de la app Android de Firebase agrega la huella **SHA-1**. Para verla localmente, desde la carpeta del proyecto ejecuta:

   ```sh
   ./android/gradlew -p android :app:signingReport
   ```

   Agrega la SHA-1 de `debug` para probar. Cuando distribuyas una versión firmada, agrega también la huella de esa firma.
5. Descarga el `google-services.json` actualizado desde Firebase y colócalo en:

   ```text
   android/app/google-services.json
   ```

6. Sincroniza Gradle y vuelve a compilar. El plugin de Google Services solo se activa cuando ese archivo existe; sin él, la app muestra la pantalla que explica la configuración pendiente.

Firebase genera en ese archivo el cliente OAuth web que Credential Manager necesita. La app lo lee como `default_web_client_id`; no pongas un client ID inventado ni una clave privada en Kotlin. La configuración local está excluida de Git mediante `.gitignore`.

## Comportamiento de la app

- No se puede abrir el chat sin iniciar sesión en una cuenta de Google.
- Firebase conserva la sesión y restaura el usuario al volver a abrir la app.
- SQLite conserva una copia local del historial, particionada por UID. Cuando Cloud Firestore y sus reglas están habilitados, las transcripciones, preguntas, respuestas, resúmenes y textos o tareas guardados también se sincronizan con la cuenta; el audio no se guarda. Sigue [la guía de Firestore](FIRESTORE_SYNC.md) para crear la base y publicar las reglas.
- Si ya había conversaciones sin cuenta, aparece una confirmación para asociarlas a la primera cuenta. Al elegir **Asociar a esta cuenta**, se preparan para sincronizar; **Dejar aparte** conserva los datos en el espacio local anterior y puedes importarlos luego desde **Ajustes → Cuenta de Google**.
- Cerrar sesión detiene la escucha, conserva la copia local de esa cuenta y limpia el estado de credenciales de Credential Manager. Al volver con la misma cuenta, el historial local se restaura y se compara con el guardado en Firestore.
- La autenticación de Google no cambia el envío de fragmentos de texto a Pollinations o AI Horde cuando se solicitan respuestas online. No se envía el audio a esos proveedores.

## Dependencias integradas

Se usan versiones estables/currentes verificadas en la documentación oficial a noviembre de 2026: Firebase BoM `35.1.0`, Google Services plugin `4.5.0`, Credential Manager `1.6.0` y Google ID `1.2.1`. Este proyecto declara dependencias directamente en `app/build.gradle.kts`; no tiene `libs.versions.toml`.

Referencias oficiales: [Credential Manager y Sign in with Google](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation), [Google con Firebase Authentication para Android](https://firebase.google.com/docs/auth/android/google-signin) y [configuración Firebase Android](https://firebase.google.com/docs/android/setup).

## Verificación pendiente

La integración puede compilarse sin `google-services.json`, pero el flujo real de Google no funcionará hasta completar los pasos anteriores. Después de añadirlo, hay que probar selector, cancelación, sesión restaurada, cambio de cuenta, cierre de sesión y separación de historiales en un emulador con Google Play Services o en un teléfono.
