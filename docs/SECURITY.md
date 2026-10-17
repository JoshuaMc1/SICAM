# 🔐 Seguridad - Sistema de Inventario Municipal

**Última actualización:** 28 de Noviembre de 2025

---

## 🛡️ Arquitectura de Seguridad

### Capas de Seguridad Implementadas

```
┌─────────────────────────────────────────┐
│     CAPA 1: Interfaz de Usuario         │
│  - Ocultación de elementos sin permiso  │
│  - Validación de formularios            │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│     CAPA 2: Lógica de Negocio           │
│  - Validación de permisos               │
│  - Validaciones especiales              │
│  - Control de sesión                    │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│     CAPA 3: Acceso a Datos              │
│  - Consultas parametrizadas             │
│  - Prevención de SQL Injection          │
│  - Transacciones ACID                   │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│     CAPA 4: Base de Datos               │
│  - Contraseñas hasheadas                │
│  - Cifrado de credenciales              │
│  - Auditoría completa                   │
└─────────────────────────────────────────┘
```

---

## 🔑 Sistema de Autenticación

### Contraseñas

#### Hashing (BCrypt)
```java
// Configuración
- Algoritmo: BCrypt
- Factor de trabajo: 10 (2^10 = 1,024 iteraciones)
- Salt: Generado automáticamente
- Longitud hash: 60 caracteres
```

**Ejemplo de hash:**
```
$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
│   │  │                    Salt (22 chars)                    │      Hash (31 chars)       │
│   │  └─ Factor de trabajo (10)
│   └─ Versión de BCrypt (2a)
└─ Identificador de algoritmo
```

#### Validación de Contraseñas
```java
// Requisitos mínimos
- Longitud mínima: 6 caracteres
- Sin restricción de complejidad (a mejorar)
- Verificación de contraseña actual en cambios

// Recomendaciones para producción
- Longitud mínima: 8 caracteres
- Al menos una mayúscula
- Al menos un número
- Al menos un carácter especial
```

#### Contraseña por Defecto
```java
// Formato para nuevos usuarios
Patrón: "contraseña" + año_actual
Ejemplo 2025: "contraseña2025"

// Flag de cambio obligatorio
must_change_password = 1
```

---

## 🔐 Sistema de Autorización

### Modelo RBAC (Role-Based Access Control)

#### Estructura de Permisos
```
Permiso = MÓDULO + ACCIÓN

Ejemplo:
- INVENTARIO.CREATE
- INVENTARIO.READ
- INVENTARIO.UPDATE
- INVENTARIO.DELETE
```

#### Roles del Sistema

| Rol | Permisos | Módulos | Descripción |
|-----|----------|---------|-------------|
| **ROOT** | 98 (100%) | 14/14 | Superadministrador con acceso total |
| **ADMIN** | 85 (~87%) | 13/14 | Administrador general (sin auditoría total) |
| **SUPERVISOR** | 45 (~46%) | 9/14 | Supervisor operativo |
| **OPERADOR** | 28 (~29%) | 8/14 | Operador básico (sin DELETE) |
| **CONSULTOR** | 20 (~20%) | 8/14 | Consulta y reportes |
| **AUDITOR** | 12 (~12%) | 3/14 | Auditoría y supervisión especializada |

### Matriz de Permisos

