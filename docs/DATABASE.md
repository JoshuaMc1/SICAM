# 🗄️ Base de Datos - Sistema de Inventario Municipal

**Última actualización:** 28 de Noviembre de 2025

## 📊 Estructura de la Base de Datos

### Motor de Base de Datos
- **MySQL 8.0+**
- **Charset:** utf8mb4
- **Collation:** utf8mb4_unicode_ci

---

## 📋 Tablas Principales

### 1. `roles`
Tabla de roles del sistema con permisos asociados.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | INT | ID único del rol |
| `nombre` | VARCHAR(50) | Nombre del rol (ROOT, ADMIN, etc.) |
| `descripcion` | TEXT | Descripción del rol |
| `activo` | TINYINT(1) | Estado del rol (1=activo, 0=inactivo) |
| `created_at` | TIMESTAMP | Fecha de creación |

**Relaciones:**
- Tiene muchos: `users` (a través de `rol_id`)
- Tiene muchos: `permisos` (a través de `rol_permisos`)

---

### 2. `permisos`
Tabla de permisos granulares del sistema.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | INT | ID único del permiso |
| `modulo` | VARCHAR(50) | Módulo (INVENTARIO, USUARIOS, etc.) |
| `accion` | VARCHAR(50) | Acción (CREATE, READ, UPDATE, DELETE) |
| `descripcion` | TEXT | Descripción del permiso |
| `created_at` | TIMESTAMP | Fecha de creación |

**Relaciones:**
- Pertenece a muchos: `roles` (a través de `rol_permisos`)

---

### 3. `rol_permisos`
Tabla pivote para la relación many-to-many entre roles y permisos.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | INT | ID único |
| `rol_id` | INT | FK a `roles.id` |
| `permiso_id` | INT | FK a `permisos.id` |
| `created_at` | TIMESTAMP | Fecha de asignación |

---

### 4. `users`
Tabla de usuarios del sistema con soporte para soft delete.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | INT | ID único del usuario |
| `name` | VARCHAR(100) | Nombre completo |
| `username` | VARCHAR(50) | Nombre de usuario (único) |
| `password` | VARCHAR(255) | Contraseña hasheada (BCrypt) |
| `rol_id` | INT | FK a `roles.id` (nullable para usuario SYSTEM) |
| `must_change_password` | TINYINT(1) | Requiere cambio de contraseña |
| `status` | TINYINT(1) | Estado: 1=activo, 0=eliminado |
| `deleted_at` | TIMESTAMP | Fecha de eliminación (soft delete) |
| `deleted_by` | INT | FK a `users.id` (quien eliminó) |
| `created_at` | TIMESTAMP | Fecha de creación |

**Índices:**
- UNIQUE: `username`
- INDEX: `rol_id`
- INDEX: `status`

**Usuario Especial:**
- Usuario `SYSTEM` (username='SYSTEM', rol_id=NULL, status=0)
- Usado para operaciones automáticas del sistema
- No puede iniciar sesión ni ser editado manualmente

**Relaciones:**
- Pertenece a: `roles`
- Tiene muchos: `transactions` (como usuario que registra)
- Tiene muchos: `audit` (como usuario que realiza acción)
- Auto-relación: `deleted_by` referencia a `users.id`

---

### 5. `people`
Tabla de personas con soporte para soft delete.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | INT | ID único de la persona |
| `dni` | VARCHAR(20) | DNI o identificación (único) |
| `first_name` | VARCHAR(50) | Nombre(s) |
| `last_name` | VARCHAR(50) | Apellido(s) |
| `phone` | VARCHAR(20) | Teléfono (formato: 0000-0000) |
| `internal_code` | VARCHAR(50) | Código interno (opcional) |
| `status` | TINYINT(1) | Estado: 1=activo, 0=eliminado |
| `deleted_at` | TIMESTAMP | Fecha de eliminación (soft delete) |
| `deleted_by` | INT | FK a `users.id` (quien eliminó) |
| `created_at` | TIMESTAMP | Fecha de creación |

**Índices:**
- UNIQUE: `dni`
- INDEX: `internal_code`
- INDEX: `status`

**Relaciones:**
- Tiene muchos: `transactions` (préstamos y transacciones)
- Pertenece a: `users` (a través de `deleted_by`)

---

