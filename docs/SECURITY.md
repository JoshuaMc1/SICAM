# Seguridad de SICAM

Revisado el 21 de octubre de 2026. Este documento describe controles observables en el código y distingue las limitaciones actuales; no certifica que el sistema esté libre de vulnerabilidades.

## Autenticación

User utiliza jBCrypt 0.4 para crear y verificar hashes de contraseña. `BCrypt.gensalt()` aplica su configuración predeterminada y un salt por contraseña; no se guardan contraseñas de cuentas como texto plano en users.password.

UserService comprueba el estado de la cuenta y del rol. SYSTEM no es una cuenta interactiva. Los mensajes de inicio pueden distinguir una cuenta bloqueada/inactiva de credenciales incorrectas según el flujo.

La interfaz de Usuarios crea/restablece cuentas ordinarias con una contraseña temporal basada en el año y marca `must_change_password`. No debe interpretarse como una contraseña aleatoria ni como una credencial adecuada para mantener sin cambio. ROOT se crea mediante el asistente y no tiene una contraseña publicada por defecto.

El cambio desde Perfil valida la contraseña actual, la confirmación y una longitud mínima de seis caracteres. No se documenta una política uniforme de complejidad, caducidad, MFA o bloqueo temporal por intentos como si ya estuviera implementada. Esas mejoras requerirían cambios de código.

## Autorización

La referencia del catálogo y de la matriz inicial es [AUTORIZACION.md](AUTORIZACION.md): 45 permisos, 12 módulos y seis roles iniciales.

Authorization y AuthorizationRepository consultan el estado vigente del usuario, rol y permisos. La revocación surte efecto al realizar la siguiente operación que compruebe autorización; no se afirma que cada control visible cambie automáticamente sin una nueva carga.

- Ocultar/deshabilitar botones facilita el uso, pero la seguridad de la operación se comprueba también en servicios.
- ROOT tiene acceso a las acciones implementadas y es la única identidad que puede ejecutar operaciones destructivas.
- ADMIN no puede modificar cuentas ROOT, asignar ROOT ni delegar operaciones destructivas.
- ROLES.UPDATE no concede ASSIGN_PERMISSIONS. USUARIOS.UPDATE no concede CHANGE_ROLE.
- Ver eliminados/invalidados exige SISTEMA.VIEW_DELETED y no concede RESTORE.
- Importar inventario es exclusivo de ROOT; exportar directamente CSV/Excel desde Inventario es exclusivo de ADMIN/ROOT.
- Reportes comprueba lectura de los módulos consultados y los permisos de generación/exportación. Auditoría comprueba READ y EXPORT por separado.

## Protección de identidades y registros

SYSTEM está oculto en la gestión normal y no admite edición ordinaria. ROOT se oculta a usuarios no ROOT y está protegido frente a cambios no autorizados. No se puede cambiar el rol propio ni eliminar/desactivar la cuenta propia. Los roles del sistema conservan su identidad y no se eliminan cuando las reglas impiden hacerlo.

`inactive` y `deleted` son estados diferentes para usuarios, personas, productos y roles. La eliminación lógica mantiene metadatos y la restauración requiere permisos. Las eliminaciones permanentes están sujetas a restricciones de relaciones; no equivalen a una garantía de conservación indefinida de todo el historial.

Las transacciones utilizan `invalidated`, no `inactive`, al invalidarse. TransactionService revierte o reaplica stock con validaciones, bloqueo y auditoría dentro de la misma transacción. No hay permiso ni flujo para editar transacciones o aprobar préstamos.

## Sesiones y conexiones

Session mantiene el usuario en memoria y una revisión para detectar cambios de sesión. El cierre de sesión limpia el usuario. El inicio asíncrono comprueba que sus resultados sigan perteneciendo a la sesión antes de aplicarlos.

No existe un timeout de inactividad de 30 minutos implementado en Session; los valores de timeout que aparecían anteriormente eran propuestas. Tampoco se debe confundir cierre de sesión con cierre de la aplicación y creación de respaldo.

