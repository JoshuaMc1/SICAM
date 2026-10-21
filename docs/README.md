# Documentación de SICAM

Referencia revisada contra el código y las migraciones del repositorio el **21 de octubre de 2026**, para la versión **1.0-SNAPSHOT**.

| Guía | Propósito |
| --- | --- |
| [GETTING_STARTED.md](GETTING_STARTED.md) | Instalación, actualización y diagnóstico |
| [FEATURES.md](FEATURES.md) | Funciones disponibles y reglas de operación |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Organización del código y responsabilidades |
| [DATABASE.md](DATABASE.md) | Diccionario del esquema y migraciones |
| [AUTORIZACION.md](AUTORIZACION.md) | Catálogo de permisos, roles y restricciones |
| [SECURITY.md](SECURITY.md) | Autenticación, protección de cuentas y límites de seguridad |
| [REPORTES.md](REPORTES.md) | Qué incluye cada reporte PDF/CSV |
| [EVENTOS_AUTOMATICOS.md](EVENTOS_AUTOMATICOS.md) | Actualización programada de préstamos vencidos |
| [REVISION_DOCUMENTACION.md](REVISION_DOCUMENTACION.md) | Registro de incoherencias corregidas y observaciones pendientes |

## Cómo consultar las guías

Para instalar, empiece por Inicio y Base de datos. Para operar, consulte Funcionalidades y Reportes. Para administrar accesos, use Autorización y Seguridad. Para desarrollar, consulte Arquitectura y las pruebas en [src/test](../src/test).

El código, [pom.xml](../pom.xml), [PermissionPolicy](../src/main/java/com/joshuamc1/sicam/core/PermissionPolicy.java) y las [migraciones](../src/main/resources/migrations) son las fuentes de referencia. Las cifras de permisos corresponden a una instalación nueva, no a roles modificados durante el uso.

## Documentos y evidencias locales

- `documentation.pdf` se edita manualmente en Word; queda fuera de esta actualización.
- `docs/qa/PLAN_PRUEBAS.md` y `docs/qa/SEGUIMIENTO.md`, cuando existan localmente, son planes y registros históricos de pruebas. Sus resultados describen los momentos de ejecución; no se reescriben como si fueran el comportamiento actual.
- `docs/qa/`, `qa-evidence/`, `evidence/` y `.dev-notes/` están excluidos de Git. No publique capturas con datos reales o configuraciones privadas.
- Las herramientas versionadas en [tools/qa](../tools/qa) permiten capturar y comparar datos sin modificarlos.

La revisión documental no constituye una certificación de producción ni sustituye una prueba de restauración de respaldos en un entorno separado.
