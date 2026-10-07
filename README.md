# V · Local Voice Assistant Android

![Logo de V](docs/assets/v-logo.svg)

Asistente de voz nativo en Kotlin y Jetpack Compose con transcripción y respuestas locales, historial privado y mapas de conversación.

## Interfaz actual · 0.12

Menú lateral con historial por fecha y búsqueda, chats que puedes continuar por voz, y micrófono compacto en la barra inferior.

![Sidebar con datos sintéticos](docs/screenshots/v-android-sidebar-synthetic.png)

![Chat con datos sintéticos](docs/screenshots/v-android-portrait-synthetic.png)

![Mapa con datos sintéticos](docs/screenshots/v-android-map-synthetic.png)

## Android 0.12: sidebar rediseñado

Menú lateral con filas compactas, iconos vectoriales, búsqueda sin borde, botón destacado para nueva conversación y selección del chat activo. El historial se agrupa en Hoy, Ayer, Últimos 7 días y Anteriores según la fecha de actualización del dispositivo. Lista virtualizada para historiales largos. Textos guardados y Ajustes están en un bloque inferior.

## Android 0.11: menú lateral y barra de voz

El botón ☰ abre un menú lateral con el historial, buscador y «Nueva conversación». Toca un chat para abrirlo y continuar con el micrófono. Textos guardados y Ajustes se encuentran también en el menú. La barra inferior compacta permite escribir y contiene el micrófono; durante la escucha muestra el nivel real de audio y permite pausar. Se retira la navegación inferior y el gran micrófono flotante.

## Android 0.10: chats y mapa interactivo

Toca una tarjeta del historial para abrirla en Mapa. Desde el chat, «‹ Chats» regresa al historial y «＋» inicia otro. Toca el nombre en la barra del chat para nombrar tu materia; el nombre se conserva al continuar otras clases. Pulsa el micrófono en un chat reabierto para añadir la nueva transcripción sin borrar lo anterior.

En Mapa, el título pliega o despliega el esquema. Toca el encabezado de una rama para abrirla con animación. «Temas» agrupa todos los fragmentos por palabras destacadas y permite revisarlos; es una clasificación léxica, no una identificación semántica garantizada. El resumen sigue usando contexto reciente.

## Android 0.9: conversaciones organizadas

El historial conserva la transcripción, preguntas, respuestas y resumen de cada conversación. Al abrir una entrada se muestra su mapa, reconstruido del texto guardado. Las tarjetas incluyen tema, fecha y contadores. Se elimina el encabezado «Tu conversación». Las nuevas sesiones ya no recortan el historial a 80 fragmentos o 30 respuestas; el contexto enviado al modelo sigue limitado para no saturarlo. El resumen no representa necesariamente toda una sesión larga. Los fragmentos descartados por versiones anteriores no pueden recuperarse.

# V · Asistente Android independiente

Versión 0.12: app nativa Kotlin + Jetpack Compose para el Samsung Galaxy A54 de 8 GB. Escucha, transcripción progresiva, preguntas relevantes, respuestas locales, resumen reciente y textos seleccionados persistentes. Funciona sin servidor Mac y sin servicios remotos de transcripción. El laboratorio de rendimiento sigue disponible en Ajustes.

## Interfaz y uso

- Botón de voz flotante centrado en la parte inferior de Conversar. Toca para iniciar y vuelve a tocar para pausar. El indicador muestra RMS medido del audio del micrófono, no una animación de audio simulado.
- Diseño oscuro con acentos violetas; la escucha usa un indicador verde. Pestañas Conversar, Guardado y Ajustes; navegación compacta en horizontal.
- Texto plano continuo por defecto. «Editar o guardar fragmentos» permite corregir texto, asignar una etiqueta manual y guardar una selección. Activar «Etiquetar voces» muestra los bloques; no añade clasificaciones de pregunta/afirmación al texto.
- Respuestas progresivas junto a la transcripción en ventanas de al menos 600 dp; en vertical aparece primero la respuesta más reciente para conservar legibilidad. Explicaciones de conocimiento general, sin verificación externa.
- Detección por señales lingüísticas conservadoras: requiere una pregunta con contenido, descarta fragmentos incoherentes conocidos y no responde a cualquier «que». No es una evaluación semántica infalible ni una reparación del audio.
- Resumen de contexto reciente: solicita una actualización cada 20 segundos cuando la generación está libre, o al pulsar Actualizar resumen. Usa las últimas ocho intervenciones hasta 1.200 caracteres; no se presenta como resumen exhaustivo de una sesión larga. Los fragmentos relevantes son citas literales seleccionadas por reglas de longitud y palabras clave.
- Guardado explícito de datos estructurados: recuerdos, tareas, cursos y proyectos con IDs estables, edición y borrado. Las tareas permiten curso/proyecto, fecha escrita por el usuario y completar/reabrir. No se inventan fechas, responsables ni obligaciones a partir del audio. La procedencia distingue texto escrito, transcripción, resumen y respuesta del modelo.
- El mensaje escrito «qué tengo pendiente de Cálculo» consulta los textos seleccionados localmente. Las preguntas del audio nunca reciben esos datos ni ejecutan acciones de guardado.
- «Escuchar» usa una voz española del sistema que no requiera conexión; si no está instalada, indica cómo habilitarla. Pausa el micrófono para evitar transcribir su propia salida. No hay lectura automática.