### 6. `products`
Tabla de productos del inventario con códigos y soft delete.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | INT | ID único del producto |
| `code` | VARCHAR(50) | Código del producto (único, EAN-13) |
| `name` | VARCHAR(100) | Nombre del producto |
| `description` | TEXT | Descripción detallada |
| `unit` | VARCHAR(20) | Unidad de medida (pcs, kg, L, etc.) |
| `initial_stock` | DECIMAL(10,2) | Stock inicial |
| `current_stock` | DECIMAL(10,2) | Stock actual |
| `minimum_stock` | DECIMAL(10,2) | Stock mínimo (alerta) |
| `status` | TINYINT(1) | Estado: 1=activo, 0=eliminado |
| `deleted_at` | TIMESTAMP | Fecha de eliminación (soft delete) |
| `deleted_by` | INT | FK a `users.id` (quien eliminó) |
| `created_at` | TIMESTAMP | Fecha de creación |
| `updated_at` | TIMESTAMP | Última actualización |

**Índices:**
- UNIQUE: `code`
- INDEX: `name`
- INDEX: `current_stock`
- INDEX: `status`

**Características:**
- Códigos generados automáticamente (EAN-13)
- Códigos de barras (Code128) y QR descargables
- Compatible con lectores de códigos

**Relaciones:**
- Tiene muchos: `transaction_items`
- Pertenece a: `users` (a través de `deleted_by`)

---

### 7. `transactions`
Tabla de transacciones multi-producto (ingresos, egresos, préstamos).

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | INT | ID único de la transacción |
| `type` | ENUM | Tipo: 'in', 'out', 'loan' |
| `date` | DATE | Fecha de la transacción |
| `person_id` | INT | FK a `people.id` (nullable) |
| `user_id` | INT | FK a `users.id` (quien registró) |
| `notes` | TEXT | Notas generales de la transacción |
| `total_items` | INT | Cantidad total de productos |
| `status` | TINYINT(1) | Estado: 1=válido, 0=invalidado |
| `invalidated_at` | TIMESTAMP | Fecha de invalidación |
| `invalidated_by` | INT | FK a `users.id` (quien invalidó) |
| `loan_code` | VARCHAR(50) | Código único del préstamo (P-XX20251128-001) |
| `loan_status` | ENUM | Estado préstamo: pending, returned, paid, replaced |
| `expected_return_date` | DATE | Fecha esperada de devolución (préstamos) |
| `actual_return_date` | DATE | Fecha real de devolución |
| `return_type` | ENUM | Tipo devolución: same_product, purchased, replaced, other |
| `return_notes` | TEXT | Notas sobre la devolución |
| `related_loan_id` | INT | FK a `transactions.id` (para devoluciones) |
| `created_at` | TIMESTAMP | Fecha de registro |

**Índices:**
- INDEX: `type`
- INDEX: `date`
- INDEX: `person_id`
- INDEX: `user_id`
- INDEX: `status`
- UNIQUE: `loan_code` (para préstamos)
- INDEX: `loan_status`
- INDEX: `related_loan_id`

**Características:**
- Soporte para múltiples productos por transacción
- Códigos únicos para préstamos
- Sistema de seguimiento de devoluciones
- Invalidación de transacciones (no eliminación)
- Estados de préstamo completos

**Relaciones:**
- Pertenece a: `users` (quien registró)
- Pertenece a: `people` (opcional)
- Tiene muchos: `transaction_items` (productos de la transacción)
- Auto-relación: `invalidated_by` y `related_loan_id`

---

### 8. `transaction_items`
Tabla de items individuales de cada transacción (multi-producto).

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | INT | ID único del item |
| `transaction_id` | INT | FK a `transactions.id` |
| `product_id` | INT | FK a `products.id` |
| `quantity` | DECIMAL(10,2) | Cantidad del producto |
| `notes` | TEXT | Notas específicas del producto |
| `created_at` | TIMESTAMP | Fecha de creación |

**Índices:**
- INDEX: `transaction_id`
- INDEX: `product_id`

**Constraints:**
- ON DELETE RESTRICT (no se puede eliminar producto usado en transacción)
- ON UPDATE CASCADE

**Relaciones:**
- Pertenece a: `transactions`
- Pertenece a: `products`

---

### 9. `audit`
Tabla de auditoría de todas las acciones.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | INT | ID único del registro |
| `usuario_id` | INT | FK a `users.id` |
| `accion` | VARCHAR(50) | Acción realizada |
| `tabla_afectada` | VARCHAR(50) | Tabla afectada |
| `registro_afectado_id` | INT | ID del registro afectado |
| `detalles` | TEXT | Detalles de la acción |
| `fecha` | TIMESTAMP | Fecha y hora |

