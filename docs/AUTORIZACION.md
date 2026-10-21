# Roles y permisos implementados

Actualizado el 21 de octubre de 2026. Esta es la referencia de la política aplicada en el código.

## Catálogo y configuración inicial

Hay 45 permisos implementados en 12 módulos. Los conteos siguientes corresponden a una instalación nueva; las asignaciones personalizadas se conservan.

| Rol | Permisos iniciales | Alcance |
| --- | ---: | --- |
| ROOT | 45 | Todos los permisos implementados; sus permisos no se pueden editar. |
| ADMIN | 44 | Administración, Roles, Auditoría y Backup; sin operaciones destructivas ni acceso reservado a ROOT. |
| AUDITOR | 13 | Lectura operativa, usuarios, auditoría, reportes y visualización de eliminados. |
| SUPERVISOR | 27 | Operaciones, invalidación, restauración y reportes; sin gestión de usuarios y roles. |
| OPERADOR | 13 | Crear y consultar operaciones, crear/editar inventario y personas; sin invalidar ni restaurar. |
| CONSULTOR | 9 | Lectura operativa y reportes; sin usuarios, roles, auditoría ni eliminados. |

Las transacciones no admiten edición ni aprobación. Ingresos, Egresos y Préstamos utilizan CREATE, READ, DELETE (invalidar) y RESTORE. Los permisos UPDATE de esos módulos, PRESTAMOS.APPROVE y CONFIGURACION no se incluyen en el catálogo inicial ni se aceptan en PermissionPolicy; no existe una migración nueva de limpieza para bases anteriores.

## Autorización efectiva

- Cada validación consulta el usuario, su rol y los permisos vigentes en la base. Los usuarios y roles inactivos o eliminados no autorizan acciones; activo y status del rol deben coincidir en estado activo.
- ROOT tiene acceso a los permisos implementados aunque se hayan retirado accidentalmente sus asignaciones. Las operaciones destructivas siguen reservadas a ROOT aunque se asignen por error a otro rol.
- ROLES.UPDATE edita datos del rol. ROLES.ASSIGN_PERMISSIONS permite gestionar sus permisos. No se puede modificar ROOT ni delegar privilegios superiores a los propios o delegar operaciones destructivas.
- USUARIOS.UPDATE edita datos. Cambiar un rol existente exige además USUARIOS.CHANGE_ROLE. No se permite cambiar el rol propio, asignar ROOT, eliminar/desactivar la cuenta propia ni modificar SYSTEM.
- Los nombres de los roles del sistema se conservan para mantener las funciones que dependen de su identidad. Los roles personalizados mantienen su nombre al mostrarlos.
- Ver eliminados exige SISTEMA.VIEW_DELETED. Restaurar exige además RESTORE del módulo; ambos permisos son independientes.
- Las pantallas ocultan acciones no disponibles o las dejan deshabilitadas según su flujo. Cuando corresponde se muestra un aviso rojo compartido; no se añade un mensaje por cada fila de acciones. Usuarios oculta sus botones no autorizados; Reportes oculta o deshabilita opciones según los permisos aplicables.

## Funciones por identidad del rol

La exportación CSV/Excel desde Inventario continúa siendo exclusiva de ADMIN y ROOT. No existe un permiso delegable para habilitarla a otros roles. La importación sigue reservada a ROOT mediante la política de operaciones destructivas.

Los backups automáticos se ejecutan como operaciones internas del sistema. Los manuales comprueban BACKUP.CREATE y las eliminaciones BACKUP.DELETE.

## Reportes y auditoría

Generar reportes exige REPORTES.READ, REPORTES.GENERATE y lectura de los módulos consultados. La exportación desde la pantalla exige además REPORTES.EXPORT. Elegir eliminados/invalidados exige VIEW_DELETED; sin este permiso los históricos predeterminados solo muestran operaciones activas.

Los reportes de usuarios excluyen SYSTEM y cuentas ROOT independientemente del rol que exporte. La actividad detallada por usuario exige además lectura de Usuarios y Personas, y muestra transacciones por fecha de registro, no todas sus acciones de Auditoría.

La exportación de Auditoría exige AUDITORIA.READ y AUDITORIA.EXPORT. DASHBOARD.READ autoriza las estadísticas generales del inicio; no concede permisos de escritura.

