# Sesión única por cuenta

Revisado el 30 de octubre de 2026. Este control se aplica a clientes actualizados que comparten la misma base MySQL, tanto en un equipo como en equipos distintos.

## Uso y recuperación

1. Inicie sesión con su usuario y contraseña. Si la cuenta ya tiene una sesión vigente, SICAM rechaza el nuevo acceso y conserva la sesión anterior.
2. Para cambiar de equipo, cierre la sesión anterior y vuelva a entrar desde el otro equipo. No existe una excepción por usar el mismo dispositivo.
3. Después de un apagón o cierre forzado, espere hasta un minuto desde la última renovación e intente nuevamente. No necesita que un administrador borre la reserva.

La sesión se renueva aunque no use el teclado o el ratón y mientras tenga un diálogo abierto. **No existe cierre por inactividad.** Suspender el equipo o perder la conexión durante suficiente tiempo puede hacer caducar la sesión. Cuando SICAM confirma que la reserva se perdió, vuelve al acceso y solicita iniciar sesión nuevamente.

Una falla transitoria de conexión se reintenta y no cierra inmediatamente la ventana. Mientras la base no pueda verificar una reserva vigente, las operaciones que exigen autorización no pueden continuar. Recuperar la red no permite renovar una reserva que ya caducó.

## Funcionamiento

| Paso | Componente y comportamiento |
| --- | --- |
| Autenticar | `UserService.login` comprueba credenciales y disponibilidad del usuario/rol antes de llamar a `Session.beginAuthenticated`. |
| Reservar | `SessionRepository.acquire` ejecuta un único `UPDATE` condicional sobre `users`. Solo adquiere una reserva libre o caducada y exige usuario y rol activos. |
| Identificar | `Session` genera un UUID por reserva y guarda el usuario en memoria. MySQL conserva `session_token` y `session_expires_at`. |
| Renovar | `Session.startHeartbeat`, iniciado desde `LoginController`, programa una renovación cada 15 segundos con un hilo daemon independiente de JavaFX. Cada renovación válida extiende la reserva a 60 segundos desde el reloj UTC de MySQL. |
| Autorizar | `Authorization` verifica el token vigente en la base además del rol y los permisos. Un token caducado o sustituido no autoriza operaciones. |
| Detectar pérdida | Si la renovación no afecta ninguna fila, la reserva se marca perdida; `MainController` programa el cierre de sesión en el hilo JavaFX. |
| Liberar | `Session.setCurrentUser(null)` cancela la renovación y libera exclusivamente el token de esa instancia. `App.stop` lo ejecuta en `finally`, después del intento de respaldo de cierre. |

La renovación y la liberación utilizan una conexión JDBC independiente, con límites de cinco segundos para conexión, socket y consultas. No necesitan la conexión ActiveJDBC de otro hilo. La renovación exige que el token siga vigente: no recupera una reserva vencida ni reemplaza la de otro cliente. Un cierre tardío tampoco puede borrar la reserva del nuevo propietario.

Si falla la auditoría del inicio de sesión, se libera la reserva adquirida. Los errores posteriores de apertura de la ventana también limpian la sesión. La revisión local de `Session` permite descartar resultados asíncronos de una sesión anterior.

Este control no sustituye las transacciones y bloqueos de negocio ni revierte operaciones que ya estaban en ejecución. Clientes antiguos que no comprueban la reserva pueden omitirlo; actualice todos los equipos que compartan la base.

## Esquema y actualización

Los campos se añadieron a la migración original `20251114112628_create_user_table.sql`:

| Campo | Tipo | Uso |
| --- | --- | --- |
| `session_token` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL` | Identificador de la reserva, comparado distinguiendo mayúsculas. |
| `session_expires_at` | `DATETIME(6) DEFAULT NULL` | Caducidad calculada en UTC por MySQL. |

Una instalación nueva recibe estos campos. **Reemplazar el JAR no actualiza una tabla existente:** no se añadió una migración `ALTER TABLE`; `MigrationRunner` omite archivos registrados y `CREATE TABLE IF NOT EXISTS` no añade columnas.

Para una instalación con datos, prepare y pruebe una migración específica sobre una copia respaldada antes de actualizar los clientes. Recrear el esquema se reserva para bases de desarrollo desechables. Consulte [Instalación y actualización](GETTING_STARTED.md) y [Base de datos](DATABASE.md).

## Diagnóstico

| Mensaje o situación | Acción |
| --- | --- |
| La cuenta ya tiene una sesión activa | Cierre la sesión en la otra instancia. Si hubo un cierre inesperado, espere hasta un minuto desde la última renovación. |
| Sesión finalizada tras una suspensión o interrupción prolongada | Restablezca la conectividad e inicie sesión nuevamente. |
| No se pudo verificar la reserva de sesión | Revise conexión, logs y presencia de los dos campos en `users`. |
| Sigue sin poder entrar después de caducar la reserva | Compruebe credenciales, estado del usuario y estado del rol; el rechazo también puede indicar que la cuenta dejó de estar disponible. |

No publique los tokens de sesión en capturas o incidencias. La caducidad se consulta con el reloj UTC de MySQL; no depende de que coincida el reloj de cada equipo.

## Pruebas

`mvn test` incluye `SessionRepositoryTest` y `SessionTest` con SQLite: reserva, renovación, caducidad, rechazo de duplicados, liberación por propietario, credenciales incorrectas, fallo de auditoría y autorización de sesiones sustituidas. Las pruebas existentes ajustan sus esquemas de usuarios para incluir los campos nuevos.

La prueba opcional con MySQL se ejecuta con:

```bash
mvn -Dsicam.test.mysql=true -Dtest=SessionMySqlTest test
```

Configure `SICAM_TEST_SECURE_DIR` con una carpeta que contenga `mysql.config`, o las variables `SICAM_TEST_MYSQL_URL`, `SICAM_TEST_MYSQL_USER` y `SICAM_TEST_MYSQL_PASSWORD`. La cuenta MySQL necesita crear y eliminar bases de pruebas. Se usa un esquema aleatorio `sicam_session_test_*`, se carga el DDL original de usuarios y se elimina el esquema al terminar; no se modifican las tablas de la instalación.

La prueba comprueba competencia entre conexiones y renovación real en segundo plano durante 65 segundos sin interacción. La suite SQLite no acredita por sí sola estos comportamientos en MySQL.