**Índices:**
- INDEX: `usuario_id`
- INDEX: `accion`
- INDEX: `tabla_afectada`
- INDEX: `fecha`

**Relaciones:**
- Pertenece a: `users`

---

### 10. `backups`
Tabla de historial de backups de la base de datos.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | INT | ID único del registro |
| `filename` | VARCHAR(255) | Nombre del archivo de backup |
| `filepath` | VARCHAR(512) | Ruta completa del archivo |
| `size_bytes` | BIGINT | Tamaño del archivo en bytes |
| `usuario_id` | INT | FK a `users.id` (quien creó el backup) |
| `created_at` | TIMESTAMP | Fecha de creación |
| `status` | ENUM('SUCCESS', 'FAILED') | Estado del backup |
| `error_message` | TEXT | Mensaje de error (si falló) |

**Índices:**
- INDEX: `created_at`
- INDEX: `usuario_id`
- INDEX: `status`

**Relaciones:**
- Pertenece a: `users`

---

## 🔄 Migraciones

### Sistema de Migraciones
Las migraciones se ejecutan automáticamente en orden secuencial usando el patrón:
```
YYYYMMDDHHMMSS_descripcion.sql
```

### Tabla de Control
```sql
CREATE TABLE IF NOT EXISTS schema_migrations (
    version VARCHAR(255) PRIMARY KEY,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Migraciones Disponibles

| Versión | Archivo | Descripción |
|---------|---------|-------------|
| 20251114112627 | create_roles_table.sql | Crea tabla de roles |
| 20251114112628 | create_user_table.sql | Crea tabla de usuarios |
| 20251114112848 | create_people_table.sql | Crea tabla de personas |
| 20251114112911 | create_product_table.sql | Crea tabla de productos |
| 20251114113444 | create_transaction_table.sql | Crea tabla de transacciones |
| 20251114114102 | create_audit_table.sql | Crea tabla de auditoría |
| 20251114114104 | create_indexes_table.sql | Crea índices optimizados |
| 20251114114105 | create_permissions_table.sql | Crea tabla de permisos |
| 20251114114106 | create_role_permissions_table.sql | Crea tabla pivote |
| 20251114114107 | seed_roles_and_permissions.sql | Inserta datos iniciales |
| 20251120000001 | create_backups_table.sql | Crea tabla de backups |
| 20251120000002 | add_backup_permissions.sql | Agrega permisos de backup |

---

## 🔗 Diagrama de Relaciones

```
┌─────────────┐       ┌──────────────┐       ┌─────────────┐
│    roles    │◄──────│ rol_permisos │──────►│  permisos   │
└──────┬──────┘       └──────────────┘       └─────────────┘
       │
       │ 1:N
       ▼
┌─────────────┐
│    users    │───────┐
└──────┬──────┘       │
       │              │ 1:N
       │ 1:N          ▼
       │         ┌─────────────┐       ┌──────────────────┐
       │         │   backups   │       │   audit          │
       │         └─────────────┘       └──────────────────┘
       │
       │ 1:N (user_id, invalidated_by, deleted_by)
       ▼
┌──────────────────┐       ┌──────────────────┐
│  transactions    │──────►│ transaction_items│
│  (multi-producto)│  1:N  └────────┬─────────┘
└────┬────┬────────┘               │
     │    │                        │ N:1
     │    │ N:1                    ▼
     │    ▼                  ┌─────────────┐
     │  ┌─────────────┐      │  products   │
     │  │   people    │      │ (con código)│
     │  └─────────────┘      └─────────────┘
     │
     │ (related_loan_id para devoluciones)
     └──────┐
            │
            ▼
      ┌──────────────────┐
      │  transactions    │
      │  (self-relation) │
      └──────────────────┘
```

**Notas:**
- Las transacciones soportan múltiples productos (1:N con transaction_items)
- Los préstamos tienen códigos únicos y seguimiento completo
- Soft delete implementado en users, people y products
- Usuario SYSTEM para operaciones automáticas

---

## 📝 Consultas Útiles

### Ver Roles con Cantidad de Usuarios (Excluyendo SYSTEM)
```sql
SELECT r.nombre, r.descripcion, COUNT(u.id) as usuarios
FROM roles r
LEFT JOIN users u ON r.id = u.rol_id AND u.status = 1 AND u.username != 'SYSTEM'
GROUP BY r.id, r.nombre, r.descripcion;
```

### Ver Permisos de un Rol
```sql
SELECT p.modulo, p.accion, p.descripcion
FROM permisos p
JOIN rol_permisos rp ON p.id = rp.permiso_id
JOIN roles r ON r.id = rp.rol_id
WHERE r.nombre = 'ADMIN'
ORDER BY p.modulo, p.accion;
```

### Stock Bajo de Productos (Solo Activos)
```sql
SELECT id, code, name, current_stock, minimum_stock
FROM products
WHERE current_stock <= minimum_stock 
  AND status = 1
