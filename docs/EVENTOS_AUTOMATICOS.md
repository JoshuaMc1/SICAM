# ✅ Eventos MySQL Automáticos - Implementación Mejorada

## 🎯 ¿Qué se implementó?

Se agregó funcionalidad en `MigrationRunner.java` para **crear automáticamente los eventos MySQL** después de ejecutar las migraciones, **sin usar DELIMITER**.

## 🔧 Cómo Funciona

### 1. Flujo de Ejecución

```
┌─────────────────────────────────────┐
│  App.java inicia                    │
└────────────┬────────────────────────┘
             │
             ▼
┌─────────────────────────────────────┐
│  DatabaseConfig.openConnection()    │
└────────────┬────────────────────────┘
             │
             ▼
┌─────────────────────────────────────┐
│  MigrationRunner.runMigrations()    │
│  - Ejecuta migraciones .sql         │
│  - Registra en schema_migrations    │
└────────────┬────────────────────────┘
             │
             ▼
┌─────────────────────────────────────┐
│  createOverdueLoansEvents()         │
│  - Activa event scheduler           │
│  - Crea 3 eventos automáticamente   │
└─────────────────────────────────────┘
```

### 2. Eventos Creados

Se crean **3 eventos** para redundancia:

1. **`update_overdue_loans_morning`** → 8:35 AM cada día
2. **`update_overdue_loans_backup`** → 9:00 AM cada día
3. **`update_overdue_loans_midnight`** → 00:05 AM cada día

### 3. Sin DELIMITER

Los eventos se crean en **una sola línea SQL** sin usar `DELIMITER $$`:

```java
String sql = "CREATE EVENT update_overdue_loans_morning " +
        "ON SCHEDULE EVERY 1 DAY " +
        "STARTS (CURDATE() + INTERVAL '08:35:00' HOUR_SECOND) " +
        "DO BEGIN " +
        "  DECLARE affected_count INT DEFAULT 0; " +
        "  DECLARE system_user_id INT DEFAULT 1; " +
        "  SELECT id INTO system_user_id FROM users WHERE username = 'SYSTEM' LIMIT 1; " +
        "  UPDATE transactions SET loan_status = 'overdue' " +
        "  WHERE type = 'loan' AND loan_status = 'pending' " +
        "  AND status = 'active' AND expected_return_date < CURDATE(); " +
        "  SET affected_count = ROW_COUNT(); " +
        "  IF affected_count > 0 THEN " +
        "    INSERT INTO audit (user_id, action, table_name, ...) " +
        "    VALUES (system_user_id, 'scheduled_update', ...); " +
        "  END IF; " +
        "END";

Base.exec(sql);
```

## 📋 Funciones Agregadas

### `createOverdueLoansEvents()`
Función principal que orquesta la creación de eventos.

### `enableEventScheduler()`
Activa el `event_scheduler` de MySQL con `SET GLOBAL event_scheduler = ON`.

### `createOverdueEventMorning()`
Crea el evento de las 8:35 AM.

### `createOverdueEventBackup()`
Crea el evento de las 9:00 AM (respaldo).

### `createOverdueEventMidnight()`
Crea el evento de las 00:05 AM (medianoche).

### `dropEventIfExists(String eventName)`
Elimina un evento si existe (para recrearlo).

## ✅ Ventajas de esta Solución

### 1. **Totalmente Automático**
- No requiere ejecución manual
- Se ejecuta al iniciar la aplicación
- Idempotente (puede ejecutarse múltiples veces)

### 2. **Sin DELIMITER**
- Compatible con ActiveJDBC
- No requiere archivos .sql separados
- Todo en código Java

### 3. **Robusto**
- Maneja errores sin interrumpir la aplicación
- Logs informativos
- Si falla, se puede ejecutar manualmente

### 4. **Usuario SYSTEM**
- Obtiene dinámicamente el ID del usuario SYSTEM
- Auditoría correcta
- No hardcodea IDs

### 5. **Redundancia**
- 3 horarios diferentes
- Mayor confiabilidad
- Si uno falla, los otros siguen funcionando

## 🚀 ¿Qué hacer ahora?

### ✅ Nada! Es Automático

La próxima vez que inicies la aplicación:

1. Se ejecutarán las migraciones pendientes
2. Se crearán automáticamente los eventos MySQL
3. Los préstamos vencidos se actualizarán automáticamente cada día

### Verificar Eventos Creados

