# Guardar el historial en la cuenta Google

La app conserva una copia local y sincroniza el historial de conversaciones con Cloud Firestore bajo `users/{UID}/conversations/{id}`. Cada UID solo puede leer o modificar sus propios documentos. La transcripción se divide en fragmentos para admitir clases largas. Se sincronizan el texto, las preguntas y respuestas, el resumen, el mapa, las carpetas usadas y los textos o tareas guardados (`users/{UID}/savedItems`). El audio no se almacena.

## Preparar Firebase

1. Abre el proyecto **Asistente V** en Firebase Console.
2. En el menú de productos, abre **Firestore Database** y elige **Create database**.
3. Selecciona el modo de producción y una región próxima a tus usuarios. La región se debe elegir con cuidado.
4. Después de crearla, abre **Rules** y publica el contenido del archivo [`firestore.rules`](../../firestore.rules). No uses reglas abiertas a todo el mundo.
5. Mantén habilitado Google en **Authentication → Sign-in method** y usa el `google-services.json` de este proyecto.

No hace falta crear colecciones manualmente: aparecen al guardar la primera conversación. En **Ajustes → Cuenta de Google**, la app muestra si pudo sincronizar. Si aún no existe la base o las reglas no están publicadas, el historial sigue guardándose localmente y el estado muestra que falta completar la conexión.

Las conversaciones creadas antes de iniciar sesión permanecen en el espacio local anterior hasta que se elija **Importar conversaciones locales anteriores**. Después de importarlas, también se sincronizan.