## Configuración de MySQL

MySQLConfigManager guarda `mysql.config` como Properties en la carpeta configurada por AppConfig; por defecto `~/.sicam/`. Los campos se cifran y codifican en Base64.

La implementación deriva una clave de 256 bits con PBKDF2WithHmacSHA256 y 65.536 iteraciones, pero utiliza material de clave y salt fijos en el código y `Cipher.getInstance("AES")`. **No implementa el esquema AES-CBC con salt e IV aleatorios por operación que describía el documento anterior.** No publique el archivo ni considere que su cifrado impide el acceso a alguien con la aplicación y acceso local suficiente.

La URL JDBC actual contiene `useSSL=false`, `allowPublicKeyRetrieval=true` y `serverTimezone=UTC`. No se debe afirmar que la conexión MySQL está protegida con TLS. Revisar cifrado autenticado, almacenamiento de secretos y transporte seguro requiere una tarea de implementación aparte; aquí se corrige la descripción.

## Auditoría

AuditService registra las acciones invocadas por los flujos. Se conservan ID del evento, user_id, acción, tabla, ID afectado, detalles y fecha con hora. AuditRepository incorpora el nombre y username actuales de users mediante LEFT JOIN; esos nombres no son una instantánea histórica de la identidad al momento del evento.

- La exportación completa no se limita a 1.000 eventos.
- El PDF es horizontal y no recorta los detalles a cinco líneas; el CSV conserva comillas y saltos de línea.
- Generar/exportar Auditoría no implica permiso para alterar sus eventos.
- Los respaldos automáticos se registran en backups bajo SYSTEM; BackupService no genera adicionalmente una entrada de audit para su creación automática.
- Los eventos de vencimiento crean una entrada agregada `scheduled_update` cuando cambian préstamos.
- Auditoría no es un registro inmutable ni firmado: el esquema y las cuentas con acceso directo a MySQL no proporcionan esa garantía.

La actividad detallada por usuario de Reportes es un historial de transacciones que creó, no una consulta de todos sus eventos de Auditoría. Un usuario puede tener cero transacciones y muchas acciones auditadas.

## Entradas y exportaciones

Las consultas parametrizadas y las listas de columnas permitidas evitan interpolar entradas como ordenaciones SQL arbitrarias en los repositorios revisados. Esto no certifica automáticamente cada sentencia del proyecto.

La importación valida todas las filas antes de guardar, controla códigos repetidos, cantidades y cambios posteriores a la vista previa. No evalúa fórmulas de Excel.

Escapar comillas y delimitadores produce un CSV válido, pero no neutraliza por sí mismo fórmulas interpretadas por hojas de cálculo. No presente los exportadores como una protección específica contra fórmulas CSV.

FileChooser permite al usuario elegir el archivo de destino; no constituye una restricción que obligue a escribir únicamente dentro de una carpeta segura. Los permisos del sistema operativo siguen aplicándose.

## Respaldos y operación

Los respaldos requieren herramientas externas y contienen información de la base. No se describen como cifrados: gzip comprime, no cifra. El módulo implementa creación, consulta y eliminación; no restauración desde interfaz. Debe probarse la recuperación en un entorno separado.

Las migraciones originales de desarrollo pueden eliminar tablas transaccionales. No debe recomendarse borrar tablas o desactivar el firewall como solución general de soporte. Consulte [Inicio y actualización](GETTING_STARTED.md) antes de actualizar datos existentes.

## Mejoras que siguen pendientes

Una política uniforme de contraseñas, MFA, timeout, cifrado autenticado de configuración, TLS y una revisión del manejo de credenciales de procesos externos son mejoras a evaluar; no se declaran implementadas por esta documentación. La revisión documental no modifica esos controles ni ejecuta acciones en la base de datos.

Reporte incidencias de seguridad al responsable del proyecto sin publicar credenciales, hashes, configuraciones o evidencias con datos personales.
