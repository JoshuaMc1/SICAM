# Reportes PDF/CSV y Auditoría

Revisado el 21 de octubre de 2026. ReportRepository construye los datos que comparten PDF y CSV; el formato PDF puede agrupar o colorear celdas sin alterar sus valores.

## Acceso y filtros

La pantalla Reportes exige REPORTES.READ; generar exige GENERATE y lectura de los módulos utilizados. La exportación añade EXPORT. Consultar usuarios o personas requiere sus permisos de lectura. VIEW_DELETED permite incluir registros eliminados o estados no activos en los históricos autorizados; no concede restauración.

El filtro global de estado se aplica a movimientos e históricos. Los indicadores de consumo, mayor movimiento y préstamos vigentes aplican sus propios criterios de vigencia; no deben sumar operaciones invalidadas porque se cambie el filtro del histórico.

La exportación directa CSV/Excel desde Inventario tiene restricciones diferentes: ADMIN o ROOT. Esto no impide que otro rol autorizado exporte un reporte de inventario desde Reportes.

## Catálogo y criterios

| Reporte | Datos y criterio |
| --- | --- |
| Usuarios del sistema | ID, nombre, username, estado y fecha de registro. Sin columna de rol. Siempre excluye SYSTEM y cuentas con rol ROOT, incluso si exporta ROOT. |
| Usuarios agrupados por rol | Excluye SYSTEM y cuentas ROOT. PDF: una tabla por rol, sin repetir columna de rol dentro de la tabla. CSV: conserva el rol en cada fila. |
| Inventario completo | ID, código, producto, descripción, unidad, stock actual/mínimo, estado, fecha de registro y nivel de stock. Son existencias actuales; puede incluir eliminados solo con el filtro y permiso correspondientes. |
| Bajo el mínimo | Stock positivo menor que el mínimo; no incluye sin stock ni cerca del mínimo. |
| Sin stock | Stock menor o igual a cero. |
| Productos por fecha de registro | Filtra products.created_at; el stock mostrado sigue siendo el actual, no el stock que tenía en esa fecha. |
| Movimientos de los últimos 30 días | Desde hoy menos 29 días hasta hoy, ambos incluidos, según transactions.date. |
| Movimientos por fecha de operación | Filtra transactions.date según el período elegido. |
| Historial de transacciones | Una fila por transacción con productos desglosados, tipo, estado y datos de invalidación; estados según autorización y filtro. |
| Transacciones por tipo | Ingresos, egresos o préstamos. Título en español, sin mostrar los códigos internos in/out/loan como nombre del tipo. |
| Transacciones por tipo y fecha | Mismo detalle, filtrando además la fecha de operación. |
| Transacciones por usuario | Transacciones registradas por el usuario seleccionado, mediante transactions.user_id. |
| Transacciones por persona | Transacciones asociadas a transactions.person_id. No son las acciones de una cuenta del sistema. |
| Productos con mayor movimiento | Todos los productos no eliminados con movimientos vigentes dentro del período, ordenados por número de transacciones distintas; ya no se limita a 20. Cantidades ingresadas, egresadas y prestadas por separado. |
| Personas con préstamos activos | Préstamos activos pendientes/vencidos, una fila por préstamo con ID/código, DNI, responsable, teléfono, productos/unidades/cantidades, fechas, estado y días de atraso. Vencidos primero. |
| Consumo mensual de productos | Egresos y artículos vigentes de los últimos 12 meses calendario, incluyendo el actual hasta hoy. Cantidad y número de egresos distintos por producto, unidad y mes; no suma préstamos como consumo. |
| Productos sin movimientos | Productos no eliminados sin movimiento vigente durante al menos 90 días. Último movimiento según fecha de operación; si nunca hubo uno, usa la fecha de creación. Ordena por días sin movimiento. |
| Actividad detallada por usuario | Transacciones creadas por el usuario, filtradas por fecha de registro transactions.created_at o por todas las fechas. Incluye productos, estados, fechas, persona, notas generales e invalidación. No es un resumen diario ni la bitácora general de Auditoría. |
| Dashboard mensual con KPI | Actividad del mes por fecha de operación y estado; existencias actuales separadas, cantidades por unidad, participación y registros actuales. PDF con tarjetas/gráfico y páginas complementarias de indicadores. |