ORDER BY current_stock ASC;
```

### Transacciones Multi-Producto del Día
```sql
SELECT 
    t.id,
    t.type,
    t.date,
    t.total_items,
    t.status,
    p.first_name || ' ' || p.last_name as person_name,
    u.username,
    COUNT(ti.id) as products_count
FROM transactions t
LEFT JOIN people p ON t.person_id = p.id
JOIN users u ON t.user_id = u.id
LEFT JOIN transaction_items ti ON t.id = ti.transaction_id
WHERE DATE(t.created_at) = CURDATE()
GROUP BY t.id
ORDER BY t.created_at DESC;
```

### Préstamos Pendientes
```sql
SELECT 
    t.id,
    t.loan_code,
    t.date,
    t.expected_return_date,
    DATEDIFF(t.expected_return_date, CURDATE()) as days_remaining,
    p.first_name || ' ' || p.last_name as person_name,
    p.phone,
    t.total_items,
    u.username as created_by
FROM transactions t
JOIN people p ON t.person_id = p.id
JOIN users u ON t.user_id = u.id
WHERE t.type = 'loan' 
  AND t.loan_status = 'pending'
  AND t.status = 1
ORDER BY t.expected_return_date ASC;
```

### Préstamos Próximos a Vencer (7 días)
```sql
SELECT 
    t.loan_code,
    p.first_name || ' ' || p.last_name as person_name,
    p.phone,
    t.expected_return_date,
    DATEDIFF(t.expected_return_date, CURDATE()) as days_remaining,
    t.total_items
FROM transactions t
JOIN people p ON t.person_id = p.id
WHERE t.type = 'loan'
  AND t.loan_status = 'pending'
  AND t.status = 1
  AND t.expected_return_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 7 DAY)
ORDER BY t.expected_return_date ASC;
```

### Historial de un Producto
```sql
SELECT 
    t.id,
    t.type,
    t.date,
    ti.quantity,
    ti.notes,
    p.first_name || ' ' || p.last_name as person_name,
    u.username,
    t.status
FROM transaction_items ti
JOIN transactions t ON ti.transaction_id = t.id
LEFT JOIN people p ON t.person_id = p.id
JOIN users u ON t.user_id = u.id
WHERE ti.product_id = ?
ORDER BY t.date DESC, t.created_at DESC;
```

### Auditoría de Acciones de un Usuario
```sql
SELECT 
    a.accion,
    a.tabla_afectada,
    a.registro_afectado_id,
    a.detalles,
    a.fecha
FROM audit a
WHERE a.usuario_id = ?
ORDER BY a.fecha DESC
LIMIT 100;
```

### Usuarios Eliminados (Soft Delete)
```sql
SELECT 
    u.id,
    u.username,
    u.name,
    r.nombre as rol,
    u.deleted_at,
    del_by.username as deleted_by
FROM users u
LEFT JOIN roles r ON u.rol_id = r.id
LEFT JOIN users del_by ON u.deleted_by = del_by.id
WHERE u.status = 0
  AND u.username != 'SYSTEM'
ORDER BY u.deleted_at DESC;
```

---

## 🔐 Características de Seguridad

### Soft Delete
- **Usuarios, Personas y Productos** usan eliminación lógica
- Campo `status`: 1 = activo, 0 = eliminado
- Campos `deleted_at` y `deleted_by` para trazabilidad
- Solo usuarios ROOT pueden eliminar permanentemente
- Usuarios con permiso RESTORE pueden restaurar

### Invalidación de Transacciones
- Transacciones no se eliminan, se **invalidan**
- Campo `status`: 1 = válido, 0 = invalidado
- Campos `invalidated_at` y `invalidated_by` para auditoría
- Permiso especial INVALIDATE requerido
- El stock se ajusta automáticamente al invalidar

### Códigos Únicos
- **Productos**: Código EAN-13 único (`code`)
- **Préstamos**: Código único `P-XX20251128-001` (`loan_code`)
  - P: Préstamo
  - XX: Iniciales de la persona
  - 20251128: Fecha (YYYYMMDD)
  - 001: Secuencial del día

### Integridad Referencial
- `ON DELETE RESTRICT` en productos usados en transacciones
- `ON UPDATE CASCADE` para mantener consistencia
- Foreign keys en todas las relaciones
- Validaciones a nivel de aplicación y base de datos

---

## 🎯 Índices y Optimización

### Índices Importantes
```sql
-- Búsqueda rápida por código de producto
CREATE INDEX idx_products_code ON products(code);

