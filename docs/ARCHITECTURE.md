# 🏗️ Arquitectura - SICAM - Sistema de Control de Activos Municipales

## 📁 Estructura del Proyecto

```
SICAM/
├── src/
│   ├── main/
│   │   ├── java/com/joshuamc1/sicam/
│   │   │   ├── config/              # ⚙️ Configuraciones del sistema
│   │   │   ├── controllers/         # 🎮 Controladores JavaFX
│   │   │   ├── core/                # 🔧 Utilidades centrales
│   │   │   ├── export/              # 📄 Exportación de documentos
│   │   │   ├── models/              # 📊 Modelos de datos (ORM)
│   │   │   ├── models/dto/          # 📦 Data Transfer Objects
│   │   │   ├── repositories/        # 🗄️ Repositorios (acceso a datos)
│   │   │   ├── services/            # 🔨 Lógica de negocio
│   │   │   └── App.java             # 🚀 Clase principal
│   │   └── resources/
│   │       ├── css/                 # 🎨 Hojas de estilo
│   │       ├── fxml/                # 🖼️ Vistas JavaFX
│   │       ├── images/              # 🖼️ Recursos gráficos
│   │       ├── migrations/          # 🔄 Migraciones SQL
│   │       ├── database.properties  # 🔧 Config de ActiveJDBC
│   │       └── logback.xml          # 📝 Config de logging
│   └── test/                        # 🧪 Tests unitarios
├── docs/                            # 📚 Documentación
├── logs/                            # 📝 Archivos de log
├── target/                          # 🎯 Archivos compilados
├── pom.xml                          # 📦 Maven config
└── README.md                        # 📖 Documentación principal
```

---

## 🏛️ Patrón de Arquitectura

### MVC + Service Layer + Repository

```
┌─────────────────────────────────────────────────────────────┐
│                         VISTA (FXML)                        │
│  - login.fxml, dashboard.fxml, inventory.fxml, etc.         │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    CONTROLADOR (JavaFX)                     │
│  - LoginController, MainController, InventoryController     │
│  - Maneja eventos UI                                        │
│  - Valida permisos (primera capa)                           │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    SERVICIO (Service)                       │
│  - UserService, ProductService, TransactionService          │
│  - Lógica de negocio                                        │
│  - Validaciones (segunda capa)                              │
│  - Orquesta repositorios                                    │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                  REPOSITORIO (Repository)                   │
│  - UserRepository, ProductRepository                        │
│  - Acceso a datos CRUD                                      │
│  - Consultas específicas                                    │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                     MODELO (ActiveJDBC)                     │
│  - User, Product, Transaction, Rol, Permiso                 │
│  - Mapeo ORM                                                │
│  - Validaciones de modelo                                   │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    BASE DE DATOS (MySQL)                    │
│  - Tablas: users, products, transactions, etc.              │
└─────────────────────────────────────────────────────────────┘
```

---

## 📦 Paquetes y Responsabilidades

### 1. `config/` - Configuración del Sistema

#### `DatabaseConfig.java`
**Propósito:** Gestión de conexión a MySQL con ActiveJDBC

```java
Responsabilidades:
- Abrir/cerrar conexiones a la base de datos
- Cargar propiedades de database.properties
- Proporcionar métodos estáticos para conexión

Métodos principales:
- open(): void
- close(): void
```

#### `MigrationRunner.java`
**Propósito:** Ejecutor de migraciones SQL

```java
Responsabilidades:
- Leer archivos .sql de migrations/
- Ejecutar migraciones en orden
- Registrar migraciones aplicadas
- Idempotencia (no re-ejecutar)

Métodos principales:
- runMigrations(): void
- executeMigration(String filename): void
```

#### `MySQLConfigManager.java`
**Propósito:** Gestión segura de credenciales MySQL

```java
Responsabilidades:
- Cifrar/descifrar credenciales
- Guardar/cargar configuración
- Validar configuración

Métodos principales:
- saveConfig(MySQLConfig): void
- loadConfig(): MySQLConfig
- isConfigured(): boolean
```

