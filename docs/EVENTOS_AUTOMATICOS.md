# Eventos MySQL para préstamos vencidos

Revisado el 21 de octubre de 2026. Fuente: [MigrationRunner.java](../src/main/java/com/joshuamc1/sicam/config/MigrationRunner.java) y [LoanDueDate.java](../src/main/java/com/joshuamc1/sicam/utils/LoanDueDate.java).

## Dos mecanismos diferentes

La interfaz y los reportes pueden detectar un préstamo vencido comparando `expected_return_date` con el día actual, aunque la base conserve todavía `loan_status='pending'`. También consideran vencido un estado ya guardado como `overdue`. Una fecha que vence hoy no cuenta como atraso.

Los eventos MySQL persisten `loan_status='overdue'` para los préstamos que cumplen:

```sql
type = 'loan'
AND loan_status = 'pending'
AND status = 'active'
AND expected_return_date < CURDATE()
```

No modifican stock, no invalidan transacciones, no liquidan préstamos ni crean devoluciones. Al afectar registros insertan una entrada agregada de auditoría `scheduled_update`; no insertan un evento por cada producto.

## Configuración al iniciar

Después de las migraciones, MigrationRunner intenta activar `event_scheduler` y crear estos eventos en la conexión MySQL configurada:

| Evento | Horario diario del servidor |
| --- | --- |
| update_overdue_loans_morning | 08:35 |
| update_overdue_loans_backup | 09:00 |
| update_overdue_loans_midnight | 00:05, con inicio calculado para el día siguiente |

Cada evento existente se intenta eliminar y recrear. La sentencia completa `CREATE EVENT ... DO BEGIN ... END` se envía por JDBC; `DELIMITER` es una instrucción de algunos clientes SQL y no forma parte de esa llamada JDBC.

La configuración necesita permisos MySQL y un programador habilitado; no es correcto afirmar que funciona sin ninguna condición administrativa. La aplicación registra advertencias si no puede configurarlo y puede continuar sin esos eventos.

## SYSTEM y auditoría

Los cuerpos de los eventos buscan la cuenta `username='SYSTEM'` y usan su ID para la auditoría. Sin embargo, la variable SQL se inicializa con **1** antes del SELECT. Por eso la documentación anterior que afirmaba «no hay ningún ID fijo» era inexacta. La existencia correcta de SYSTEM debe comprobarse; no debe asumirse que el ID 1 siempre le corresponde.

SYSTEM es una cuenta técnica creada por las migraciones iniciales, sin rol ni contraseña interactiva. Su estado no debe describirse como el número 0; el esquema de usuarios utiliza estados de texto.

## Verificación de solo lectura

```sql
SHOW VARIABLES LIKE 'event_scheduler';

SELECT EVENT_NAME, STATUS, INTERVAL_VALUE, INTERVAL_FIELD, STARTS, TIME_ZONE
FROM information_schema.EVENTS
WHERE EVENT_SCHEMA = DATABASE();

SELECT id, loan_code, loan_status, expected_return_date
FROM transactions
WHERE type = 'loan' AND status = 'active'
  AND loan_status IN ('pending', 'overdue');

SELECT id, user_id, action, details, created_at
FROM audit
WHERE action = 'scheduled_update'
ORDER BY id DESC;
```

Que la interfaz muestre «Vencido» no acredita por sí solo que los eventos estén funcionando: compruebe también el valor persistido y los eventos del servidor.

## Horarios y diagnóstico

CURDATE() y los horarios de los eventos dependen de MySQL; LocalDate.now() depende del equipo donde corre SICAM. La conexión declara serverTimezone=UTC, pero eso no garantiza por sí mismo que la zona de eventos, la del servidor y la del cliente sean idénticas. Diferencias cerca de medianoche requieren revisar las zonas de ambas máquinas.

Si los eventos no aparecen o no cambian estados, revise el programador, el esquema seleccionado, permisos EVENT, errores de definición y logs. Activar el programador global corresponde al administrador del servidor y puede requerir privilegios adicionales; no se necesita conceder esos privilegios globales a todo usuario de la aplicación.

Existe [MANUAL_create_overdue_events.sql](../src/main/resources/migrations/MANUAL_create_overdue_events.sql) para configuración manual por un administrador. El archivo `20251201000001_create_overdue_loans_events.sql` está en resources, pero no está en la lista automática de getMigrationFiles(); los eventos automáticos descritos aquí los crea el código Java.