```
┌─────────────┬──────┬───────┬────────────┬──────────┬───────────┬─────────┐
│   Módulo    │ ROOT │ ADMIN │ SUPERVISOR │ OPERADOR │ CONSULTOR │ AUDITOR │
├─────────────┼──────┼───────┼────────────┼──────────┼───────────┼─────────┤
│ Dashboard   │  ✓   │   ✓   │     ✓     │    ✓     │     ✓     │    ✓   │
│ Inventario  │ CRUDRS│ CRUDRS│    CRUD   │   CRU    │     R     │    -    │
│ Ingresos    │CRUDRIS│CRUDRI │    CRUD   │   CRU    │     R     │    -    │
│ Egresos     │CRUDRIS│CRUDRI │    CRUD   │   CRU    │     R     │    -    │
│ Préstamos   │CRUDRIS│CRUDRI │    CRUD   │   CRU    │     R     │    -    │
│ Personas    │ CRUDRS│ CRUDRS│    CRUD   │   CRU    │     R     │    -    │
│ Usuarios    │ CRUDRS│ CRUDRS│     -     │    -     │     -     │    -    │
│ Roles       │ CRUD  │ CRUD  │     -     │    -     │     -     │    -    │
│ Auditoría   │  RE   │   R   │     -     │    -     │     -     │   RE    │
│ Reportes    │  GE   │   GE  │     GE    │    GE    │    GE     │   GE    │
│ Backup      │ CRD   │  CRD  │     -     │    -     │     -     │    -    │
│ Perfil      │  U    │   U   │     U     │    U     │     U     │    U    │
│ Configuración│ RU   │   R   │     -     │    -     │     -     │    -    │
└─────────────┴──────┴───────┴────────────┴──────────┴───────────┴─────────┘

Leyenda:
C = CREATE    │ R = READ     │ U = UPDATE   │ D = DELETE      │ E = EXPORT
G = GENERATE  │ S = RESTORE  │ I = INVALIDATE
```

### Acciones por Permiso

| Acción | Código | Descripción | Aplicable a |
|--------|--------|-------------|-------------|
| **Crear** | CREATE | Crear nuevos registros | Todos los módulos CRUD |
| **Leer** | READ | Ver y listar registros | Todos los módulos |
| **Actualizar** | UPDATE | Editar registros existentes | Todos los módulos CRUD |
| **Eliminar** | DELETE | Eliminar registros (soft delete) | Todos los módulos CRUD |
| **Restaurar** | RESTORE | Restaurar registros eliminados | Usuarios, Personas, Productos |
| **Invalidar** | INVALIDATE | Invalidar transacciones | Ingresos, Egresos, Préstamos |
| **Exportar** | EXPORT | Exportar datos (CSV, PDF) | Reportes, Auditoría |
| **Generar** | GENERATE | Generar reportes | Reportes |

---

## 🔒 Validaciones de Seguridad

### Validaciones Especiales Implementadas

#### 1. Protección del Usuario ROOT
```java
// En UsersController.onDelete()
if (rol != null && "ROOT".equals(rol.getString("nombre"))) {
    Utils.showAlert("Error", "No se puede eliminar el usuario ROOT del sistema");
    return;
}
```

#### 2. Prevención de Auto-Eliminación
```java
// En UsersController.onDelete()
User currentUser = Session.getCurrentUser();
if (selected.getId().equals(currentUser.getId())) {
    Utils.showAlert("Error", "No puedes eliminar tu propio usuario");
    return;
}
```

#### 3. Protección de Roles del Sistema
```java
// En RolesController.onEliminarRol()
List<String> rolesDelSistema = Arrays.asList(
    "ROOT", "ADMIN", "AUDITOR", "SUPERVISOR", "OPERADOR", "CONSULTOR"
);

if (rolesDelSistema.contains(nombreRol)) {
    Utils.showAlert("Error", "No se puede eliminar un rol del sistema");
    return;
}
```

#### 4. Prevención de Eliminación de Roles en Uso
```java
// En RolesController.onEliminarRol()
int cantidadUsuarios = rol.getUsuarios().size();
if (cantidadUsuarios > 0) {
    Utils.showAlert("Error", 
        "No se puede eliminar el rol porque tiene " + cantidadUsuarios + " usuario(s) asignado(s)");
    return;
}
```