#### `MySQLConfigDialog.java`
**Propósito:** Diálogo de configuración inicial

```java
Responsabilidades:
- Interfaz para configurar MySQL
- Probar conexión
- Ejecutar migraciones iniciales
- Configuración de usuario ROOT

Métodos principales:
- showConfigDialog(Stage): void
```

#### `InitialSetupDialog.java`
**Propósito:** Diálogo de configuración de usuario ROOT

```java
Responsabilidades:
- Crear primer usuario ROOT
- Validar datos de usuario
- Configuración inicial del sistema

Métodos principales:
- show(Stage): boolean
```

---

### 2. `controllers/` - Controladores JavaFX

#### Estructura de Paquetes
```
controllers/
├── LoginController.java          # Autenticación
└── dashboard/                    # Dashboard y módulos
    ├── MainController.java       # Menú lateral y navegación
    ├── HomeController.java       # Dashboard principal
    ├── InventoryController.java  # Gestión de inventario
    ├── IncomesController.java    # Registro de ingresos
    ├── OutcomesController.java   # Registro de egresos
    ├── LoansController.java      # Gestión de préstamos
    ├── PeopleController.java     # Gestión de personas
    ├── UsersController.java      # Gestión de usuarios
    ├── RolesController.java      # Gestión de roles
    ├── AuditController.java      # Auditoría del sistema
    ├── ReportsController.java    # Generación de reportes
    ├── BackupController.java     # Gestión de backups
    └── ProfileController.java    # Perfil de usuario
```

#### Responsabilidades Comunes

**Todos los controladores:**
```java
Patrón común:
1. @FXML - Inyección de componentes UI
2. initialize() - Configuración inicial
3. configurePermissions() - Configurar permisos
4. onAction() - Manejadores de eventos
5. loadData() - Carga de datos
6. validate() - Validaciones

Ejemplo típico:
@FXML
public void initialize() {
    configurePermissions();
    loadData();
}

private void configurePermissions() {
    PermissionHelper.hideIfNoPermission(btnNew, "MODULO", "CREATE");
}

@FXML
public void onNew() {
    if (!PermissionHelper.validateAndShow("MODULO", "CREATE")) return;
    // Lógica
}
```

---

### 3. `core/` - Utilidades Centrales

#### `Session.java`
**Propósito:** Gestión de sesión de usuario

```java
Responsabilidades:
- Mantener usuario actual en memoria
- Proporcionar acceso al usuario logueado
- Clear de sesión (logout)

Atributos:
- currentUser: User
- currentUserId: Integer
- currentUsername: String

Métodos:
- setCurrentUser(User): void
- getCurrentUser(): User
- isLoggedIn(): boolean
- clear(): void
```

#### `PermissionHelper.java`
**Propósito:** Validación centralizada de permisos

```java
Responsabilidades:
- Verificar permisos del usuario actual
- Ocultar elementos UI sin permiso
- Mostrar alertas de acceso denegado

Métodos:
- canAccess(String module, String action): boolean
- hideIfNoPermission(Node, String module, String action): void
- validateAndShow(String module, String action): boolean
```

#### `Utils.java`
**Propósito:** Utilidades generales

```java
Responsabilidades:
- Mostrar alertas
- Formateo de datos
- Validaciones comunes

Métodos:
- showAlert(String title, String message): void
- showConfirm(String title, String message): boolean
- formatDate(LocalDate): String
```

#### `Logs.java`
**Propósito:** Sistema de logging

```java
Responsabilidades:
- Logging estructurado
- Niveles: INFO, WARN, ERROR, DEBUG
- Configuración via logback.xml

Métodos:
- info(Class, String message, Object... args): void
- warn(Class, String message, Object... args): void
- error(Class, String message, Throwable t): void
```

#### `DatabaseManager.java`
**Propósito:** Gestión automática de conexiones

```java
Responsabilidades:
- Abrir conexión antes de operación
- Cerrar conexión después de operación
- Manejo de errores de conexión

Métodos:
- execute(Runnable task): void
- executeWithResult(Supplier<T> task): T
```

