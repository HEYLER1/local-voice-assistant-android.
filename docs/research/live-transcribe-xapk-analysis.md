# Inspección estática de Live Transcribe 9.0.965827460

Fecha: 7 de octubre de 2026. Se examinó el XAPK facilitado por el usuario, sin instalarlo, ejecutarlo ni contactar con sus servicios. No se publica el paquete ni sus bibliotecas.

- Paquete declarado: `com.google.audio.hearing.visualization.accessibility.scribe`.
- Versión declarada: `9.0.965827460`, código `206928`.
- SHA-256 del XAPK: `b5d6937aa85ced53191c43143eaf8c73ddaf49131f9f5b8a89934637b29357c1`.
- Contiene un APK base (21.188.790 bytes) y un split ARM64 (9.114.340 bytes).
- Método: inventario ZIP, decodificación de AndroidManifest.xml con aapt2 y extracción de tablas de cadenas DEX. No es una reconstrucción completa del flujo de ejecución ni una validación de firma del distribuidor.

## Evidencias

| Evidencia encontrada | Interpretación | Límite |
|---|---|---|
| `SodaSpeechSession.java`, `SodaRecognitionListener.java`, `SodaRecognizerException`, `SODA_AGSA`, `SODA_AIAI`, `SODA_SBG` | El cliente incorpora referencias a rutas de reconocimiento SODA. | Las cadenas pueden incluir código no usado; no identifican la ruta elegida en el A54. |
| `createOnDeviceSpeechRecognizer`, `com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService`, `com.google.android.apps.miphone.aiai.app.AiAiSpeechRecognitionService` | Integra reconocimiento local de Android y referencias a servicios Google. | No prueba que una aplicación externa tenga acceso a todas sus funciones internas. |
| `CloudSpeechSession.java`, `CloudSpeechSessionFactory.java`, `speech.googleapis.com`, mensajes gRPC | Existe una ruta de reconocimiento conectado. | No sabemos si estaba activa durante la comparación del usuario. |
| `SwitchableSpeechSession.java`, errores de `SwitchableSpeechSessionFactory` y gestión de descarga de idiomas | Hay infraestructura para cambiar de sesión/ruta y gestionar modelos de idioma. | No se ha reconstruido su política de selección. |
| `RepeatingRecognitionSession.java`, `libogg_opus_encoder.so`, `libresampler.so` | Gestión de sesiones, codificación Opus y conversión de frecuencia de audio. | No demuestra menor latencia ni más precisión por sí solo. |
| `NoiseSuppressor`, mensajes de activación y error de supresión de ruido | Se contempla el efecto de supresión de ruido Android. | No revela sus parámetros ni demuestra separación de música. |
| `audio_set_960ms.tflite`, `earsnet_streaming_model.tflite`, `sound_discovery_model.tflite`, `plss_*`, `libtensorflowlite_jni.so` | Hay modelos y ejecución TFLite asociados a detección/descubrimiento de sonidos, según sus nombres y recursos. | No deben identificarse automáticamente como el modelo de transcripción. |

## Qué no incluye esta inspección

No se encontró un modelo ASR completo y claramente identificado dentro de los assets enumerados ni una biblioteca `libsoda.so` entre las siete bibliotecas nativas del split. Esto es compatible con reconocimiento delegado a un servicio externo o paquetes descargados, pero no excluye datos/modelos encapsulados de otra forma. No se identificó la arquitectura neuronal exacta, número de parámetros, pesos de español ni selección activa del motor.

## Consecuencia para V

La opción Android local de V 0.13 utiliza la API pública on-device, coherente con una referencia observada en el XAPK. Esto no equivale a integrar el motor completo de Live Transcribe. Antes de cambiar el valor predeterminado, comprobar disponibilidad de español y comparar el mismo audio en modo avión, midiendo WER, tiempo de primer texto, pérdidas entre sesiones y memoria. Mantener la ruta conectada separada del objetivo independiente/sin conexión.

Fuentes públicas para interpretar las evidencias:

- [Cliente abierto de Google Live Transcribe](https://github.com/google/live-transcribe-speech-engine): cliente Cloud Speech, no pesos locales completos.
- [SpeechRecognizer de Android](https://developer.android.com/reference/android/speech/SpeechRecognizer): API pública de reconocimiento on-device.
- [Protocolo SODA en Chromium](https://github.com/chromium/chromium/blob/main/chrome/services/speech/soda/proto/soda_api.proto): describe reconocimiento local, resultados parciales/finales y contexto; no demuestra que el mismo binario o configuración se use en Android.

## Investigación adicional de la ruta conectada — 8 de octubre de 2026

Se encontró el literal `latest_long` en `classes2.dex`, utilizado mediante una instrucción const-string en el método `b` de la clase ofuscada `Lflg;`. También se encontró `speech.googleapis.com` en el método `d` de `Ljmh;`. Se inspeccionaron tablas DEX y referencias a cadenas; no se reconstruyó el flujo completo ni se capturó una solicitud activa. Por tanto, `latest_long` es un candidato presente en el código, no una identificación confirmada del modelo usado en la prueba del usuario. No se encontró un literal Chirp en esta búsqueda; su ausencia no descarta selección del lado del servidor.

El repositorio oficial de Live Transcribe publica un cliente Cloud Speech, no los pesos de su modelo. La API pública Cloud Speech permite seleccionar `latest_long` para conversaciones y contenido largo, con resultados streaming. Una integración conectada requeriría credenciales propias, facturación y envío de audio a Google; no cumpliría el modo totalmente independiente sin conexión. El acceso local mediante SpeechRecognizer es otra vía y no garantiza compartir el modelo de esta ruta conectada.

Fuentes: https://github.com/google/live-transcribe-speech-engine y https://docs.cloud.google.com/speech-to-text/docs/v1/transcription-model

## Consulta real de idiomas del emulador

La prueba `SystemSpeechSupportTest` consultó `createOnDeviceSpeechRecognizer` y `checkRecognitionSupport` con `es-ES`, sin iniciar grabación ni descarga. Android respondió: reconocimiento local disponible; único idioma instalado `en-US`; español de España `es-ES` y de Estados Unidos `es-US` entre los idiomas admitidos; ninguna descarga pendiente. Esto demuestra que nuestra app puede consultar la disponibilidad del español a través de la API pública en este emulador. No demuestra que el proveedor local sea el mismo modelo que produjo la transcripción conectada ni que los pesos sean exportables.

Los servicios enumerados por PackageManager son AiAiSpeechRecognitionService (`com.google.android.as`) y GoogleTTSRecognitionService (`com.google.android.tts`). Para comprobar la transcripción local española, debe descargarse el idioma y repetirse la consulta antes de probar en modo avión.
