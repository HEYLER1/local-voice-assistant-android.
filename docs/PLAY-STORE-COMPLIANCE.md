# Preparación de Asistente V para Google Play

## Cambios incluidos en la app

- La persona puede leer la política desde la pantalla de inicio de sesión y desde Ajustes.
- La cuenta tiene una opción de eliminación con confirmación reciente de Google.
- Al confirmar, la app elimina conversaciones y fragmentos de Firestore, textos guardados, copias locales y la cuenta de Firebase Authentication.
- Cada solicitud explícita de resumen ofrece un video recompensado; el resultado se genera tras completar el video. La transcripción y las preguntas siguen disponibles sin verlo.
- Los anuncios de apertura tienen un intervalo mínimo persistente de 30 minutos y no se muestran durante una grabación ni mientras se genera una respuesta o resumen.
- Las reglas de Firestore permiten borrar solo los documentos de la propia cuenta; el resto de lecturas y escrituras sigue limitado al propietario.

## Requisitos externos antes de enviar a revisión

1. Correo de privacidad y soporte configurado: `soportevestudio@gmail.com`. Verifica que se atiendan las solicitudes y que la página externa permita pedir la eliminación sin iniciar sesión.
2. Publica las reglas actualizadas de `firestore.rules` en Firebase Console → Firestore Database → Rules → **Publicar**. La eliminación en la app no podrá completar la parte en la nube hasta que estén publicadas.
3. Publica la carpeta `docs/` con GitHub Pages: en el repositorio, abre **Settings → Pages**, elige la rama principal y la carpeta `/docs`. Comprueba que estas direcciones abren sin iniciar sesión:
   - `https://heyler1.github.io/local-voice-assistant-android./privacy-policy.html`
   - `https://heyler1.github.io/local-voice-assistant-android./delete-account.html`
4. En Play Console, añade la URL de privacidad y la URL de solicitud de eliminación, y completa Anuncios, Público objetivo, Clasificación por edades y Seguridad de los datos.
5. En Seguridad de los datos, declara también lo que recolectan o comparten los SDK incluidos. La app usa Google Sign-In/Firebase, Firestore, Pollinations, AI Horde, Google Mobile Ads y UMP. Revisa la divulgación vigente de cada servicio antes de enviar.
6. Prueba la eliminación con una cuenta de prueba que tenga conversaciones y textos guardados. Comprueba que desaparezcan de Firebase Authentication, Firestore y del teléfono; prueba también la solicitud web desde una ventana privada.

## Publicación

Genera un **Android App Bundle (`.aab`) release firmado** desde Android Studio y súbelo primero a una prueba interna. No uses el APK de depuración para la ficha de Play. La clave de subida y sus contraseñas deben quedarse privadas y guardadas fuera del repositorio.