#### `dialog/` - Diálogos Reutilizables
```
dialog/
├── DateRangeDialog.java       # Selector de rango de fechas
├── PersonDialogHelper.java    # Diálogos de personas
├── TransactionTypeDialog.java # Selector de tipo de transacción
├── UserSelectionDialog.java   # Selector de usuarios
├── MonthYearDialog.java       # Selector mes/año
└── PersonSelectionDialog.java # Selector de personas
```

---

### 4. `export/` - Exportación de Documentos

#### `PdfAuditExporter.java`
**Propósito:** Exportación de auditoría a PDF

```java
Responsabilidades:
- Generar PDF con registros de auditoría
- Formato tabular con encabezados
- Paginación automática

Métodos:
- export(List<AuditDTO>, File): void
```

#### `PdfReportExporter.java`
**Propósito:** Exportación de reportes a PDF

```java
Responsabilidades:
- Generar reportes en PDF
- Múltiples tipos de reportes
- Gráficos y tablas

Métodos:
- exportReport(Report, File): void
```

---

### 5. `models/` - Modelos de Datos (ORM)

#### ActiveJDBC Models

Cada modelo extiende `Model` de ActiveJDBC y representa una tabla en la BD.

#### `User.java`
```java
Tabla: users
Relaciones:
- belongsTo(Rol.class, "rol_id")
- hasMany(Transaction.class, "user_id")

Métodos personalizados:
- setPassword(String): void          // Hashea con BCrypt
- validatePassword(String): boolean  // Verifica hash
- getRol(): Rol                      // Obtiene rol
- getRolNombre(): String             // Obtiene nombre del rol
- tienePermiso(String, String): boolean // Valida permiso
- isRootRole(): boolean              // Es ROOT?
```

#### `Rol.java`
```java
Tabla: roles
Relaciones:
- hasMany(User.class, "rol_id")
- hasManyThrough(Permiso.class, "rol_permisos")

Métodos personalizados:
- getPermisos(): List<Permiso>       // Carga permisos
- sincronizarPermisos(List<Integer>): void // Actualiza permisos
- getUsuarios(): List<User>          // Obtiene usuarios
- isActivo(): boolean                // Está activo?
- isRoot(): boolean                  // Es ROOT?
```

#### `Permiso.java`
```java
Tabla: permisos
Relaciones:
- hasManyThrough(Rol.class, "rol_permisos")

Métodos personalizados:
- agruparPorModulo(): Map<String, List<Permiso>> // Agrupa por módulo
```

#### `Product.java`
```java
Tabla: products
Relaciones:
- hasMany(Transaction.class, "product_id")

Métodos personalizados:
- updateStock(double quantity): void    // Actualiza stock
- isLowStock(): boolean                 // Stock bajo?
- isOutOfStock(): boolean               // Sin stock?
```

#### `Transaction.java`
```java
Tabla: transactions
Constantes:
- TYPE_IN = "IN"      // Ingreso
- TYPE_OUT = "OUT"    // Egreso
- TYPE_LOAN = "LOAN"  // Préstamo

Relaciones:
- belongsTo(Product.class, "product_id")
- belongsTo(User.class, "user_id")
- belongsTo(Person.class, "person_id")
```

#### `Person.java`
```java
Tabla: people
Relaciones:
- hasMany(Transaction.class, "person_id")

Métodos personalizados:
- getFullName(): String // Nombre completo
```

#### `Audit.java`
```java
Tabla: audit
Relaciones:
- belongsTo(User.class, "usuario_id")

Sin métodos personalizados
```

#### `Backup.java`
```java
Tabla: backups
Relaciones:
- belongsTo(User.class, "usuario_id")

Métodos personalizados:
- getUsuario(): User              // Obtiene usuario que creó el backup
- getUsuarioNombre(): String      // Obtiene nombre del usuario
- isSuccessful(): boolean         // Verifica si fue exitoso
- getSizeMB(): double             // Obtiene tamaño en MB
```