#### 5. Validación de Cambio de Contraseña
```java
// En ProfileController.onSave()
// 1. Verificar contraseña actual
if (!currentUser.validatePassword(currentPassword)) {
    Utils.showAlert("Error", "La contraseña actual es incorrecta");
    return;
}

// 2. Validar longitud mínima
if (newPassword.length() < 6) {
    Utils.showAlert("Error", "La nueva contraseña debe tener al menos 6 caracteres");
    return;
}

// 3. Verificar confirmación
if (!newPassword.equals(confirmPassword)) {
    Utils.showAlert("Error", "Las contraseñas nuevas no coinciden");
    return;
}
```

#### 6. Protección de Escalada de Privilegios
```java
// En ProfileController
// Los usuarios NO pueden cambiar su propio rol
// Los campos username y rol son de solo lectura
txtUsername.setEditable(false);
txtRole.setEditable(false);
```

---

## 🔐 Cifrado de Datos Sensibles

### Credenciales de MySQL

#### Algoritmo de Cifrado
```java
Algoritmo: AES-256 (Advanced Encryption Standard)
Modo: CBC (Cipher Block Chaining)
Padding: PKCS5Padding
Key Derivation: PBKDF2 con SHA-256
Iteraciones: 65,536
Salt: Aleatorio de 16 bytes
IV: Aleatorio de 16 bytes por operación
```

#### Ubicación del Archivo Cifrado
```
Windows: %USERPROFILE%\.inventario_municipal\mysql.config
Linux/Mac: ~/.inventario_municipal/mysql.config
```

#### Estructura del Archivo
```json
{
  "host": "encrypted_base64_string",
  "port": "encrypted_base64_string",
  "database": "encrypted_base64_string",
  "username": "encrypted_base64_string",
  "password": "encrypted_base64_string"
}
```

---

## 🕵️ Sistema de Auditoría

### Registro de Acciones

#### Eventos Auditados
```java
// Autenticación
- login: Inicio de sesión exitoso
- logout: Cierre de sesión
- login_failed: Intento de login fallido (a implementar)

// CRUD de Usuarios
- create_user: Creación de usuario
- update_user: Actualización de usuario
- delete_user: Eliminación de usuario
- reset_password: Reseteo de contraseña

// CRUD de Roles
- create_rol: Creación de rol
- update_rol: Actualización de rol
- delete_rol: Eliminación de rol
- update_permissions: Cambio de permisos de rol

// CRUD de Productos
- create_product: Creación de producto
- update_product: Actualización de producto
- delete_product: Eliminación de producto

// Transacciones
- create_transaction: Registro de transacción
- update_transaction: Modificación de transacción
- delete_transaction: Eliminación de transacción

// CRUD de Personas
- create_person: Creación de persona
- update_person: Actualización de persona
- delete_person: Eliminación de persona

// Backups
- create_backup: Creación de backup de BD
- delete_backup: Eliminación de backup
```

#### Información Capturada
```java
public class AuditDTO {
    private Integer id;                    // ID del registro
    private Integer usuarioId;             // ID del usuario
    private String usuarioName;            // Nombre del usuario
    private String accion;                 // Acción realizada
    private String tablaAfectada;          // Tabla afectada
    private Integer registroAfectadoId;    // ID del registro afectado
    private String detalles;               // Detalles adicionales (JSON)
    private String fecha;                  // Timestamp
}
```

#### Ejemplo de Registro de Auditoría
```json
{
  "id": 1,
  "usuarioId": 1,
  "usuarioName": "admin",
  "accion": "create_product",
  "tablaAfectada": "products",
  "registroAfectadoId": 42,
  "detalles": "{\"name\":\"Laptop Dell\",\"quantity\":5}",
  "fecha": "2025-11-20 02:00:00"
}
```

---

## 🔍 Control de Sesiones

### Gestión de Sesión

#### Clase Session
```java
public class Session {
    private static User currentUser;        // Usuario actual
    private static Integer currentUserId;   // ID del usuario
    private static String currentUsername;  // Username
    
    // Métodos
    public static void setCurrentUser(User user);
    public static User getCurrentUser();
    public static boolean isLoggedIn();
    public static void clear();  // Logout
}
```

