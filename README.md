# SICAM — Sistema de Control de Activos Municipales

Aplicación de escritorio JavaFX para gestionar productos, personas, ingresos, egresos y préstamos con varios productos por transacción. Utiliza MySQL, autorización por roles y permisos, auditoría y exportaciones PDF/CSV.

**Versión:** 1.0-SNAPSHOT. **Documentación revisada:** 30 de octubre de 2026.

## Funcionalidades

- Inventario con códigos de producto, códigos de barras/QR, stock actual, mínimo e indicadores de existencias.
- Ingresos, egresos y préstamos: creación, consulta, invalidación y restauración. No existe edición de transacciones ni aprobación de préstamos.
- Devolución completa de préstamos mediante un ingreso relacionado; seguimiento de pendientes, vencidos y cerrados.
- Importación CSV/Excel con vista previa, modos de creación/actualización, validación integral y detección de cambios concurrentes.
- Usuarios, personas y roles con permisos vigentes comprobados en servicios y controles de interfaz.
- Una sesión por cuenta en la misma base de datos, con renovación automática sin cierre por inactividad y recuperación tras cierres inesperados.
- Tablas paginadas con ordenación sobre el conjunto completo de resultados.
- Reportes operativos, históricos y analíticos en PDF y CSV; PDF horizontal para documentos con muchas columnas.
- Auditoría de acciones, con exportación completa o por fechas y detalles sin recorte.
- Creación, consulta y eliminación de respaldos; automatización configurable al iniciar y al cerrar normalmente la aplicación.

## Requisitos y ejecución

El entorno verificado utiliza **JDK 25**, **Maven 3.9.11** y **MySQL 8.0+**. JavaFX está fijado en **25.0.1**. El `source/target=14` del compilador no significa que Java 14 o 21 puedan ejecutar las dependencias actuales.

Para los respaldos se necesitan `mysqldump` y `gzip` accesibles desde el proceso de la aplicación.

```bash
git clone https://github.com/JoshuaMc1/SICAM.git
cd SICAM
mvn clean test
mvn javafx:run
```

Configure una base MySQL dedicada y siga el asistente inicial para guardar la conexión y crear la cuenta ROOT. No se proporciona una cuenta ROOT con contraseña predeterminada. Consulte [Inicio y actualización](docs/GETTING_STARTED.md) antes de usar una base que ya tenga datos: una migración de desarrollo elimina y recrea las tablas transaccionales.

La aplicación usa MySQL; SQLite se utiliza en pruebas automatizadas. Activar `app.env=production` no cambia automáticamente el motor a SQLite.

## Roles y estados

La instalación inicial contiene seis roles: ROOT, ADMIN, AUDITOR, SUPERVISOR, OPERADOR y CONSULTOR. El catálogo tiene **45 permisos en 12 módulos**. Las asignaciones iniciales y las restricciones por identidad de rol se detallan en [Autorización](docs/AUTORIZACION.md).

Productos, personas, usuarios y roles distinguen `active`, `inactive` y `deleted`. Las transacciones distinguen `active`, `invalidated` y `cancelled`; su estado es independiente de `loan_status` en los préstamos. Ver eliminados o invalidados no equivale a tener permiso para restaurarlos.

## Documentación

| Documento | Contenido |
| --- | --- |
| [Índice](docs/README.md) | Guías disponibles y fuentes de referencia |
| [Inicio y actualización](docs/GETTING_STARTED.md) | Entorno, conexión, ejecución, respaldos y diagnóstico |
| [Funcionalidades](docs/FEATURES.md) | Flujos implementados y limitaciones |
| [Arquitectura](docs/ARCHITECTURE.md) | Capas, conexiones, autorización y exportadores |
| [Base de datos](docs/DATABASE.md) | Esquema, estados, relaciones y migraciones reales |
| [Autorización](docs/AUTORIZACION.md) | Roles, permisos y funciones reservadas |
| [Seguridad](docs/SECURITY.md) | Controles implementados y límites actuales |
| [Sesión única](docs/SESIONES.md) | Funcionamiento, recuperación, actualización y pruebas |
| [Reportes y auditoría](docs/REPORTES.md) | Criterios de cálculo, filtros y formatos |
| [Eventos automáticos](docs/EVENTOS_AUTOMATICOS.md) | Préstamos vencidos y programador de MySQL |
| [Revisión documental](docs/REVISION_DOCUMENTACION.md) | Incoherencias encontradas y correcciones |

`docs/documentation.pdf` se mantiene manualmente y no se actualizó en esta revisión. Las evidencias y bitácoras de QA locales no forman parte del repositorio.

## Validación y mantenimiento

- `mvn test`: validación del 30 de octubre de 2026 con JDK 25; **154 pruebas detectadas, 152 aprobadas y 2 omitidas**, sin fallos ni errores. Las dos omitidas pertenecen a `SessionMySqlTest`, que requiere activación y configuración explícitas.
- Pruebas con MySQL, varios usuarios, concurrencia y revisión visual complementan las pruebas con SQLite; ninguna cifra de pruebas garantiza por sí sola todos los entornos de instalación.
- Herramientas de diagnóstico de solo lectura: [tools/qa](tools/qa). No confundir capturas de diagnóstico con respaldos restaurables.
- [GitHub Issues](https://github.com/JoshuaMc1/SICAM/issues) para incidencias. Logs de aplicación: `logs/app.log`.

## Dependencias y licencia

Versiones declaradas en [pom.xml](pom.xml): JavaFX 25.0.1, ActiveJDBC 3.5-j11, MySQL Connector/J 8.3.0, jBCrypt 0.4, PDFBox 2.0.29, Apache POI 5.2.5, ZXing 3.5.3 y Logback 1.4.11.

El código del proyecto se distribuye bajo [MIT](LICENSE.txt). Las dependencias y las [fuentes DejaVu incluidas](src/main/resources/fonts/README.md) mantienen sus propias licencias.

Desarrollado por Joshua MC. Repositorio: https://github.com/JoshuaMc1/SICAM.