---

### 6. `models/dto/` - Data Transfer Objects

#### `AuditDTO.java`
**Propósito:** Transferir datos de auditoría con información de usuario

```java
Atributos:
- id: Integer
- usuarioId: Integer
- usuarioName: String         // JOIN con users
- accion: String
- tablaAfectada: String
- registroAfectadoId: Integer
- detalles: String
- fecha: String
```

---

### 7. `repositories/` - Repositorios

#### Patrón Repository

Cada repositorio encapsula consultas SQL complejas y operaciones CRUD.

#### `UserRepository.java`
```java
Responsabilidades:
- CRUD de usuarios
- Consultas personalizadas

Métodos:
- findById(Integer id): User
- findByUsername(String username): User
- findAll(): List<User>
- save(User user): boolean
- delete(Integer id): boolean
- listPaged(int offset, int limit): List<User>
- count(): long
```

#### `ProductRepository.java`
```java
Responsabilidades:
- CRUD de productos
- Consultas de stock

Métodos:
- findById(Integer id): Product
- findAll(): List<Product>
- findLowStock(): List<Product>
- findOutOfStock(): List<Product>
- updateStock(Integer id, double quantity): boolean
```

#### `TransactionRepository.java`
```java
Responsabilidades:
- CRUD de transacciones
- Consultas por tipo y fecha

Métodos:
- findById(Integer id): Transaction
- findByType(String type): List<Transaction>
- findByDateRange(Date start, Date end): List<Transaction>
- findByProduct(Integer productId): List<Transaction>
```

#### `AuditRepository.java`
```java
Responsabilidades:
- Consultas de auditoría
- Filtros avanzados

Métodos:
- findAll(): List<Audit>
- findByUser(Integer userId): List<Audit>
- findByAction(String action): List<Audit>
- findByDateRange(Date start, Date end): List<Audit>
- listPaged(int offset, int limit): List<AuditDTO>
```

---

### 8. `services/` - Servicios (Lógica de Negocio)

#### `UserService.java`
```java
Responsabilidades:
- Lógica de negocio de usuarios
- Validaciones complejas
- Coordinación con repositorio

Métodos:
- createUser(String name, String username, ...): User
- updateUser(Integer id, ...): boolean
- deleteUser(Integer id): boolean
- resetPassword(Integer id, String newPassword): boolean
- authenticate(String username, String password): User
```

#### `ProductService.java`
```java
Responsabilidades:
- Lógica de negocio de productos
- Gestión de stock
- Validaciones

Métodos:
- createProduct(String name, ...): Product
- updateProduct(Integer id, ...): boolean
- deleteProduct(Integer id): boolean
- adjustStock(Integer id, double quantity): boolean
```

#### `TransactionService.java`
```java
Responsabilidades:
- Registro de transacciones
- Actualización de stock
- Validaciones de negocio

Métodos:
- createTransaction(Integer productId, String type, ...): Transaction
- validateTransaction(Transaction): boolean
- updateProductStock(Transaction): void
```

#### `AuditService.java`
```java
Responsabilidades:
- Registro de auditoría
- Consultas de auditoría

Métodos:
- log(Integer userId, String action, String table, Integer recordId, String details): void
- getAuditTrail(Integer userId): List<Audit>
```

#### `ReportService.java`
```java
Responsabilidades:
- Generación de reportes
- Consultas complejas para reportes

Métodos:
- usersExcludingRoot(): Report
- inventoryComplete(): Report
- transactionsByType(String type): Report
- productsMostMoved(): Report
```

#### `BackupService.java`
```java
Responsabilidades:
- Creación de backups de BD
- Gestión de archivos de backup
- Limpieza de backups antiguos

Métodos:
- createBackup(): Path
- listBackupsPaged(int offset, int limit): List<Backup>
- deleteBackup(Integer backupId): void
- cleanOldBackups(int keepLast): int
- getBackupDirectory(): Path
```

---

## 🔄 Flujo de Datos