## Actualización de instalaciones existentes

La matriz corregida está en las migraciones originales: cambiar esos archivos no vuelve a aplicarlos en una base que ya los tenga registrados. La regeneración completa se utilizó con datos de pruebas desechables; no es un procedimiento de actualización para una base operativa. Para conservar datos existentes se necesita un respaldo comprobado y una migración específica. No se agregó una migración de limpieza. Consulte [Base de datos](DATABASE.md) y [Actualización](GETTING_STARTED.md).

Los cambios de asignaciones de permisos se guardan conjuntamente en una transacción y se registran en Auditoría.

## Comprobaciones recomendadas en la interfaz

1. ADMIN no puede editar permisos de ROOT ni seleccionar operaciones destructivas para otro rol.
2. Un rol con ROLES.UPDATE y sin ASSIGN_PERMISSIONS ve la acción de permisos bloqueada con aviso rojo.
3. Un rol con USUARIOS.UPDATE y sin CHANGE_ROLE puede editar el nombre de una cuenta, pero no su rol.
4. Un rol con AUDITORIA.READ y sin EXPORT puede consultar Auditoría, pero no exportar.
5. Un usuario con REPORTES y sin USUARIOS.READ no puede generar reportes de usuarios.
6. AUDITOR puede ver eliminados sin disponer de RESTORE.
7. Un usuario desactivado durante una sesión pierde autorización en la siguiente acción comprobada.
8. ADMIN y ROOT pueden exportar desde Inventario; un rol personalizado con los mismos permisos operativos no puede.


## Importación de inventario

La importación sigue reservada a ROOT y comprueba autorización tanto al preparar la vista previa como al guardar. Permite solo crear, solo actualizar o ambas operaciones. Valida el archivo completo; un error impide importar todas sus filas. Los códigos vacíos generan productos nuevos y los códigos existentes identifican actualizaciones. Los productos inactivos y eliminados se bloquean sin reactivarse. Una descripción vacía conserva la existente al actualizar.

El guardado y la auditoría comparten una transacción. La auditoría registra el stock anterior, nuevo y su diferencia; estos ajustes no crean ingresos o egresos. Los reportes de movimientos continúan representando las transacciones de esos módulos. Si el inventario cambió después de la vista previa, se exige revisarlo nuevamente. Cada vista previa puede ejecutarse una sola vez, incluso si la operación falla.

CSV admite UTF-8 con BOM, coma o punto y coma y campos entrecomillados con saltos de línea. Excel conserva el formato visible de los códigos; para códigos largos o con ceros iniciales se recomienda celdas de texto. Las fórmulas no se evalúan. Las cantidades admiten hasta dos decimales, sin separadores de miles, entre 0 y 99999999.99.


## Integridad de ingresos, egresos y préstamos

La creación, invalidación y restauración guardan stock, cabecera, productos y Auditoría dentro de una misma transacción. Los fallos revierten todos los cambios de la operación. Si existe una transacción externa, se usa un savepoint sin confirmarla. Los registros se vuelven a consultar bajo bloqueo en MySQL y los productos se bloquean por ID en orden ascendente.

Las cantidades deben ser finitas, positivas, con hasta dos decimales y dentro de DECIMAL(10,2). El stock no puede quedar negativo ni superar 99999999.99, tampoco al invalidar ingresos o restaurar egresos. Las filas repetidas de un producto se suman antes de comprobar disponibilidad. La creación y restauración exigen productos activos; la invalidación puede corregir movimientos históricos de productos inactivos.

Los préstamos pendientes o vencidos pueden devolverse si están activos. Para mismo producto y comprado/pagado, las cantidades por producto deben coincidir con todo lo prestado; no existe flujo de devoluciones parciales. Reemplazado y Otro conservan la posibilidad de productos distintos y exigen notas que expliquen la liquidación completa; no se comparan cantidades de unidades diferentes. Invalidar una devolución reabre el préstamo; restaurarla lo cierra si todavía está abierto. No se permite invalidar un préstamo con devolución activa ni restaurar una devolución si otro ingreso ya cerró el préstamo.

La edición manual de inventario guarda Auditoría de forma atómica y verifica que el stock no haya cambiado desde que se abrió la edición. Estos controles previenen nuevas inconsistencias; no recalculan ni reparan automáticamente los movimientos históricos.
