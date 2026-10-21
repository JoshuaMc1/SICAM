# Funcionalidades de SICAM

Actualizado el 21 de octubre de 2026. Se describen funciones implementadas; el acceso efectivo depende del usuario, su rol y sus permisos vigentes.

## Inventario

Crear, consultar, editar, eliminar lógicamente y restaurar productos. La eliminación permanente y la importación están reservadas a ROOT. La exportación directa CSV/Excel del módulo es exclusiva de ADMIN y ROOT; esto es distinto de exportar reportes de inventario desde Reportes.

Cada producto tiene código único opcional, nombre, descripción, unidad, stock actual y mínimo. Puede generar códigos y descargar representaciones Code128/QR. Un lector funciona como entrada de teclado; el campo apropiado debe tener foco.

Los indicadores y filtros comparten [StockLevel](../src/main/java/com/joshuamc1/sicam/core/enums/StockLevel.java):

| Nivel | Regla | Color |
| --- | --- | --- |
| Sin stock | Stock actual menor o igual a 0 | Gris |
| Bajo el mínimo | Stock positivo menor que el mínimo | Rojo |
| Cerca del mínimo | Mínimo positivo y stock entre el mínimo y el 120 % del mínimo, ambos incluidos | Amarillo |
| Stock suficiente | Los demás casos | Verde |

Los niveles son excluyentes: «bajo el mínimo» no incluye los productos sin stock. Los cambios manuales comprueban que el stock no haya cambiado desde que se abrió la edición y se registran en Auditoría.

## Importación CSV y Excel

Disponible para ROOT, con vista previa y modos «solo crear», «solo actualizar» y «crear o actualizar». La vista previa informa creados, actualizados, omitidos y errores. Un error impide guardar todo el archivo; el guardado de productos y auditoría es transaccional.

Columnas requeridas: `name`, `unit`, `current_stock`, `minimum_stock`. Opcionales: `code`, `description`, `status`. Si se incluye `status`, solo admite productos activos. Los encabezados se comparan sin distinguir mayúsculas.

```csv
code,name,description,unit,current_stock,minimum_stock
MAT-001,Cemento,Bolsa de cemento,saco,10.00,3.00
MAT-002,Pala,,unidad,5.00,2.00
```

- CSV: UTF-8 con BOM opcional, coma o punto y coma, comillas y saltos de línea dentro de campos.
- Excel: archivos `.xls` y `.xlsx`; no se evalúan fórmulas. Use celdas de texto para códigos largos o con ceros iniciales.
- El código existente identifica una actualización; un código vacío identifica un producto nuevo con código generado.
- Los códigos repetidos en el archivo se rechazan. No se reactivan productos inactivos o eliminados por importación.
- Una descripción vacía conserva la existente al actualizar.
- Stock y mínimo: de 0 a 99999999.99, como máximo dos decimales, sin separadores de miles.
- Si los datos cambiaron tras la vista previa, se cancela sin guardar y se exige revisar otra vez. Un plan no se puede ejecutar dos veces.
- La importación ajusta existencias y registra auditoría; no crea ingresos o egresos artificiales.

## Ingresos, egresos y préstamos

Cada transacción contiene una cabecera y uno o más productos. Guarda fecha de operación, responsable, persona asociada y notas. La consulta de detalles permite ver el desglose sin duplicar la transacción en el listado.

No se editan transacciones ni se aprueban préstamos. Se crean, consultan, invalidan o restauran conforme a los permisos del módulo.

| Operación | Efecto en existencias |
| --- | --- |
| Crear ingreso | Suma las cantidades |
| Crear egreso o préstamo | Resta las cantidades disponibles |
| Invalidar | Revierte el efecto del registro |
| Restaurar | Reaplica el efecto, si las reglas actuales lo permiten |

La cabecera usa `active`, `invalidated` o `cancelled`; los artículos usan `active` o `cancelled`. La interfaz y los PDF transaccionales presentan los estados con badges. Invalidado no equivale a inactivo.

Se guardan conjuntamente stock, cabecera, artículos y auditoría. Se comprueban productos, cantidades positivas de hasta dos decimales, existencias y límites numéricos. La invalidación de un ingreso también puede fallar si ya se consumió el stock que tendría que retirarse. Repetir invalidaciones/restauraciones no debe modificar el stock otra vez.

## Préstamos y devoluciones

`loan_status` es independiente del estado de la transacción: `pending`, `overdue`, `returned`, `paid` o `replaced`. Se utilizan fecha prevista de devolución, código de préstamo y filtros/indicadores de vencimiento. Un préstamo que vence hoy todavía está en plazo.

Las devoluciones se registran mediante un ingreso relacionado:

- Mismo producto y comprado/pagado: deben coincidir todos los productos y cantidades prestadas.
- Reemplazado y otro: permiten productos distintos; exigen explicar en notas cómo se liquida el préstamo completo.
- No existe devolución parcial. Las cantidades de unidades diferentes no se suman para decidir si se liquidó el préstamo.
- No puede invalidarse un préstamo con devolución activa.
- Invalidar una devolución reabre el préstamo; restaurarla lo cierra si ninguna otra devolución activa lo ha cerrado.

En la tabla de préstamos se utiliza «Productos»; las notas se consultan en «Ver», evitando columnas redundantes.

## Usuarios, roles y personas

- Personas: DNI único, nombre, apellido, teléfono y código interno; eliminación lógica y restauración autorizada.
- Usuarios: creación, edición, activación/desactivación, eliminación lógica, restauración y restablecimiento de contraseña según permisos y protección de la cuenta.
- Desactivar una cuenta no es eliminarla. Una cuenta eliminada se consulta en «Ver eliminados».
- SYSTEM es una cuenta técnica, oculta en la gestión normal y sin inicio de sesión interactivo.
- ROOT no se muestra a usuarios que no son ROOT; no se puede asignar ese rol mediante la gestión ordinaria.
- Roles: edición de datos y asignación de permisos son operaciones distintas. Los nombres de los roles del sistema están protegidos.

La matriz completa y los permisos que permiten cada acción se documentan en [AUTORIZACION.md](AUTORIZACION.md).

## Consulta, exportación y auditoría

La búsqueda y ordenación de las tablas paginadas se aplican al conjunto de resultados antes de seleccionar la página. Un ID se ordena como número, no como texto. La presencia de botones y menús depende de los permisos; ocultar un botón no sustituye la comprobación del servicio.

Los [reportes](REPORTES.md) distinguen históricos con estado y cálculos de operaciones vigentes. La actividad detallada de un usuario muestra transacciones que registró; otras acciones de ese usuario se consultan en Auditoría.

Auditoría incluye evento, ID y nombre del autor, nombre de usuario, acción, tabla, ID afectado, detalles y fecha con hora. Sus exportaciones completas no se limitan a la página visible ni a 1.000 registros.

## Respaldos y límites actuales

El módulo permite crear, listar y eliminar respaldos `.sql.gz`. Los automáticos están deshabilitados inicialmente y se pueden habilitar; al iniciar se intenta uno por día y al cerrar normalmente se intenta otro. No existe una restauración de respaldos desde la interfaz: se debe realizar con herramientas de MySQL y un procedimiento comprobado.

El sistema no incorpora aprobación de préstamos, edición de transacciones, devoluciones parciales, API REST, app móvil ni firma digital. Esas funciones no deben presentarse como implementadas.