### Ejemplo: Crear un Producto

```
1. Usuario hace clic en "Nuevo Producto" (UI)
   ↓
2. InventoryController.onNew()
   - Valida permiso INVENTARIO.CREATE
   ↓
3. Muestra diálogo para ingresar datos
   ↓
4. Usuario llena formulario y presiona "Guardar"
   ↓
5. InventoryController valida datos
   ↓
6. Llama a ProductService.createProduct(...)
   ↓
7. ProductService valida lógica de negocio
   ↓
8. Llama a ProductRepository.save(product)
   ↓
9. ProductRepository ejecuta INSERT en MySQL
   ↓
10. AuditService.log("create_product", ...)
   ↓
11. Retorna éxito a controller
   ↓
12. Controller muestra mensaje de éxito
   ↓
13. Recarga tabla de productos
```

---

## 🎨 Recursos (resources/)

### `css/dashboard.css`
```css
Estilos personalizados para:
- Sidebar del dashboard
- Botones
- Tablas
- Formularios
```

### `fxml/` - Vistas JavaFX
```
fxml/
├── login.fxml              # Pantalla de login
└── dashboard/
    ├── home.fxml           # Dashboard principal
    ├── inventory.fxml      # Gestión de inventario
    ├── incomes.fxml        # Registro de ingresos
    ├── outcomes.fxml       # Registro de egresos
    ├── loans.fxml          # Gestión de préstamos
    ├── people.fxml         # Gestión de personas
    ├── users.fxml          # Gestión de usuarios
    ├── roles.fxml          # Gestión de roles
    ├── audit.fxml          # Auditoría
    ├── reports.fxml        # Reportes
    ├── backup.fxml         # Gestión de backups
    └── profile.fxml        # Perfil de usuario
```

### `migrations/` - Migraciones SQL
```
Patrón de nombres:
YYYYMMDDHHMMSS_descripcion.sql

Ejemplo:
20251114112627_create_roles_table.sql
```

---

## 🔌 Dependencias Principales

### Maven Dependencies (pom.xml)

```xml
<!-- JavaFX -->
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-controls</artifactId>
    <version>25.0.1</version>
</dependency>

<!-- ActiveJDBC (ORM) -->
<dependency>
    <groupId>org.javalite</groupId>
    <artifactId>activejdbc</artifactId>
    <version>3.5-j11</version>
</dependency>

<!-- MySQL Driver -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>8.3.0</version>
</dependency>

<!-- BCrypt (Hashing) -->
<dependency>
    <groupId>at.favre.lib</groupId>
    <artifactId>bcrypt</artifactId>
    <version>0.10.2</version>
</dependency>

<!-- Logback (Logging) -->
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.4.11</version>
</dependency>

<!-- PDFBox (PDF Generation) -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>2.0.29</version>
</dependency>
```

---

## 🚀 Ciclo de Vida de la Aplicación

```
1. App.main()
   ↓
2. Verifica configuración MySQL
   ↓
3. Si NO configurado:
   - Muestra MySQLConfigDialog
   - Guarda configuración
   - Ejecuta migraciones
   - Muestra InitialSetupDialog (crear ROOT)
   ↓
4. Si SÍ configurado:
   - Carga login.fxml
   ↓
5. Usuario ingresa credenciales
   ↓
6. LoginController.onLogin()
   - Valida credenciales
   - Crea sesión (Session.setCurrentUser)
   - Registra auditoría (login)
   ↓
7. Carga dashboard.fxml (MainController)
   ↓
8. MainController.setLoggedUser()
   - Configura permisos del menú
   - Oculta módulos sin acceso
   - Carga home.fxml por defecto
   ↓
9. Usuario interactúa con el sistema
   ↓
10. Usuario hace logout
    - Registra auditoría (logout)
    - Session.clear()
    - Vuelve a login.fxml
```

---

**Documentación actualizada:** 20 de Noviembre de 2025  
**Versión:** 1.0  
**Arquitectura:** MVC + Service + Repository