## Actualizar desde el laboratorio

Instala el nuevo APK **encima de la app anterior**, sin desinstalarla. Conserva el identificador `com.heyler.voicelab` y la firma de desarrollo usada en esta máquina, por lo que mantiene los modelos ya importados. No incluye los pesos dentro del APK. La distribución actual es un APK de desarrollo; una publicación en Play Store requiere su propia firma y preparación de distribución.

## Concurrencia y contexto

ASR y generación se ejecutan en trabajos separados. Solo una generación local trabaja a la vez; la cola admite ocho solicitudes pendientes y muestra cuando una pregunta se omite por saturación. Las hipótesis se revisan sobre el mismo fragmento, con espera de 1.500 ms para parciales y 500 ms para finales. Cambios de la pregunta invalidan la respuesta derivada; cambios de narración que conservan la pregunta no la repiten. No se reinicia el motor cada frase.

La transcripción y las respuestas se conservan en el historial privado de cada conversación; solo el contexto enviado al modelo está limitado. Borrar sesión elimina ese contexto y su entrada de historial sin borrar textos elegidos. Al enviar la app al segundo plano se detienen escucha y generación; la rotación conserva la sesión y la captura; los modelos pueden seguir residentes mientras exista el proceso. La base SQLite de textos elegidos usa almacenamiento privado de la app, copias de seguridad deshabilitadas y borrado directo, sin embeddings ni índices externos. No se afirma cifrado propio adicional al almacenamiento del dispositivo ni autenticación biométrica.

## Alcance actual

Las etiquetas de voz son manuales. No incluye huellas de voz, diarización automática, Telegram, notificaciones de tareas, extracción automática de fechas relativas ni verificación en Internet. La versión 0.6 añade captura directa de audio de reproducción mediante consentimiento de Android. No promete filtrar música o corregir universalmente los errores del micrófono con altavoces. La validación funcional usa audios sintéticos; la calidad acústica y la concurrencia sostenida de esta nueva versión requieren pruebas en el A54 real.

## Herramientas y motores

- Android Gradle Plugin 9.4.1, Gradle 9.6.0, Kotlin y Compose Compiler 2.4.0; Android SDK 37.
- Android 12/API 31 o posterior, ARM64. El A54 es el dispositivo objetivo; no se afirma rendimiento sin medirlo allí.
- Moonshine Voice Android 0.1.5: Small Streaming español, arquitectura 4. Los mismos archivos del modelo de escritorio, sin reinstanciarlo por frase y sin reinicios arbitrarios cada 12 segundos.
- LiteRT-LM Android 0.18.0, CPU. Modelo inicial de prueba: Qwen3 0.6B mixed INT4. La CPU permite una base comparable; GPU/NPU no se presentan como compatibles o más rápidas en el Exynos del A54 sin ensayos.
- La plantilla de conversación está adaptada a Qwen3 y a los mensajes con partes de LiteRT-LM 0.18.0. Importar otros modelos no implica compatibilidad; requieren su plantilla y pruebas propias.
- Modelos importados con el selector de archivos. El APK no necesita permiso de Internet; no incluye pesos grandes. No hay descargas ni fallbacks remotos ocultos.

## Instalar y preparar