#### Ciclo de Vida de Sesión
```
┌─────────────┐
│   LOGIN     │
└──────┬──────┘
       │
       ▼
┌─────────────┐     ┌──────────────┐
│  Validar    │────►│  Credenciales│
│ Credenciales│     │   Correctas? │
└─────────────┘     └──────┬───────┘
                           │ Sí
                           ▼
                    ┌──────────────┐
                    │ Crear Sesión │
                    │ currentUser  │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │   Dashboard  │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │  LOGOUT      │
                    │ Session.clear│
                    └──────────────┘
```

#### Timeout de Sesión
```java
// A implementar (recomendado para producción)
- Timeout de inactividad: 30 minutos
- Renovación automática: Al realizar acción
- Aviso antes de expirar: 5 minutos
```

---

## 🛡️ Prevención de Vulnerabilidades

### SQL Injection

#### ✅ Protección Implementada
```java
// Uso de consultas parametrizadas (ActiveJDBC)
User.where("username = ?", username);
Product.where("id = ?", productId);

// ❌ NUNCA hacer esto
String sql = "SELECT * FROM users WHERE username = '" + username + "'";

// ✅ SIEMPRE usar parámetros
String sql = "SELECT * FROM users WHERE username = ?";
```

### XSS (Cross-Site Scripting)

#### Protección en JavaFX
```java
// JavaFX sanitiza automáticamente inputs
// No permite ejecución de scripts en Labels/TextFields

// Recomendación adicional para exportación HTML
StringEscapeUtils.escapeHtml4(userInput);
```

### CSRF (Cross-Site Request Forgery)

#### No Aplica
```
Aplicación de escritorio (JavaFX) - No vulnerable a CSRF
Solo aplicable en aplicaciones web
```

### Path Traversal

#### Protección en Exportación
```java
// Validar nombres de archivo
File file = new File(userSelectedPath);
if (!file.getCanonicalPath().startsWith(allowedDirectory)) {
    throw new SecurityException("Invalid file path");
}
```

---

## 📋 Mejores Prácticas de Seguridad

### Para Desarrolladores

1. **Nunca almacenar contraseñas en texto plano**
   ```java
   ❌ user.setPassword(plainPassword);
   ✅ user.setPassword(BCrypt.hashpw(plainPassword, BCrypt.gensalt(10)));
   ```

2. **Validar permisos en múltiples capas**
   ```java
   // Capa 1: UI
   PermissionHelper.hideIfNoPermission(button, "MODULO", "ACCION");
   
   // Capa 2: Lógica
   if (!PermissionHelper.validateAndShow("MODULO", "ACCION")) {
       return;
   }
   ```

3. **Registrar todas las acciones críticas**
   ```java
   AuditService.log(userId, "delete_user", "users", deletedUserId, details);
   ```

4. **Validar entrada de usuario**
   ```java
   if (input == null || input.trim().isEmpty()) {
       throw new ValidationException("Campo obligatorio");
   }
   ```

5. **Manejar excepciones apropiadamente**
   ```java
   try {
       // Operación
   } catch (Exception e) {
       Logs.error(getClass(), "Error: {}", e.getMessage());
       // NO mostrar stack trace al usuario
       Utils.showAlert("Error", "Operación fallida");
   }
   ```

### Para Administradores

1. **Cambiar contraseñas por defecto inmediatamente**
2. **Usar contraseñas fuertes y únicas**
3. **Revisar logs de auditoría regularmente**
4. **Mantener backups actualizados**
5. **Limitar permisos al mínimo necesario**
6. **Actualizar el sistema regularmente**

---

## 🤖 Usuario SYSTEM

### Características Especiales

El sistema incluye un usuario especial `SYSTEM` para operaciones automáticas.