-- Búsqueda rápida por código de préstamo
CREATE UNIQUE INDEX idx_loan_code ON transactions(loan_code);

-- Filtrado por estado
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_people_status ON people(status);
CREATE INDEX idx_products_status ON products(status);
CREATE INDEX idx_transactions_status ON transactions(status);

-- Búsqueda de préstamos
CREATE INDEX idx_transactions_loan_status ON transactions(loan_status);
CREATE INDEX idx_transactions_type ON transactions(type);

-- Auditoría
CREATE INDEX idx_audit_fecha ON audit(fecha);
CREATE INDEX idx_audit_usuario_id ON audit(usuario_id);
```

### Consultas Optimizadas
- Paginación a nivel de repositorio
- Búsquedas con debounce (500ms)
- Índices en campos de búsqueda frecuente
- Joins optimizados en consultas complejas

---

## 📊 Estadísticas y Métricas

### Total de Productos por Estado
```sql
SELECT 
    status,
    COUNT(*) as total,
    SUM(current_stock) as stock_total
FROM products
GROUP BY status;
```

### Préstamos por Estado
```sql
SELECT 
    loan_status,
    COUNT(*) as total
FROM transactions
WHERE type = 'loan'
GROUP BY loan_status;
```

### Usuarios por Rol
```sql
SELECT 
    r.nombre as rol,
    COUNT(u.id) as usuarios
FROM roles r
LEFT JOIN users u ON r.id = u.rol_id AND u.status = 1
WHERE u.username != 'SYSTEM' OR u.username IS NULL
GROUP BY r.id, r.nombre;
```

### Auditoría por Usuario
```sql
SELECT a.*, u.username
FROM audit a
JOIN users u ON a.usuario_id = u.id
WHERE u.username = 'admin'
ORDER BY a.fecha DESC
LIMIT 50;
```

---

## 🔒 Consideraciones de Seguridad

### Contraseñas
- Hasheadas con **BCrypt** (factor 10)
- Nunca almacenar en texto plano
- Validación de complejidad en aplicación

### Credenciales de Conexión
- Cifradas con **AES-256**
- Almacenadas en: `%USERPROFILE%\.inventario_municipal\mysql.config`
- No incluir en código fuente

### Permisos MySQL
```sql
-- Usuario con permisos mínimos necesarios
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX
ON inventario_municipal.*
TO 'inventario_user'@'localhost'
IDENTIFIED BY 'contraseña_segura';
```

---

## 🔧 Backup y Restauración

### Backup Completo
```bash
mysqldump -u root -p inventario_municipal > backup_$(date +%Y%m%d_%H%M%S).sql
```

### Restaurar Backup
```bash
mysql -u root -p inventario_municipal < backup_20251120_020000.sql
```

### Backup Programado (Windows)
```batch
@echo off
set MYSQL_USER=root
set MYSQL_PASS=tu_contraseña
set BACKUP_DIR=C:\Backups\InventarioMunicipal
set TIMESTAMP=%DATE:~-4,4%%DATE:~-7,2%%DATE:~-10,2%_%TIME:~0,2%%TIME:~3,2%%TIME:~6,2%

mysqldump -u %MYSQL_USER% -p%MYSQL_PASS% inventario_municipal > %BACKUP_DIR%\backup_%TIMESTAMP%.sql
```

---

## 📊 Optimización

### Índices Recomendados
Ya implementados en la migración `create_indexes_table.sql`:

- `products`: `name`, `current_stock`
- `transactions`: `product_id`, `type`, `date`, `person_id`, `user_id`
- `audit`: `usuario_id`, `accion`, `tabla_afectada`, `fecha`
- `people`: `dni`, `internal_code`
- `users`: `username`, `rol_id`

### Mantenimiento
```sql
-- Analizar tablas
ANALYZE TABLE products, transactions, audit;

-- Optimizar tablas
OPTIMIZE TABLE products, transactions, audit;

-- Ver uso de índices
SHOW INDEX FROM transactions;
```

---

**Documentación actualizada:** 20 de Noviembre de 2025  
**Versión de BD:** 1.0  
**Compatible con:** MySQL 8.0+