1. Compila con Android Studio o `./gradlew :app:assembleDebug`. Se requiere JDK 21 o posterior compatible con Gradle; se probó el JDK de Android Studio.
2. Instala `app/build/outputs/apk/debug/app-debug.apk` en el teléfono o con `adb install -r ...`.
3. Importa `moonshine-es.zip` con **Importar voz ZIP**. Contiene los ocho archivos de Small Streaming ES, todos en la raíz. Los modelos se importan por separado y no se incluyen en este repositorio.
4. Descarga y copia `qwen3_0_6b_mixed_int4.litertlm` al teléfono, y usa **Importar LLM**. Fuente pública fijada: https://huggingface.co/litert-community/Qwen3-0.6B/tree/a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76 . No requiere un servidor; la primera descarga sí necesita red.
5. Activa modo avión. Los ensayos con archivos y las respuestas deben seguir funcionando. El permiso del micrófono se pide solo al pulsar **Escuchar micrófono**.

## Protocolo de comparación

Mantén brillo, volumen, aplicaciones abiertas y carga de batería lo más constantes posible. Evita cargar el teléfono durante una medición de consumo; el informe incluye si estaba cargando. Espera a que se enfríe entre escenarios y alterna el orden para reducir sesgos por calentamiento.

- **Voz sola:** descarga el LLM de memoria, carga Moonshine una vez y transcribe los mismos cinco WAV sintéticos incluidos, tres vueltas (15 casos). WER normalizado y RTF por caso. Es transcripción de clips, no la latencia del micrófono en vivo.
- **LLM solo:** descarga el ASR de memoria, carga el LLM una vez y responde las mismas cinco consultas fijas por vuelta, tres vueltas (15 casos). Se mide primer texto y tiempo completo. Se desactiva pensamiento y se limita a 160 tokens de salida; no se certifica calidad factual.
- **Integrados:** mantiene ambos motores cargados, transcribe cada WAV y ejecuta la misma consulta fija del caso. Esto permite comparar cargas equivalentes y el coste de coexistencia en memoria. No es todavía selección automática de preguntas en conversación libre.
- **20 minutos:** repite el escenario integrado durante al menos 20 minutos, terminando el caso iniciado. Permite comparar rapidez inicial y final, muestras PSS, batería y estado térmico. Se detiene ante temperatura de batería ≥45 °C o estado térmico Android ≥SEVERE. El umbral es conservador y no sustituye medidas térmicas del fabricante.
- **Detener:** detiene captura y solicita cancelación del LLM. Una llamada nativa de ASR de un clip puede tardar en retornar; no se promete interrumpir esa inferencia en cero milisegundos. No se inicia el siguiente caso tras cancelar.
- **Exportar métricas:** guarda JSON elegido por el usuario. Contiene métricas, modelo general del dispositivo y versión Android, no audio, transcripciones, preguntas personales, seriales, Android ID ni cuentas. Las métricas sintéticas se guardan primero en el almacenamiento privado de la app; **Borrar resultados** las elimina.

P50/p95 usan rango más cercano. Con 15 casos son descriptivos de esta muestra pequeña, no garantía de la población. Temperatura corresponde al sensor de **batería**, no al procesador. PSS se muestrea entre casos, no representa un pico exacto ni memoria exclusiva del modelo. El porcentaje de batería es una medida gruesa; no es consumo en mWh.

## Captura y privacidad del laboratorio

La captura manual usa AudioRecord mono 16 kHz, memoria y los eventos reales del SDK. No se guardan grabaciones del usuario. Los cinco WAV incluidos son voz sintetizada con Piper para este ensayo; no son grabaciones personales. Al abandonar la actividad del laboratorio se cancela la escucha. La captura de micrófono se detiene al salir; la entrada de video utiliza un servicio de proyección solo durante la sesión autorizada. No hay permiso de red; se eliminan explícitamente permisos heredados de dependencias que no necesita el laboratorio. Copias de seguridad deshabilitadas.