#### Configuración
```java
username: "SYSTEM"
password: "" (vacío, no válido para login)
rol_id: NULL
status: 0 (inactivo)
must_change_password: 0
```

#### Propósito
- Realizar operaciones automáticas del sistema
- Registro de backups automáticos
- Operaciones programadas
- Tareas del sistema sin intervención humana

#### Protecciones
```java
// 1. No puede iniciar sesión
if ("SYSTEM".equals(username)) {
    throw new AuthenticationException("Usuario no autorizado");
}

// 2. No aparece en lista de usuarios
WHERE u.username != 'SYSTEM' AND u.status = 1

// 3. No puede ser creado manualmente
if ("SYSTEM".equalsIgnoreCase(username)) {
    throw new ValidationException("No se puede crear usuario SYSTEM");
}

// 4. No puede ser editado
if ("SYSTEM".equals(user.getUsername())) {
    throw new SecurityException("Usuario SYSTEM no puede ser modificado");
}

// 5. No puede ser eliminado
if ("SYSTEM".equals(user.getUsername())) {
    throw new SecurityException("Usuario SYSTEM no puede ser eliminado");
}
```

#### Uso del Usuario SYSTEM
```java
// Backups automáticos
User systemUser = User.findFirst("username = ?", "SYSTEM");
Backup backup = new Backup();
backup.set("usuario_id", systemUser.getId());
backup.set("mensaje", "Copia de seguridad creada por el sistema");
backup.saveIt();
// NO se registra en auditoría
```

---

## 🗑️ Soft Delete (Eliminación Lógica)

### Concepto

En lugar de eliminar registros permanentemente de la base de datos, se marcan como "eliminados" mediante campos de estado.

### Implementación

#### Campos de Soft Delete
```sql
-- En tablas: users, people, products
status TINYINT(1) DEFAULT 1,        -- 1=activo, 0=eliminado
deleted_at TIMESTAMP NULL,           -- Fecha de eliminación
deleted_by INT NULL,                 -- Usuario que eliminó
FOREIGN KEY (deleted_by) REFERENCES users(id)
```

#### Proceso de Eliminación
```java
// 1. Usuario hace click en "Eliminar"
public void softDelete(Integer id) {
    // Verificar permiso DELETE
    if (!hasPermission("DELETE")) {
        throw new PermissionException("Sin permiso para eliminar");
    }
    
    // Marcar como eliminado
    Product product = Product.findById(id);
    product.set("status", 0);
    product.set("deleted_at", new Timestamp(System.currentTimeMillis()));
    product.set("deleted_by", Session.getCurrentUserId());
    product.saveIt();
    
    // Registrar en auditoría
    AuditService.log(userId, "soft_delete", "products", id, "Producto eliminado");
}
```

### Restauración de Datos

#### Permiso RESTORE
```java
// Solo usuarios con permiso RESTORE pueden ver y restaurar
public void restore(Integer id) {
    // Verificar permiso RESTORE
    if (!hasPermission("RESTORE")) {
        throw new PermissionException("Sin permiso para restaurar");
    }
    
    // Restaurar registro
    Product product = Product.findById(id);
    product.set("status", 1);
    product.set("deleted_at", null);
    product.set("deleted_by", null);
    product.saveIt();
    
    // Registrar en auditoría
    AuditService.log(userId, "restore", "products", id, "Producto restaurado");
}
```

### Filtrado de Datos

#### Consultas Normales (Solo Activos)
```java
// Sin permiso RESTORE - solo ve activos
List<Product> products = Product.where("status = ?", 1);
```

#### Consultas con Permiso RESTORE
```java
// Con permiso RESTORE - ve todos (con badge de estado)
if (hasPermission("RESTORE")) {
    // Mostrar todos
    List<Product> products = Product.findAll();
} else {
    // Solo activos
    List<Product> products = Product.where("status = ?", 1);
}
```

### Eliminación Permanente

