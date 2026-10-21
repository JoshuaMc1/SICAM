# Revisión documental del 21 de octubre de 2026

## Alcance

Se revisaron README.md y todas las guías Markdown versionadas de docs frente al código actual, el pom y las migraciones. Se actualizaron las ocho guías existentes y se agregaron Reportes y este registro. No se modificó documentation.pdf ni el documento Word. Las bitácoras locales de docs/qa se conservan como evidencia histórica, no como especificaciones vigentes.

Esta revisión modifica documentación, no lógica, permisos, configuraciones, migraciones ni datos. El commit de referencia inicial es 740ffa7. Los resultados de QA mencionados corresponden a ejecuciones previas, no a nuevas pruebas funcionales hechas por editar estos documentos.

## Incoherencias corregidas

| Antes | Corrección y referencia |
| --- | --- |
| Java 14/21 como requisito suficiente, mezclado con JavaFX 25.0.1 | Entorno verificado JDK 25; se distingue source/target del compilador de las dependencias de ejecución. Referencia: pom.xml. |
| BCrypt 0.10.2 y nombre de librería incoherente | Dependencia real jBCrypt 0.4; ver pom y User. |
| SQLite descrito como motor automático de producción | DatabaseConfig abre MySQL; SQLite se usa en pruebas. |
| Documentos de 2025 y afirmaciones de producción garantizada | Revisión fechada y alcance de validación explícito para 1.0-SNAPSHOT. |
| Roles y módulos con definiciones repetidas o desfasadas | Autorización como referencia central; catálogo y asignaciones iniciales contrastados con semillas. |
| Permisos UPDATE de transacciones, aprobación o CONFIGURACION presentados como disponibles | Se documentan solo las 45 combinaciones implementadas en PermissionPolicy. |
| La limpieza de permisos parecía una migración nueva | Se aclara que la corrección está en semillas originales y no vuelve a aplicarse en bases ya migradas. |
| Status de productos/personas/usuarios descrito como 0/1 | ENUM active/inactive/deleted; roles.activo sí es un indicador separado. |
| Cabecera con product_id/quantity y tipos IN/OUT/LOAN | Cabecera multi-producto con tipos in/out/loan y detalles en transaction_items. |
| Columnas inexistentes initial_stock/updated_at y tamaños de campos distintos | Diccionario regenerado desde el DDL registrado. |
| SYSTEM descrito como status=0 | Es una cuenta técnica sin contraseña/rol interactivo; el estado del esquema es textual y por defecto active. |
| Restauración de respaldos anunciada desde la interfaz | El módulo implementa crear, consultar y eliminar; la recuperación se hace fuera de él. |
| Respaldos automáticos siempre activos y garantizados al cerrar | Deshabilitados por defecto; son intentos al iniciar/cerrar normalmente, sujetos a herramientas y errores. |
| Todo dato local colocado bajo .sicam | Se documenta la ruta antigua del control automático y el tratamiento especial del directorio de backups. |
| Importar CSV/Excel figuraba como propuesta futura | Documentados modos, vista previa, validaciones y guardado transaccional implementados. |
| Vencimiento dependiente únicamente del evento MySQL | Se separa cálculo visual por fecha de actualización persistida overdue. |
| Los eventos funcionaban sin condiciones y no usaban ningún ID fijo | Se documentan permisos/programador y la inicialización de system_user_id a 1. |
| Usuarios del sistema incluía rol y ROOT según el exportador | Sin rol; exclusión de SYSTEM y cuentas ROOT siempre. Agrupados por rol: tablas PDF separadas. |
| Productos con mayor movimiento seguía siendo Top 20 | Incluye todos los productos no eliminados con movimientos vigentes del período. |
| Actividad detallada por usuario descrita como resumen diario o auditoría general | Detalle de transacciones por fecha de registro o todas las fechas; se distingue de Auditoría. |
| Inventario filtrado por fechas interpretado como stock histórico | Las fechas seleccionan registros, pero las existencias son actuales. |
| Auditoría exportada sin aclarar sus límites anteriores | Se documentan exportación sin tope de 1.000, detalles sin recorte, username y escape CSV corregido. |
| AES-CBC con salt/IV aleatorios y configuración JSON | Implementación real: Properties, clave derivada con material fijo y Cipher AES; no se promete aleatoriedad que no existe. |
| Session.clear(), isLoggedIn(), relaciones y métodos ilustrativos inexistentes | Arquitectura describe las clases y responsabilidades reales, sin copiar APIs inventadas. |
| Recomendaciones de borrar tablas o desactivar firewall como solución general | Diagnóstico no destructivo y actualización con respaldo y migración específica. |
| Enlaces a PLAN_MEJORAS_BUGS y migraciones que no existen | Índices corregidos y lista de migraciones extraída de getMigrationFiles(). |

## Diferencias del código que quedan registradas, sin cambiarlas

1. **Control automático corregido después de esta revisión:** AutoBackupManager ya usa AppConfig, por defecto `~/.sicam/.cache/`, con copia inicial de la configuración antigua si falta la nueva. Sigue pendiente que BackupService respete toda la ruta personalizada de app.secure.dir, en lugar de reconstruirla desde su nombre final bajo user.home.
2. MySQLConfigManager no implementa salt/IV aleatorios por operación y desactiva SSL en la URL actual. Son límites reales de la implementación, no problemas resueltos al corregir el texto.
3. Los eventos inicializan el ID de SYSTEM a 1; debe existir la cuenta correcta para no depender de esa suposición. Los horarios del cliente y MySQL pueden diferir.
4. La migración multi-producto elimina tablas y las semillas modificadas no actualizan instalaciones anteriores. Falta un procedimiento de actualización conservando datos para esas bases.
5. El historial transaccional exporta notas generales, pero no transaction_items.notes por separado. El esquema sí conserva esas notas.
6. El autor de auditoría se muestra con el nombre y username actuales de users, no con una identidad histórica inmutable. Los respaldos automáticos no generan una entrada audit adicional en BackupService.
7. Escapar CSV no es neutralizar fórmulas de hojas de cálculo; gzip no es cifrar; FileChooser no restringe a una carpeta segura.
8. El PDF de KPI calcula porcentajes complementarios; el CSV conserva los conteos base. Se documenta la diferencia de presentación en vez de asumir salidas visualmente idénticas.

## Comprobaciones documentales

- Conteo del catálogo y asignaciones iniciales mediante ejecución de las sentencias de semillas compatibles en una base SQLite temporal en memoria: 45 permisos; ROOT 45, ADMIN 44, AUDITOR 13, SUPERVISOR 27, OPERADOR 13 y CONSULTOR 9. No se escribió en MySQL.
- Diccionario de columnas y lista automática contrastados con los SQL y MigrationRunner del repositorio.
- Revisión de enlaces locales, referencias desfasadas, formato Git y preservación del PDF.
- La última suite funcional anterior aprobó 137 pruebas. No se repite una suite de aplicación por cambios exclusivos de Markdown.

## Ajustes posteriores solicitados

Se cambió la implementación de AutoBackupManager para guardar el control en la carpeta de SICAM, conservar las opciones/fechas de la ubicación anterior y priorizar una configuración nueva existente. Se actualizaron las guías de rutas y arquitectura. Estos cambios posteriores sí incluyen código y pruebas, a diferencia de la revisión exclusivamente documental descrita arriba.

Seguridad recupera una matriz visual con los 45 permisos, los 12 módulos y las asignaciones iniciales de los seis roles, junto con una tabla separada de funciones reservadas por identidad. La matriz se contrastó con las semillas, sin consultar ni modificar datos MySQL.