```sql
-- Ver eventos
SHOW EVENTS;

-- Ver estado del scheduler
SHOW VARIABLES LIKE 'event_scheduler';

-- Ver últimos logs de eventos
SELECT a.*, u.username 
FROM audit a 
LEFT JOIN users u ON u.id = a.user_id 
WHERE u.username = 'SYSTEM' 
ORDER BY a.created_at DESC 
LIMIT 5;
```

## 📊 Logs Esperados

Al iniciar la aplicación verás:

```
INFO  MigrationRunner - Iniciando migraciones de base de datos...
INFO  MigrationRunner - Migraciones completadas (sin cambios, idempotente)
INFO  MigrationRunner - Configurando eventos de préstamos vencidos...
DEBUG MigrationRunner - Event scheduler activado
DEBUG MigrationRunner - Evento creado: update_overdue_loans_morning
DEBUG MigrationRunner - Evento creado: update_overdue_loans_backup
DEBUG MigrationRunner - Evento creado: update_overdue_loans_midnight
INFO  MigrationRunner - Eventos de préstamos vencidos configurados correctamente
```

## ⚠️ Manejo de Errores

Si los eventos no se pueden crear automáticamente (por falta de permisos, etc.):

```
WARN  MigrationRunner - No se pudieron crear eventos automáticos: ...
WARN  MigrationRunner - Los eventos pueden crearse manualmente con MANUAL_create_overdue_events.sql
```

La aplicación **NO se interrumpe**, simplemente advierte y permite ejecución manual.

## 🔍 Troubleshooting

### Problema: "event_scheduler is OFF"

**Solución automática:** El código intenta activarlo con:
```sql
SET GLOBAL event_scheduler = ON
```

**Solución manual:**
```sql
-- En MySQL
SET GLOBAL event_scheduler = ON;
```

### Problema: "Access denied; you need the EVENT privilege"

El usuario MySQL necesita permisos para crear eventos:

```sql
GRANT EVENT ON *.* TO 'tu_usuario'@'localhost';
FLUSH PRIVILEGES;
```

### Problema: Los eventos no aparecen

Verificar que se crearon:
```sql
SHOW EVENTS;
```

Si no aparecen, ejecutar manualmente:
```bash
# Ubicación del archivo
src/main/resources/migrations/MANUAL_create_overdue_events.sql
```

## 📁 Archivos Relacionados

```
src/main/java/com/joshuamc1/inventariomunicipal/config/
└── MigrationRunner.java  ✅ MODIFICADO

src/main/resources/migrations/
├── MANUAL_create_overdue_events.sql  📋 Backup manual (si es necesario)
├── TEST_overdue_manual.sql           🧪 Pruebas
├── QUICK_COMMANDS.sql                ⚡ Comandos útiles
└── README_EVENTOS.md                 📖 Documentación original
```

## 🎯 Comparación: Antes vs Ahora

### ❌ Antes
- Archivo con DELIMITER no compatible
- Ejecución manual requerida
- Propenso a errores
- Documentación compleja

### ✅ Ahora
- **Totalmente automático**
- Sin archivos .sql externos
- Robusto y con manejo de errores
- Simple de usar

## 📝 Notas Técnicas

### ¿Por qué funciona sin DELIMITER?

MySQL acepta procedimientos en una sola línea SQL concatenada:

```sql
-- ✅ Funciona (una línea)
CREATE EVENT mi_evento DO BEGIN SELECT 1; SELECT 2; END

-- ❌ No funciona con MigrationRunner (requiere DELIMITER)
CREATE EVENT mi_evento
DO
BEGIN
  SELECT 1;
  SELECT 2;
END
```

### Idempotencia

Los eventos se eliminan y recrean cada vez:
```java
dropEventIfExists(eventName);
// ...
Base.exec(sql);
```

Esto asegura que siempre estén actualizados.

## ✅ Checklist de Verificación

Después de iniciar la aplicación:

- [ ] Verificar logs: `Eventos de préstamos vencidos configurados correctamente`
- [ ] Verificar en MySQL: `SHOW EVENTS;` debe mostrar 3 eventos
- [ ] Verificar scheduler: `SHOW VARIABLES LIKE 'event_scheduler';` debe ser `ON`
- [ ] (Opcional) Crear préstamo vencido de prueba y esperar actualización

---

## 🎉 Conclusión

Los eventos MySQL ahora se crean **automáticamente** al iniciar la aplicación, sin necesidad de intervención manual. El sistema es robusto, maneja errores y proporciona fallbacks manuales si es necesario.

**¡Ya no necesitas ejecutar ningún archivo SQL manualmente!** 🚀