#### Solo Usuario ROOT
```java
public void hardDelete(Integer id) {
    // SOLO ROOT puede eliminar permanentemente
    if (!isRoot()) {
        throw new PermissionException("Solo ROOT puede eliminar permanentemente");
    }
    
    // Verificar que esté eliminado (soft delete)
    Product product = Product.findById(id);
    if (product.getInteger("status") == 1) {
        throw new IllegalStateException("Debe marcar como eliminado antes de borrar");
    }
    
    // Eliminar de BD
    product.delete();
    
    // Registrar en auditoría
    AuditService.log(userId, "hard_delete", "products", id, "Eliminación permanente");
}
```

### Ventajas del Soft Delete

1. **Recuperación de datos**: Errores humanos pueden corregirse
2. **Auditoría completa**: Historial de quién eliminó y cuándo
3. **Integridad referencial**: Referencias a datos "eliminados" siguen válidas
4. **Análisis histórico**: Datos disponibles para reportes y análisis
5. **Cumplimiento legal**: Algunos datos no deben eliminarse permanentemente

---

## ❌ Invalidación de Transacciones

### Concepto

Las transacciones (ingresos, egresos, préstamos) no se eliminan, se **invalidan**.

### Implementación

#### Campos de Invalidación
```sql
-- En tabla: transactions
status TINYINT(1) DEFAULT 1,         -- 1=válido, 0=invalidado
invalidated_at TIMESTAMP NULL,        -- Fecha de invalidación
invalidated_by INT NULL,              -- Usuario que invalidó
FOREIGN KEY (invalidated_by) REFERENCES users(id)
```

#### Proceso de Invalidación
```java
// Permiso: INVALIDATE
public void invalidateTransaction(Integer transactionId) {
    // Verificar permiso
    if (!hasPermission("INVALIDATE")) {
        throw new PermissionException("Sin permiso para invalidar");
    }
    
    Transaction transaction = Transaction.findById(transactionId);
    
    // 1. Ajustar stock (revertir operación)
    if ("in".equals(transaction.getString("type"))) {
        // Era ingreso → restar del stock
        adjustStock(transaction, -1);
    } else if ("out".equals(transaction.getString("type"))) {
        // Era egreso → sumar al stock
        adjustStock(transaction, 1);
    }
    
    // 2. Marcar como invalidado
    transaction.set("status", 0);
    transaction.set("invalidated_at", new Timestamp(System.currentTimeMillis()));
    transaction.set("invalidated_by", Session.getCurrentUserId());
    transaction.saveIt();
    
    // 3. Auditoría
    AuditService.log(userId, "invalidate_transaction", "transactions", 
                    transactionId, "Transacción invalidada");
}
```

#### Filtrado de Transacciones
```java
// Ver solo válidas (por defecto)
List<Transaction> validTransactions = 
    Transaction.where("status = ?", 1);

// Ver todas (con badge de estado)
List<Transaction> allTransactions = Transaction.findAll();

// Ver solo invalidadas
List<Transaction> invalidTransactions = 
    Transaction.where("status = ?", 0);
```

### Diferencia: Soft Delete vs Invalidación

| Característica | Soft Delete | Invalidación |
|----------------|-------------|--------------|
| **Aplicable a** | Usuarios, Personas, Productos | Transacciones |
| **Campo** | `status` | `status` |
| **Puede restaurarse** | ✅ Sí (con permiso RESTORE) | ❌ No |
| **Ajusta stock** | ❌ No | ✅ Sí |
| **Motivo** | Error, duplicado | Corrección, error de registro |
| **Visible para** | Solo con permiso RESTORE | Todos (con badge) |
| **Auditoría** | `soft_delete` / `restore` | `invalidate_transaction` |

---

## 🎯 Resumen de Seguridad

### Capas de Protección