Los conteos de «movimientos» y «egresos» usan transacciones distintas, no cantidad de filas de artículos. Las cantidades se muestran por unidad; no corresponde sumar litros, unidades y sacos como una sola cantidad.

## Estados y préstamos

La vigencia operativa requiere cabecera `active` y artículo `active`. Los históricos conservan estados y marcas de artículos cancelados.

Los badges del estado de la transacción en PDF utilizan verde para Activo, rojo para Invalidado y gris para Cancelado/Inactivo. El CSV conserva la etiqueta como texto, sin información de color.

El nivel de stock usa las reglas de [FEATURES.md](FEATURES.md) y los colores de StockLevel. El PDF de inventario presenta badges; el CSV conserva la etiqueta. Un nivel de stock no es el estado activo/inactivo del producto.

La fecha prevista de devolución se compara con el día actual completo. «Vence hoy» no equivale a «Vencido». Los préstamos cerrados o invalidados no aparecen en pendientes activos. Los días de atraso no se muestran como cantidades negativas.

## PDF y CSV

- Inventario, productos por fecha, movimientos, históricos y actividad transaccional utilizan orientación horizontal para dar espacio al detalle. Auditoría utiliza A4 horizontal.
- Los encabezados de tablas se repiten al continuar en otra página. Los detalles largos no se convierten en una transacción nueva ni deben recortarse para hacerlos caber.
- Los reportes de usuarios mantienen tablas más compactas; no se fuerzan todos los documentos a horizontal.
- Reportes y Auditoría incorporan DejaVu Sans regular/negrita, con licencia incluida, para no depender del escaneo de fuentes de Windows.
- CSV se escribe en UTF-8, con escape de comillas, comas y saltos de línea. Un producto o detalle con salto de línea puede ocupar varias líneas físicas dentro de un único registro CSV.
- Los PDF incluyen metadatos de generación. El CSV conserva columnas tabulares, sin añadir tarjetas, gráficos o filas decorativas de totales al conjunto exportado.

Los porcentajes complementarios del KPI se calculan para el PDF; el CSV conserva los conteos base de indicadores. Si no hubo operaciones se muestra «Sin operaciones» en lugar de dividir entre cero.

## Auditoría

El módulo Auditoría exporta todos sus eventos o un rango de fechas de registro, independiente de la página visible o búsqueda de la tabla. «Todo» no tiene el límite anterior de 1.000 filas.

| Información | PDF | CSV |
| --- | --- | --- |
| ID del evento | Columna ID | id |
| ID, nombre y username del autor | Agrupados en Usuario | usuario_id, usuario_name y usuario |
| Acción | Acción original | accion |
| Tabla | Tabla original | tabla_afectada |
| ID afectado | Reg. ID | registro_afectado_id |
| Fecha y hora | Completas | fecha |
| Detalles | Completos, con continuación si hace falta | detalles, conservando comillas y saltos |

El PDF indica quién lo generó, cuándo, el ámbito y cuántos eventos incluye. El nombre/username del autor se consulta desde users al exportar; no es una copia histórica guardada en audit. Si no se encuentra el usuario, el evento se conserva mediante LEFT JOIN y su ID sigue disponible.

Un usuario con cero transacciones puede tener eventos de login, edición o gestión. Consulte esos eventos en Auditoría, no en «Actividad detallada por usuario» de Reportes.

## Límites que conviene interpretar correctamente

- Las existencias actuales pueden cambiar por una edición o importación, además de ingresos/egresos/préstamos. Los ajustes quedan en Auditoría y no se inventan como transacciones para cuadrar un histórico.
- El reporte de «sin movimientos» no demuestra que un producto sea obsoleto; solo expresa el período sin operaciones vigentes.
- Los históricos transaccionales exportan notas generales de la cabecera. El campo transaction_items.notes existe, pero no se exporta por separado en ese desglose actual.
- Los gráficos y colores pertenecen al PDF. Que no aparezcan en CSV no significa que falten los valores tabulares.
