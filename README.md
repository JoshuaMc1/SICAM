# 🏛️ SICAM - Sistema de Control de Activos Municipales

[![GitHub](https://img.shields.io/badge/GitHub-JoshuaMc1-blue?logo=github)](https://github.com/JoshuaMc1/SICAM)
[![Java](https://img.shields.io/badge/Java-21+-orange?logo=java)](https://www.oracle.com/java/)
[![JavaFX](https://img.shields.io/badge/JavaFX-25.0.1-brightgreen)](https://openjfx.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0+-blue?logo=mysql)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE.txt)

Sistema completo de gestión de inventario desarrollado en JavaFX con MySQL para municipalidades. Incluye **transacciones multi-producto**, sistema de **roles y permisos granulares (RBAC)**, **códigos de barras/QR**, **backups automáticos**, **auditoría completa** y **generación de reportes PDF/CSV**.

**🔗 Repositorio**: [https://github.com/JoshuaMc1/SICAM](https://github.com/JoshuaMc1/SICAM)

---

## ✨ Características Destacadas

### 📦 Gestión de Inventario
- ✅ Control completo de productos con stock actual y mínimo
- ✅ **Códigos de barras y QR** únicos por producto (EAN-13, Code128)
- ✅ Generación automática de códigos o ingreso manual
- ✅ Descarga de códigos en PNG para impresión
- ✅ **Compatible con lectores de códigos de barras/QR**
- ✅ Búsqueda inteligente por ID, nombre o código
- ✅ Alertas automáticas de stock bajo

### 💼 Transacciones Multi-Producto
- ✅ **Ingresos, Egresos y Préstamos** con múltiples productos por transacción
- ✅ Asociación con personas (empleados, beneficiarios)
- ✅ Notas generales y específicas por producto
- ✅ Actualización automática de stock
- ✅ Historial completo de movimientos
- ✅ Búsqueda por ID, producto o persona

### 🔐 Seguridad Robusta
- 🔑 **Sistema RBAC** - 6 roles predefinidos, 84 permisos granulares
- 🔒 **Autenticación** - Contraseñas con BCrypt (10 rounds)
- 👤 **Usuario SYSTEM** - Para operaciones automáticas (invisible en UI)
- 📝 **Auditoría** - Registro completo de acciones del sistema
- ✅ Validaciones en UI y backend
- ✅ Control de acceso por módulo y acción

### 📊 Reportes Profesionales
- 📄 **Exportación PDF y CSV** para todos los módulos
- 📈 Reportes de inventario (completo, stock bajo, sin stock)
- 📋 Reportes de usuarios y roles
- 📊 Reportes de movimientos por fecha y tipo
- 🔍 Exportación de auditoría completa o por rango
- ✅ Encabezado con logo, título y datos del reporte

### 💾 Backups Automáticos
- ⏰ **Backup al iniciar** la aplicación (una vez por día)
- 🚪 **Backup al cerrar** siempre que se cierre el sistema
- 🗜️ Compresión GZIP automática
- 💽 Gestión completa de backups (crear, restaurar, eliminar)
- ⚙️ Configuración habilitada/deshabilitada desde UI
- 📁 Ubicación: `~/.sicam/backups/`

---

## 🚀 Inicio Rápido

### Requisitos
- ☑️ Java JDK 21 o superior
- ☑️ Maven 3.9.11 o superior
- ☑️ MySQL 8.0 o superior
- ☑️ mysqldump y gzip configurados

### Instalación

```bash
# 1. Clonar repositorio
git clone https://github.com/JoshuaMc1/SICAM.git
cd SICAM

# 2. Compilar
mvn clean compile

# 3. Ejecutar
mvn javafx:run
```

### Primera Configuración

1. **Configurar MySQL** (wizard al iniciar):
   - Host, puerto, base de datos
   - Usuario y contraseña

2. **Crear usuario root** (admin):
   - Username, nombre y contraseña

3. **¡Listo!** Ya puedes iniciar sesión

**📖 Guía completa:** [docs/GETTING_STARTED.md](docs/GETTING_STARTED.md)

---

## 📚 Documentación

| Documento | Descripción |
|-----------|-------------|
| **[📚 README](docs/README.md)** | **Índice principal de documentación** |
| **[🚀 GETTING_STARTED](docs/GETTING_STARTED.md)** | Guía de inicio rápido y configuración |
| **[📋 FEATURES](docs/FEATURES.md)** | Lista completa de características |
| **[🏗️ ARCHITECTURE](docs/ARCHITECTURE.md)** | Arquitectura del sistema (MVC + Service + Repository) |
| **[🗄️ DATABASE](docs/DATABASE.md)** | Estructura de base de datos y migraciones |
| **[🔐 SECURITY](docs/SECURITY.md)** | Seguridad, autenticación y autorización (RBAC) |
| **[📝 PLAN_MEJORAS_BUGS](docs/PLAN_MEJORAS_BUGS.md)** | Plan de mejoras y roadmap |

---

## 🛠️ Tecnologías

### Backend
- **Java 21** - Lenguaje principal
- **JavaFX 25.0.1** - Framework de interfaz gráfica
- **ActiveJDBC 3.5** - ORM para acceso a datos
- **MySQL 8.0+** - Base de datos relacional
- **BCrypt** - Hash de contraseñas
- **Maven 3.9.11** - Gestión de dependencias

### Librerías
- **Apache PDFBox** - Generación de reportes PDF
- **ZXing** - Generación de códigos de barras y QR
- **SLF4J + Logback** - Sistema de logs
- **JBCrypt** - Cifrado de contraseñas

---

## 📊 Módulos del Sistema

```
┌─────────────────────────────────────────┐
│  🏠 Dashboard                           │  Estadísticas en tiempo real
├─────────────────────────────────────────┤
│  📦 Inventario                          │  Gestión de productos + Códigos
│  📥 Ingresos                            │  Registro multi-producto
│  📤 Egresos                             │  Salidas multi-producto
│  🤝 Préstamos                           │  Préstamos multi-producto
├─────────────────────────────────────────┤
│  👥 Usuarios                            │  Gestión de usuarios
│  🎭 Roles                               │  Gestión de roles y permisos
│  👤 Personas                            │  Gestión de personas
├─────────────────────────────────────────┤
│  📊 Reportes                            │  Generación PDF/CSV
│  💾 Backups                             │  Gestión de backups
│  🔍 Auditoría                           │  Historial de acciones
│  ⚙️  Configuración                      │  Ajustes del sistema
│  ℹ️  Acerca de                          │  Información del sistema
└─────────────────────────────────────────┘
```

---

## 🔐 Sistema de Roles y Permisos

### Roles Predefinidos

| Rol | Descripción | Acceso |
|-----|-------------|--------|
| **ROOT** | Acceso total al sistema | Todo |
| **ADMIN** | Administración general | Todo excepto configuración crítica |
| **GESTOR** | Gestión de inventario | Inventario, transacciones, reportes |
| **EDITOR** | Edición de datos | Productos, personas, transacciones |
| **OPERADOR** | Operaciones básicas | Registro de transacciones |
| **CONSULTOR** | Solo lectura | Ver información, exportar |

### Permisos Granulares

**84 permisos** distribuidos en **14 módulos** con **6 acciones cada uno**:
- CREATE, READ, UPDATE, DELETE, EXPORT, GENERATE

**Ejemplo:**
- `INVENTARIO.CREATE` - Crear productos
- `REPORTES.GENERATE` - Generar reportes
- `BACKUPS.DELETE` - Eliminar backups

**📖 Documentación completa:** [docs/SECURITY.md](docs/SECURITY.md)

---

## 📷 Características Especiales

### Códigos de Barras y QR
- Generación de códigos EAN-13 automáticos
- Códigos personalizados manuales
- Escaneo con lector USB/Bluetooth
- Descarga en PNG (Code128 y QR)
- Búsqueda por código en todo el sistema

### Backups Inteligentes
- Automáticos al iniciar (una vez por día)
- Automáticos al cerrar (siempre)
- Usuario SYSTEM como responsable
- Compresión GZIP
- Gestión desde UI

### Búsqueda Avanzada
- Búsqueda mientras escribes (debounce 500ms)
- Compatible con lectores de códigos
- Búsqueda por ID, nombre, código, DNI
- Paginación optimizada

---


## 📋 Requisitos del Sistema

### Para Desarrollo
- **Java JDK** 21 o superior
- **Maven** 3.9.11 o superior
- **MySQL Server** 8.0+
  - **mysqldump** (incluido con MySQL Server)
- **gzip** (para compresión de backups)
  - Windows: Instalar desde [GnuWin32](http://gnuwin32.sourceforge.net/packages/gzip.htm) o usar WSL
  - Linux/macOS: Incluido por defecto
- **IDE** compatible (IntelliJ IDEA, Eclipse, NetBeans)
- **Git** (para clonar el repositorio)

### Para Producción/Uso
- **Java Runtime** 21 o superior
- **MySQL Server** 8.0+
  - **mysqldump** (incluido con MySQL Server, verificar en PATH)
- **gzip** (para compresión de backups)
  - Windows: Descargar e instalar [GnuWin32 gzip](http://gnuwin32.sourceforge.net/packages/gzip.htm)
  - Linux: `sudo apt-get install gzip` (Debian/Ubuntu) o `sudo yum install gzip` (RedHat/CentOS)
  - macOS: Incluido por defecto
- **Sistema Operativo**: Windows 10/11, Linux, macOS
- **RAM**: Mínimo 2GB, recomendado 4GB
- **Espacio en disco**: 500MB libres (más espacio para backups)

#### Configuración Adicional para Windows

Para que `mysqldump` y `gzip` funcionen correctamente en el sistema de backups:

1. **mysqldump**: Agregar la carpeta `bin` de MySQL al PATH del sistema
   ```
   C:\Program Files\MySQL\MySQL Server 8.0\bin
   ```

2. **gzip**: Después de instalar GnuWin32, agregar al PATH del sistema
   ```
   C:\Program Files (x86)\GnuWin32\bin
   ```

**Verificar instalación:**
```bash
# Verificar mysqldump
mysqldump --version

# Verificar gzip
gzip --version
```

---

## 🛠️ Instalación y Configuración

### Instalación para Desarrollo

#### 1. Clonar el Repositorio
```bash
git clone https://github.com/JoshuaMc1/SICAM.git
cd SICAM
```

#### 2. Configurar MySQL
```sql
-- Crear base de datos
CREATE DATABASE sicam
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

-- Crear usuario (opcional, recomendado para producción)
CREATE USER 'sicam_user'@'localhost' IDENTIFIED BY 'tu_contraseña_segura';
GRANT ALL PRIVILEGES ON sicam.* TO 'sicam_user'@'localhost';
FLUSH PRIVILEGES;
```

#### 3. Compilar el Proyecto
```bash
mvn clean compile
```

#### 4. Ejecutar en Modo Desarrollo
```bash
mvn javafx:run
```

### Compilar para Distribución

#### Generar JAR Ejecutable
```bash
mvn clean package
```

El JAR se generará en: `target/SICAM-1.0-SNAPSHOT.jar`

#### Ejecutar JAR
```bash
java -jar target/SICAM-1.0-SNAPSHOT.jar
```

---

## 🔧 Configuración Inicial (Primera Vez)

### Paso 1: Configuración de MySQL

Al iniciar por primera vez, el sistema mostrará un diálogo solicitando:

1. **Host** del servidor MySQL (por defecto: `localhost`)
2. **Puerto** (por defecto: `3306`)
3. **Nombre de la base de datos** (por defecto: `sicam`)
4. **Usuario** MySQL
5. **Contraseña** MySQL

El sistema:
- Probará la conexión
- Creará la base de datos si no existe
- Ejecutará todas las migraciones automáticamente
- Cifrará y guardará las credenciales

### Paso 2: Crear Usuario ROOT

Después de configurar MySQL exitosamente, se solicitará crear el primer usuario ROOT:

1. **Nombre completo** del administrador
2. **Nombre de usuario** (único)
3. **Contraseña** segura

Este será el superadministrador con acceso total al sistema.

---

## 👥 Roles y Permisos

El sistema implementa un modelo **RBAC** (Role-Based Access Control) con 6 roles predefinidos:

| Rol | Permisos | Módulos Accesibles | Descripción |
|-----|----------|-------------------|-------------|
| **ROOT** | 52 (100%) | 13/13 | Superadministrador con acceso total |
| **ADMIN** | 40 (77%) | 12/13 | Administrador general (sin Auditoría ni Roles) |
| **SUPERVISOR** | 29 (56%) | 9/13 | Supervisor operativo (sin Usuarios, Roles, Auditoría, Backup) |
| **OPERADOR** | 16 (sin DELETE) | 8/13 | Operador básico (sin eliminaciones) |
| **CONSULTOR** | 12 (solo READ) | 7/13 | Consulta y reportes (solo lectura) |
| **AUDITOR** | 5 (especializado) | 3/13 | Auditoría y supervisión especializada |

### Matriz Detallada de Permisos

Ver [SECURITY.md](docs/SECURITY.md) para la matriz completa de permisos por módulo y rol.

---

## 📊 Módulos del Sistema

### Dashboard
- Estadísticas en tiempo real
- Gráficos de movimientos
- Alertas de stock bajo
- Resumen de transacciones

### Inventario
- CRUD de productos
- Control de stock
- Alertas automáticas
- Historial de movimientos

### Transacciones
- **Ingresos**: Registro de entradas al inventario
- **Egresos**: Registro de salidas del inventario
- **Préstamos**: Gestión de préstamos a personas

### Gestión
- **Personas**: Registro de personas para préstamos
- **Usuarios**: Administración de usuarios del sistema
- **Roles**: Gestión de roles y permisos (solo ROOT)

### Auditoría y Reportes
- **Auditoría**: Vista de logs del sistema (ROOT y AUDITOR)
- **Reportes**: Generación de reportes en PDF/CSV
- **Backup**: Copias de seguridad de la base de datos (ROOT y ADMIN)
- **Perfil**: Gestión del perfil propio del usuario

---

## 🔐 Seguridad

### Capas de Seguridad

1. **Interfaz de Usuario**: Ocultación de elementos sin permiso
2. **Lógica de Negocio**: Validación de permisos antes de ejecutar
3. **Acceso a Datos**: Consultas parametrizadas (prevención SQL Injection)
4. **Base de Datos**: Contraseñas hasheadas, credenciales cifradas

### Características de Seguridad

- ✅ Contraseñas hasheadas con **BCrypt** (factor 10)
- ✅ Credenciales MySQL cifradas con **AES-256**
- ✅ Validaciones especiales (no eliminar ROOT, no auto-eliminación, etc.)
- ✅ Auditoría completa de todas las acciones
- ✅ Control de sesiones de usuario
- ✅ Prevención de SQL Injection

Ver [SECURITY.md](docs/SECURITY.md) para información detallada.

---

## 🗄️ Base de Datos

### Tablas Principales

| Tabla | Descripción |
|-------|-------------|
| `roles` | Roles del sistema (ROOT, ADMIN, GESTOR, etc.) |
| `permisos` | Permisos granulares (84 permisos en total) |
| `rol_permisos` | Relación roles-permisos (Many-to-Many) |
| `users` | Usuarios del sistema (incluye usuario SYSTEM) |
| `people` | Personas (empleados, beneficiarios) |
| `products` | Productos del inventario (con códigos de barras/QR) |
| `transactions` | Transacciones (IN/OUT/LOAN) |
| `transaction_items` | **[Nuevo]** Detalles de productos por transacción (multi-producto) |
| `backups` | Historial de backups realizados |
| `audit` | Registro de auditoría completa |

### Características de la Base de Datos

- ✅ **Integridad Referencial**: Foreign keys en todas las relaciones
- ✅ **Índices Optimizados**: Para búsquedas rápidas
- ✅ **Transacciones ACID**: Consistencia garantizada
- ✅ **Migraciones Automáticas**: Sistema de versionado
- ✅ **Charset UTF-8MB4**: Soporte completo Unicode
- ✅ **InnoDB Engine**: Transacciones y foreign keys

### Migraciones

Las migraciones se ejecutan automáticamente en orden secuencial al iniciar la aplicación. Ver [DATABASE.md](docs/DATABASE.md) para más detalles.

**Migraciones Disponibles:**
- `20251114112627_create_roles_table.sql`
- `20251114112628_create_user_table.sql`
- `20251114112848_create_people_table.sql`
- `20251114112911_create_product_table.sql`
- `20251114113444_create_transaction_table.sql`
- `20251114114102_create_audit_table.sql`
- `20251114114104_create_indexes_table.sql`
- `20251114114105_create_permissions_table.sql`
- `20251114114106_create_role_permissions_table.sql`
- `20251114114107_seed_roles_and_permissions.sql`
- `20251120000001_create_backups_table.sql`
- `20251120000002_add_backup_permissions.sql`
- **`20251122000001_create_transaction_items_table.sql`** ← Multi-producto
- **`20251122000002_add_product_code_field.sql`** ← Códigos de barras/QR
- **`20251124220000_assign_system_to_backups.sql`** ← Usuario SYSTEM

---

## 🏗️ Arquitectura

### Patrón de Diseño

**MVC + Service Layer + Repository**

```
Vista (FXML) → Controlador → Servicio → Repositorio → Modelo → Base de Datos
```

### Estructura de Paquetes

```
com.joshuamc1.sicam/
├── config/           # Configuraciones del sistema
├── controllers/      # Controladores JavaFX (MVC)
├── core/             # Utilidades centrales
├── export/           # Exportación de documentos
├── models/           # Modelos de datos (ORM)
├── repositories/     # Repositorios (acceso a datos)
└── services/         # Servicios (lógica de negocio)
```

Ver [ARCHITECTURE.md](docs/ARCHITECTURE.md) para documentación completa de la arquitectura.

---

## 🐛 Solución de Problemas

### Error: "Cannot connect to MySQL"
**Solución:**
1. Verificar que MySQL esté ejecutándose
2. Verificar credenciales (usuario/contraseña)
3. Revisar que el puerto 3306 esté abierto
4. Desactivar firewall temporalmente para probar

### Error: "Failed to execute migrations"
**Solución:**
1. Verificar permisos del usuario MySQL
2. Asegurar que la base de datos esté vacía (o eliminarla y recrearla)
3. Revisar logs en `logs/app.log`

### Error: "Database not configured"
**Solución:**
1. Eliminar configuración: `%USERPROFILE%\.inventario_municipal\mysql.config`
2. Reiniciar aplicación
3. Completar configuración inicial nuevamente

### Error: "mysqldump: command not found" o fallo en backup
**Solución:**
1. Verificar instalación: `mysqldump --version`
2. Windows: Agregar MySQL bin al PATH:
   - Panel de Control → Sistema → Configuración avanzada del sistema
   - Variables de entorno → PATH → Agregar: `C:\Program Files\MySQL\MySQL Server 8.0\bin`
3. Linux/macOS: Instalar mysql-client: `sudo apt-get install mysql-client`
4. Reiniciar terminal o aplicación después de cambiar PATH

### Error: "gzip: command not found" o fallo en compresión de backup
**Solución:**
1. Verificar instalación: `gzip --version`
2. Windows: 
   - Descargar GnuWin32 gzip desde http://gnuwin32.sourceforge.net/packages/gzip.htm
   - Instalar y agregar al PATH: `C:\Program Files (x86)\GnuWin32\bin`
   - O usar WSL (Windows Subsystem for Linux)
3. Linux: `sudo apt-get install gzip`
4. macOS: gzip viene preinstalado, verificar permisos
5. Reiniciar aplicación después de instalar

### La aplicación no inicia
**Solución:**
1. Verificar que Java 14+ esté instalado: `java -version`
2. Verificar que MySQL esté ejecutándose
3. Revisar logs en `logs/app.log`

---

## 📚 Dependencias Principales

| Dependencia | Versión | Propósito |
|-------------|---------|-----------|
| **JavaFX** | 25.0.1 | Framework de interfaz gráfica |
| **ActiveJDBC** | 3.5-j11 | ORM (Object-Relational Mapping) |
| **MySQL Connector/J** | 8.3.0 | Driver de MySQL |
| **BCrypt** | 0.10.2 | Hashing de contraseñas |
| **Logback** | 1.4.11 | Sistema de logging |
| **Apache PDFBox** | 2.0.29 | Generación de PDFs |

---

## 🔄 Actualización del Sistema

Para actualizar a una nueva versión:

1. **Realizar backup** de la base de datos:
   ```bash
   mysqldump -u root -p inventario_municipal > backup.sql
   ```

2. **Instalar nueva versión** (reemplazar JAR)

3. **Ejecutar aplicación** - Las migraciones nuevas se aplicarán automáticamente

4. **Verificar funcionamiento**

---

## 📞 Soporte y Contacto

### Soporte Técnico
- **Email**: joshua15mclean@gmail.com
- **GitHub Issues**: [Reportar problema](https://github.com/JoshuaMc1/SICAM/issues)

### Enlaces Útiles
- **Repositorio**: https://github.com/JoshuaMc1/SICAM
- **Releases**: https://github.com/JoshuaMc1/SICAM/releases
- **Wiki**: https://github.com/JoshuaMc1/SICAM/wiki

---

## 🎯 Estado del Proyecto

```
✅ Sistema completo y funcional
✅ En producción
✅ Documentación completa
✅ Pruebas realizadas
✅ Backups automáticos
✅ Códigos de barras/QR
✅ Transacciones multi-producto
✅ Sistema de permisos robusto
```

**Versión:** 1.0-SNAPSHOT  
**Estado:** ✅ **Producción-Ready**  
**Fecha:** 3 de Diciembre de 2025

---

## 🚀 Mejoras Recientes Implementadas

### ✅ Transacciones Multi-Producto (Implementado)
- **Estado:** ✅ Completado
- Registro de múltiples productos en una sola transacción
- Tabla `transaction_items` para detalles por producto
- Notas generales y específicas por producto
- Cálculo automático de totales
- Actualización automática de stock
- Interfaz intuitiva para agregar/eliminar productos

### ✅ Códigos de Barras y QR (Implementado)
- **Estado:** ✅ Completado
- Generación automática de códigos EAN-13
- Generación de códigos Code128 y QR
- Descarga en formato PNG para impresión
- **Compatible con lectores de códigos de barras/QR**
- Búsqueda por código en todo el sistema
- Detección automática de Enter al escanear

### ✅ Búsqueda Avanzada (Implementado)
- **Estado:** ✅ Completado
- Búsqueda automática con debounce (500ms)
- Búsqueda por ID, nombre, código, DNI
- Compatible con lectores de códigos
- Paginación optimizada
- Filtros en tiempo real

### ✅ Validaciones Mejoradas (Implementado)
- **Estado:** ✅ Completado
- **Usuarios:** Validación de username único, campos obligatorios, helper text
- **Personas:** Validación de DNI único, máscara de teléfono (0000-0000)
- **Productos:** Validación de código único, campos obligatorios
- **Roles:** Validación preventiva, no eliminar roles en uso
- **Transacciones:** Validación de stock disponible

### ✅ Módulo "Acerca de" (Implementado)
- **Estado:** ✅ Completado
- Información del sistema
- Versión y fecha de compilación
- Créditos y licencia
- Tecnologías utilizadas
- Diseño mejorado y profesional

### ✅ Optimización de Módulos Transaccionales (Implementado)
- **Estado:** ✅ Completado
- Paginación eficiente (10, 25, 50, 100 registros)
- Carga optimizada de productos y personas
- Búsqueda con debounce para evitar consultas excesivas
- Actualización de stock en tiempo real

---

## 🔮 Mejoras Futuras Propuestas

El sistema está **completo y funcional**. Las siguientes son mejoras opcionales para futuras versiones:

### 📱 Propuestas para Consideración

- **Notificaciones por correo**: Alertas automáticas de stock bajo vía email
- **API REST**: Para integración con otros sistemas municipales
- **App móvil**: Versión móvil para consultas rápidas en campo
- **Dashboard avanzado**: Gráficos más sofisticados con JavaFX Charts
- **Importación masiva**: Carga de productos desde Excel/CSV
- **Firma digital**: Firma electrónica en reportes oficiales
- **Multi-sucursal**: Soporte para múltiples ubicaciones físicas
- **Exportación Excel**: Además de CSV, soporte nativo para .xlsx

---

## 📄 Licencia

Este proyecto está bajo la **Licencia MIT**. Ver el archivo [LICENSE.txt](LICENSE.txt) para más detalles.

```
MIT License

Copyright (c) 2025 Joshua MC

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction...
```

---

## 👨‍💻 Desarrollado por

**Joshua MC** - 2025

- GitHub: [@JoshuaMc1](https://github.com/JoshuaMc1)
- Email: joshua15mclean@gmail.com

---

## 🙏 Agradecimientos

- A la comunidad de JavaFX por el excelente framework
- A los desarrolladores de ActiveJDBC por el ORM simple y potente
- A todos los contribuidores y usuarios del sistema

---

**Versión:** 1.0-SNAPSHOT  
**Última actualización:** 3 de Diciembre de 2025  
**Estado:** Producción Ready