## Verificación

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
# Con modelos importados y dispositivo/emulador autorizado:
./gradlew :app:connectedDebugAndroidTest
```

Los tests instrumentados ejecutan los motores nativos; sin modelos fallan explícitamente. Las pruebas del asistente también cubren audio sintético → pregunta → respuesta, resumen, revisiones, pausa en segundo plano y CRUD/reapertura/borrado de textos elegidos. El resultado del emulador debe mantenerse separado del A54 real. No publicar cifras de batería/temperatura del emulador como medidas del teléfono.

El 6 de octubre de 2026 se verificaron compilación, dos pruebas unitarias y cinco instrumentadas en un emulador ARM64. Las instrumentadas incluyen pantalla nativa, ausencia de permiso de Internet, transcripción real, respuesta local real y comparación de las tres fases (60 mediciones). Los audios son sintéticos. La prueba sostenida de 20 minutos y las mediciones del A54 físico quedan pendientes.

`speech_load_ms` y `llm_load_ms` representan la última carga efectiva del motor en esta sesión; reutilizar un motor ya cargado no sustituye ese valor por cero. No son tiempos de arranque completo de la aplicación. La primera respuesta de cada fase puede incluir calentamiento y debe conservarse por separado al analizar el JSON.

Fuentes: [LiteRT-LM Kotlin](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md), [Moonshine Android](https://moonshine-voice.readthedocs.io/en/latest/quickstart/), [Qwen LiteRT](https://huggingface.co/litert-community/Qwen3-0.6B).

## Validación de la versión del asistente

La versión 0.3 corrige la cancelación de preguntas pendientes cuando llegaban revisiones con la misma pregunta. También reúne preguntas divididas entre fragmentos de audio y conserva su origen para invalidar respuestas si se edita el texto. Las preguntas tienen prioridad sobre el resumen; un resumen interrumpido se reanuda después. El modelo se prepara mientras empieza la captura.

Las respuestas de conversación están limitadas a 96 tokens y solicitan explicaciones directas en texto plano; los resúmenes usan 144 y el laboratorio conserva 160 para su protocolo comparable. «Primer texto» mide desde la entrada en la cola hasta la primera salida: incluye espera y carga del modelo, excluye reconocimiento de audio y estabilización de la pregunta. No es una garantía de latencia.

La revisión visual utiliza renders de la ventana de la propia app mediante instrumentación, en vertical y horizontal, con texto sintético y una respuesta del modelo local. Las imágenes están en `docs/screenshots/`; no contienen grabaciones, pantallas personales ni datos del usuario. La captura física, la voz TTS instalada y la concurrencia sostenida de esta actualización siguen pendientes de confirmar en el A54. Las métricas anteriores del teléfono pertenecen al laboratorio.

Validación del 6 de octubre de 2026: compilación correcta, 12 pruebas unitarias y 14 instrumentadas correctas en emulador ARM64 con motores reales. Tras el ajuste de presentación de fórmulas se repitieron las unitarias y el render visual afectado. APK local: `dist/v-asistente-android-0.3.apk`. Instalar encima de la versión anterior.

## Versión 0.12: preguntas estables y navegación

Las preguntas incompletas terminadas en artículos o conectores se descartan antes de generar. Añadir una segunda pregunta a un fragmento conserva las respuestas de las preguntas que siguen presentes; editar o eliminar una pregunta sí invalida su respuesta. La selección visible permanece en la pregunta elegida, y una lista con desplazamiento permite recorrer las últimas 30. Los enlaces subrayados en la transcripción literal abren su respuesta; el texto reconocido no se sustituye ni se reescribe. La ventana de transcripción mantiene 80 fragmentos; las respuestas conservadas pueden seguir accesibles desde la lista aunque su fragmento ya no esté visible.

La captura intenta activar `NoiseSuppressor` de Android cuando el dispositivo lo ofrece y permite habilitarlo; se libera al detener la grabación. No hay un separador de música/voz ni garantía de supresión en el A54: depende del efecto disponible en el teléfono y debe comprobarse con su micrófono. El filtro lingüístico reduce respuestas a ruido transcrito; no corrige la transcripción ni puede detectar todos los errores coherentes. Los cambios de palabras dentro de una pregunta todavía pueden invalidar su respuesta para evitar asociar explicaciones a texto distinto.

APK: `dist/v-asistente-android-0.4.apk`, actualización sobre la instalación anterior.

Validación 0.4, 6 de octubre de 2026 (Lima): 14 pruebas unitarias y 8 instrumentadas afectadas correctas. Incluye dos preguntas seguidas sobre el mismo fragmento con generación real, conservación del ID y del texto de la primera respuesta, rechazo de «que hay un», y renders nativos en ambas orientaciones. La supresión acústica física no se certifica con el emulador.

## Versión 0.12: preparación y memoria de conversación

El modelo de voz se prepara al abrir la app, sin activar el micrófono. Al pulsar el botón, la pantalla distingue preparación de grabación: `listening` solo se activa después de iniciar AudioRecord. Si la voz ya está cargada, iniciar captura evita esperar al bloqueo de generación del LLM. La preparación de respuestas se lanza después de iniciar captura; los dos motores siguen compitiendo por CPU, por lo que no se promete latencia constante en el A54 ni se evita la carga inicial en frío.

Por petición del usuario, la conversación ahora se conserva automáticamente en `conversations.db`, almacenamiento privado sin copia de seguridad ni permiso de Internet. Guarda el contexto visible (hasta 80 fragmentos y 30 respuestas por conversación), etiquetas manuales y resumen; no conserva audio. Se actualiza aproximadamente cada 300 ms mientras hay cambios y al pasar a segundo plano. Una terminación abrupta puede perder el último intervalo. El historial permite abrir, borrar y comenzar otra conversación desde Guardado. Las tareas y recuerdos estructurados siguen requiriendo elección explícita. Las conversaciones que ya se perdieron con la versión anterior no se pueden reconstruir.

Los modelos nunca interpretan el audio como autorización para guardar tareas o ejecutar acciones. Las preguntas manuales también reciben contexto de la conversación activa; se seleccionan fragmentos relacionados, texto reciente y resumen, con límite de contexto. El historial no se añade entero al prompt y no se consultan automáticamente otras conversaciones.

APK: `dist/v-asistente-android-0.5.apk`. Instalar como actualización.

El reinicio de captura espera la finalización de la captura anterior. Esto evita iniciar dos sesiones nativas al pulsar pausar/iniciar rápidamente. La validación acústica del micrófono y los tiempos de arranque reales del A54 siguen pendientes.

Validación 0.5 (6 de octubre de 2026, Lima): compilación correcta, 14 pruebas unitarias y 10 instrumentadas afectadas correctas. Incluye recuperación y borrado de historial tras reabrir la base, conservación de respuestas interrumpidas, siete regresiones de preguntas y un render de interfaz en ambas orientaciones. Los renders utilizan una conversación temporal aislada; no guardan datos de prueba sobre el historial del usuario.

## Versión 0.12: audio directo de video y revisión de cifras

Para videos reproducidos en el mismo teléfono: Ajustes → Fuente de audio → Audio del video. Al iniciar, Android solicita consentimiento MediaProjection y permiso RECORD_AUDIO. El servicio usa el tipo mediaProjection, notificación de detención y AudioPlaybackCaptureConfiguration para medios/juegos; excluye la propia app. No crea VirtualDisplay, no captura imágenes y no usa micrófono en esta ruta. Cambiar fuente, pausar, ocultar la app o revocar el token detiene la captura. Mantener el asistente visible en pantalla dividida permite reproducir el video en otra app. Tras 12 segundos continuos sin señal digital, se informa de reproducción pausada/captura bloqueada; no hay fallback silencioso al micrófono.

La app del video debe permitir captura y estar en el mismo perfil Android. La música ya mezclada en la banda sonora permanece: esto evita el recorrido acústico altavoz/micrófono, no separa instrumentos y diálogo. Para videos de otro aparato, la entrada directa no aplica; se recomienda reproducirlos en el A54 o acercar el micrófono a una fuente clara con volumen moderado.

Se resaltan menciones literales de cifras y palabras numéricas para revisión, sin convertirlas, corregirlas por conjetura ni alterar la transcripción. No hay calibración de confianza por palabra en esta versión.

El informe `docs/numeric-recognition-synthetic.json` contiene tres frases Piper y seis inferencias nativas: precio 750, año 1995, medida 3,5 y porcentaje 12. Se reconocieron las cuatro menciones tanto limpias como con tres tonos a 10 dB SNR. Se acepta la forma verbal equivalente; no se afirma que el modelo siempre produzca dígitos. El ensayo es de clips sin streaming, usa emulador ARM64 y tonos estacionarios; no reproduce música real, habla del usuario, reverberación, latencia en vivo o rendimiento del A54.

Validación: 16 pruebas unitarias. La prueba de permiso ausente confirma que el servicio informa el fallo sin iniciar la captura. La ruta positiva de MediaProjection requiere consentimiento del selector Android: pendiente de confirmar en el A54 con la aplicación de video concreta. APK `dist/v-asistente-android-0.6.apk`.

Referencia de la plataforma: https://developer.android.com/media/platform/av-capture y https://developer.android.com/develop/background-work/services/fgs/service-types#media-projection .

Validación 0.6, 7 de octubre de 2026 (Lima): compilación correcta, 16 pruebas unitarias y 12 instrumentadas correctas. Estas últimas cubren seis inferencias numéricas en un ensayo, fallo por consentimiento ausente, dos pruebas de historial, siete regresiones del asistente y un render de ambas orientaciones. La captura positiva del video concreto en el A54 sigue pendiente.

## Versión 0.12: interfaz organizada

La conversación se divide en Texto, Preguntas y Resumen. Texto mantiene una vista previa de la respuesta progresiva en vertical y paneles al costado en horizontal. Los enlaces de preguntas abren su sección; el selector conserva la pregunta elegida. El botón de resumen y las consultas escritas llevan a la sección correspondiente. La selección de sección se conserva al rotar.

Guardado se organiza en Conversaciones y Mis textos, con contadores y estados vacíos. Ajustes agrupa fuente de audio, controles de conversación, modelos y privacidad en tarjetas. El micrófono flotante permanece centrado abajo en Conversar; las otras pantallas disponen de toda la zona de contenido.

APK: `dist/v-asistente-android-0.7.apk`, actualización sobre la app anterior. La compilación y la revisión visual se verifican con renders de las pantallas de texto, preguntas, biblioteca y ajustes y de la vista horizontal. Las conversaciones de ejemplo y el contenido de la prueba son sintéticos; no se muestran datos personales. Esta actualización cambia presentación y navegación; conserva los motores y el historial de 0.6.

Validación 0.7, 7 de octubre de 2026 (Lima): compilación correcta y revisión de renders nativos de texto, preguntas, biblioteca, ajustes y orientación horizontal. Se ejecutó la prueba visual con motores locales y se revisaron las imágenes, ajustando espacio de cabecera y controles. La revisión utiliza contenido sintético y no muestra el historial del usuario.

## Versión 0.12: marca y mapa de conocimiento

Logo vectorial propio: una V conectada por nodos y barras de voz, en violeta y verde. Se usa en la cabecera y como icono adaptativo del launcher, con capa monocroma. Fuente SVG: `docs/assets/v-logo.svg`; recursos Android en `app/src/main/res/drawable/brand_*`.

Mapa organiza el contexto conservado en un nodo central y ramas desplegables: resumen generado, ideas literales del audio, preguntas/respuestas y cifras que conviene comprobar. El título usa hasta tres palabras frecuentes; no es una identificación semántica certificada del tema. Las ideas y referencias de cifras conservan los IDs y el texto de sus fragmentos originales. Tocar una pregunta abre su respuesta; los fragmentos se pueden ver/corregir. El resumen y las respuestas generadas están distinguidos de la transcripción literal.

Al pausar una escucha activa se espera a que termine la captura, se abre Mapa y se solicita el resumen reciente. Si un resumen ya estaba pendiente, se espera y después se comprueba de nuevo el contexto. Antes de solicitar el resumen se comprueba que la escucha siga detenida; enviar la app al fondo cancela la finalización pendiente. Conversaciones de menos de 100 caracteres se organizan de forma extractiva sin obligar al modelo a generar un resumen. Al ocultar la app se mantiene la política de detener generación; no se promete que el resumen final se produzca en segundo plano.

El mapa se reconstruye a partir de la transcripción, preguntas/respuestas y resumen conservados en el historial privado, sin guardar audio ni imágenes del mapa. Mantiene los límites existentes: 80 fragmentos, 30 respuestas y resumen del contexto reciente. No es un mapa exhaustivo de grabaciones largas ni certifica coherencia, causalidad o exactitud factual. Las cifras no se corrigen por conjetura y el organizador no crea tareas automáticamente.

APK: `dist/v-asistente-android-0.8.apk`, instalar encima de la versión anterior. Los renders de revisión emplean contenido sintético.

Validación 0.8, 7 de octubre de 2026 (Lima): compilación correcta, 18 pruebas unitarias y 11 instrumentadas afectadas correctas. La prueba de finalización simula el estado de escucha detenida y ejecuta el resumen con LiteRT-LM real; no certifica la captura física del A54. Se verificaron los renders nativos, repitiendo el render tras la última presentación de ramas y contadores.
