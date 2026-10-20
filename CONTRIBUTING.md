# Colaborar

Abre una incidencia con el comportamiento esperado, versión de Android y pasos reproducibles. Usa audio sintético o una muestra autorizada sin datos personales.

Áreas de colaboración: reconocimiento con ruido, filtros de preguntas, organización del conocimiento, accesibilidad y rendimiento en dispositivos físicos.

Abre el proyecto en Android Studio y ejecuta `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Las pruebas de instrumentación requieren emulador ARM64 o teléfono compatible y, para las pruebas de motores, modelos importados. No incluyas modelos, claves, bases de datos ni grabaciones personales en tus cambios.

Las clases `OnlineLiveServiceTest` y `StudentContextLiveTest` realizan solicitudes reales a Pollinations con ejemplos ficticios fijos. Selecciona las clases de instrumentación que quieras ejecutar para controlar estas llamadas; no requieren clave. Las pruebas controladas de `OnlineAssistantTest` y `LessonContextTest` usan respuestas simuladas y verifican cancelación, contexto y exclusión de la biblioteca.