```
1. UI (Interfaz)
   └─ Botones ocultos/deshabilitados según permisos
   
2. Controladores
   └─ Validación de permisos antes de ejecutar
   
3. Servicios
   └─ Lógica de negocio con validaciones
   
4. Repositorios
   └─ Consultas parametrizadas (SQL injection protection)
   
5. Base de Datos
   └─ Contraseñas hasheadas, integridad referencial
```

### Características Clave

✅ **Autenticación**: BCrypt con salt automático  
✅ **Autorización**: RBAC con 7 acciones y 14 módulos  
✅ **Auditoría**: Log completo de todas las acciones  
✅ **Soft Delete**: Eliminación lógica recuperable  
✅ **Invalidación**: Transacciones no se eliminan  
✅ **Usuario SYSTEM**: Operaciones automáticas seguras  
✅ **Sesiones**: Control de usuario actual  
✅ **Permisos granulares**: Control por módulo y acción  
✅ **Validaciones múltiples**: UI + Backend + BD  

### Recomendaciones para Producción

1. ✅ Implementar timeout de sesión
2. ✅ Agregar requisitos de complejidad de contraseñas
3. ✅ Implementar política de rotación de contraseñas
4. ✅ Habilitar logs de seguridad detallados
5. ✅ Revisar permisos regularmente
6. ✅ Mantener backups cifrados
7. ✅ Usar conexión MySQL cifrada (SSL/TLS)
8. ✅ Implementar 2FA (autenticación de dos factores)
9. ✅ Limitar intentos de login fallidos
10. ✅ Monitorear actividad sospechosa en auditoría

---

**Última revisión**: 28 de Noviembre de 2025  
**Versión**: 1.0-SNAPSHOT
7. **Deshabilitar usuarios inactivos**

### Para Usuarios Finales

1. **No compartir contraseñas**
2. **Cerrar sesión al terminar**
3. **Reportar actividad sospechosa**
4. **No dejar la sesión sin supervisión**
5. **Cambiar contraseña periódicamente**

---

## 🔄 Plan de Mejora Continua

### Recomendaciones para Producción

#### Autenticación
- [ ] Implementar autenticación de dos factores (2FA)
- [ ] Agregar preguntas de seguridad
- [ ] Implementar CAPTCHA después de X intentos fallidos
- [ ] Bloqueo temporal de cuenta tras intentos fallidos

#### Contraseñas
- [ ] Política de contraseñas más estricta (8+ caracteres, complejidad)
- [ ] Historial de contraseñas (no reutilizar últimas 5)
- [ ] Expiración de contraseñas (cada 90 días)
- [ ] Validación contra diccionario de contraseñas comunes

#### Sesiones
- [ ] Timeout de inactividad (30 minutos)
- [ ] Cierre de sesión automático
- [ ] Un solo inicio de sesión activo por usuario
- [ ] Notificación de inicio de sesión en nuevo dispositivo

#### Auditoría
- [ ] Registrar intentos de login fallidos
- [ ] Alertas en tiempo real para acciones críticas
- [ ] Dashboard de seguridad para administradores
- [ ] Exportación automática de logs

#### Cifrado
- [ ] Cifrado de base de datos en reposo
- [ ] Cifrado de comunicación con MySQL (SSL/TLS)
- [ ] Rotación periódica de claves de cifrado
- [ ] Hardware Security Module (HSM) para producción

---

## 📞 Reporte de Vulnerabilidades

Si encuentras una vulnerabilidad de seguridad:

1. **NO** la publiques públicamente
2. Envía un email a: joshua15mclean@gmail.com
3. Incluye:
   - Descripción detallada de la vulnerabilidad
   - Pasos para reproducir
   - Impacto potencial
   - Sugerencias de mitigación (opcional)

**Tiempo de respuesta estimado:** 48-72 horas

---

**Documentación actualizada:** 20 de Noviembre de 2025  
**Versión:** 1.0  
**Estado de seguridad:** Producción (con recomendaciones de mejora)

