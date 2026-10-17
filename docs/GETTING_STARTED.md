# 🚀 Guía de Inicio Rápido - Inventario Municipal

**Última actualización:** 28 de Noviembre de 2025

---

## 📋 Requisitos Previos

### **Desarrollo**
```
☑️ Java JDK 21 o superior
☑️ Maven 3.9.11 o superior
☑️ MySQL 8.0 o superior
☑️ mysqldump (incluido con MySQL, debe estar en PATH)
☑️ gzip (Linux/Mac incluido, Windows: instalar y agregar a PATH)
☑️ IDE recomendado: IntelliJ IDEA
☑️ Impresora térmica (opcional, para recibos)
```

### **Producción**
```
☑️ Java JRE 21 o superior
☑️ MySQL 8.0 o superior
☑️ mysqldump configurado en PATH del sistema
☑️ gzip configurado en PATH del sistema
☑️ 2GB RAM mínimo (4GB recomendado)
☑️ 1GB espacio en disco para aplicación
☑️ Espacio adicional para backups (depende del tamaño de BD)
☑️ Impresora térmica 58mm/80mm (opcional)
```

### **Configuración de mysqldump y gzip (Importante para Backups)**

#### Windows
```powershell
# Verificar que mysqldump está disponible
mysqldump --version

# Si no está en PATH, agregarlo:
# 1. Buscar la ruta de MySQL (ejemplo: C:\Program Files\MySQL\MySQL Server 8.0\bin)
# 2. Agregar a variables de entorno PATH
# 3. Reiniciar terminal

# Instalar gzip (si no está)
# Opción 1: Usando Git Bash (viene con Git for Windows)
# Opción 2: Descargar gzip para Windows y agregar a PATH
```

#### Linux/Mac
```bash
# Verificar disponibilidad (generalmente vienen instalados)
mysqldump --version
gzip --version
```

---

## 🛠️ Instalación y Configuración

### **1. Clonar el Repositorio**

```bash
git clone https://github.com/tu-usuario/InventarioMunicipal.git
cd InventarioMunicipal
```

### **2. Configurar MySQL**

```sql
-- Crear base de datos
CREATE DATABASE inventario_municipal CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Crear usuario (opcional)
CREATE USER 'inventario_user'@'localhost' IDENTIFIED BY 'password_seguro';
GRANT ALL PRIVILEGES ON inventario_municipal.* TO 'inventario_user'@'localhost';
FLUSH PRIVILEGES;
```

### **3. Compilar el Proyecto**

```bash
mvn clean compile
```

### **4. Ejecutar la Aplicación**

```bash
mvn javafx:run
```

O usando el archivo batch (Windows):
```bash
run.bat
```

### **5. Configuración Inicial (Primera Vez)**

Al iniciar por primera vez, se mostrará un asistente:

1. **Configurar MySQL:**
   - Host: `localhost`
   - Puerto: `3306`
   - Base de datos: `inventario_municipal`
   - Usuario: `root` (o el creado)
   - Contraseña: tu contraseña de MySQL

2. **Crear Usuario Root:**
   - Username: `admin` (recomendado)
   - Nombre: Tu nombre
   - Contraseña: Una contraseña segura
   - Confirmar contraseña

3. **¡Listo!** Ya puedes iniciar sesión

---

## 👤 Primer Login

```
Usuario: admin (el que creaste)
Contraseña: tu contraseña
```

**Rol:** ROOT (acceso total al sistema)

---

## 📚 Primeros Pasos

### **1. Crear Roles y Permisos**

Los roles ya están creados automáticamente:
- ROOT
- ADMIN
- GESTOR
- EDITOR
- OPERADOR
- CONSULTOR

### **2. Crear Usuarios**

1. Ir a **Usuarios** → **Nuevo**
2. Completar formulario:
   - Nombre
   - Username (único)
   - Contraseña
   - Rol
3. Click en **Crear**

### **3. Crear Personas**

1. Ir a **Personas** → **Nuevo**
2. Completar formulario:
   - Nombre completo
   - DNI (único)
   - Teléfono (opcional)
   - Email (opcional)
3. Click en **Crear**

### **4. Crear Productos**

1. Ir a **Inventario** → **Nuevo**
2. Completar formulario:
   - **Código**: Generado automáticamente (EAN-13)
     - Click en 🎲 para generar código aleatorio
     - O ingresa código manualmente
   - **Nombre** (obligatorio)
   - **Descripción** (opcional)
   - **Unidad** (obligatorio): Unidad, Caja, Paquete, Kg, L, etc.
   - **Stock actual** (obligatorio)
   - **Stock mínimo** (obligatorio)
3. Click en **Crear**
4. **Descargar códigos** (opcional):
   - Click en **Code128** para descargar código de barras
   - Click en **QR** para descargar código QR
   - Usar estos códigos para etiquetar productos

### **5. Registrar Transacciones Multi-Producto**

#### **Ingreso:**
1. Ir a **Ingresos** → **Nuevo Ingreso**
2. **Si es devolución de préstamo:**
   - Marcar checkbox **"Es devolución"**
   - Escribir código de préstamo (autocompletado)
   - Sistema carga automáticamente:
     - Persona del préstamo
     - Todos los productos y cantidades
   - Seleccionar tipo de devolución
   - Agregar notas si es necesario
3. **Si es ingreso normal:**
   - Seleccionar persona (opcional)
   - Seleccionar fecha
   - Agregar productos:
     - Buscar producto (por nombre, ID o código)
     - Usar escáner de código de barras/QR
     - Ingresar cantidad
     - Notas (opcional)
     - Click en **Agregar**
   - Repetir para más productos
4. Notas generales (opcional)
5. Click en **Guardar Ingreso**

#### **Egreso:**
1. Ir a **Egresos** → **Nuevo Egreso**
2. Mismo proceso que ingresos
3. **Invalidar egreso** (si es necesario):
   - Solo usuarios con permiso INVALIDATE
   - Click en botón de invalidar en la tabla
   - Confirmar acción

#### **Préstamo:**
1. Ir a **Préstamos** → **Nuevo Préstamo**
2. Seleccionar persona (obligatorio)
3. Seleccionar fecha de préstamo
4. **Fecha esperada de devolución** (obligatorio)
5. Agregar productos:
   - Buscar producto
   - Ingresar cantidad
   - Notas (opcional)
   - Click en **Agregar**
6. Notas generales del préstamo
7. Click en **Guardar Préstamo**
8. **Sistema genera código único**: P-XX20251128-001
9. **Imprimir recibo** (opcional):
   - Seleccionar tipo de impresora
   - Imprimir en térmica o normal
   - Guardar como PDF

#### **Estados de Préstamo:**
- **Pendiente**: Préstamo activo, esperando devolución
- **Devuelto**: Productos devueltos
- **Pagado**: Compensado económicamente
- **Reemplazado**: Sustituido por otro producto

#### **Filtros disponibles:**
- Todos los préstamos
- Solo pendientes
- Solo devueltos
- Invalidados

---

## 📊 Generar Reportes

### **Reportes de Usuarios**

1. Ir a **Reportes**
2. Sección **Reportes de Usuarios**
3. Click en:
   - **PDF** - Todos los usuarios (excluyendo root)
   - **CSV** - Exportar a Excel
   - **PDF** - Usuarios por rol
   - **CSV** - Exportar por rol

### **Reportes de Inventario**

1. Ir a **Reportes**
2. Sección **Reportes de Inventario**
3. Click en:
   - **PDF/CSV** - Inventario completo
   - **PDF/CSV** - Stock bajo
   - **PDF/CSV** - Sin stock
   - **PDF/CSV** - Por unidad

### **Reportes de Movimientos**

1. Ir a **Reportes**
2. Sección **Movimientos de Inventario**
3. Opciones:
   - Todos los movimientos
   - Por rango de fechas
   - Por tipo de transacción

---

## 🖨️ Impresión de Recibos de Préstamos

### **Requisitos**
- Impresora térmica 58mm o 80mm (para recibos térmicos)
- O cualquier impresora normal (para PDF/papel normal)

### **Imprimir Recibo**

1. Ir a **Préstamos**
2. Seleccionar un préstamo de la tabla
3. Click en botón **Imprimir** (🖨️)
4. Seleccionar opciones:
   - **Impresora Térmica**: Formato compacto para papel térmico
   - **Impresora Normal**: Formato A4 profesional
   - **Guardar PDF**: Guardar archivo para imprimir después
5. Seleccionar impresora disponible
6. Click en **Imprimir** o **Guardar**

### **Contenido del Recibo**
- Logo de la institución (solo en PDF)
- Código de barras del préstamo
- Información de la persona
- Fecha del préstamo y fecha esperada de devolución
- Lista de productos con cantidades
- Notas generales y específicas
- Usuario que generó el recibo
- Espacio para firma

---

## 🗑️ Gestión de Datos Eliminados (Soft Delete)

El sistema usa **eliminación lógica** en lugar de eliminar permanentemente.

### **Eliminar Usuarios/Personas/Productos**

1. Seleccionar registro de la tabla
2. Click en **Eliminar**
3. Confirmar acción
4. **El registro se marca como eliminado** (status = 0)
5. **No aparece en listas normales** (solo si tienes permiso RESTORE)

### **Restaurar Datos Eliminados**

**Requisito:** Permiso `RESTORE` (Solo ADMIN y ROOT)

1. Los registros eliminados aparecen en la tabla con badge "Eliminado"
2. Click en botón **Restaurar** (↻)
3. Confirmar acción
4. Registro vuelve a estado activo

### **Eliminación Permanente**

**Requisito:** Solo usuario ROOT

1. Seleccionar registro eliminado
2. Click en botón rojo **Eliminar Permanentemente**
3. **⚠️ ADVERTENCIA**: Esta acción no se puede deshacer
4. Confirmar acción
5. Registro se borra de la base de datos

### **Estados de Registros**
- **Activo** (badge verde): Registro normal
- **Inactivo/Eliminado** (badge rojo): Eliminación lógica
- **Invalidado** (badge naranja): Transacciones invalidadas

---

## 💾 Backups

### **Backups Automáticos**

El sistema crea backups automáticamente:
- ✅ Al iniciar sesión (una vez por día)
- ✅ Al cerrar la aplicación

**Ubicación:** `C:\Users\{usuario}\.inventario_municipal\backups\`

### **Backup Manual**

1. Ir a **Backups**
2. Click en **💾 Crear Backup**
3. Esperar confirmación
4. Ver en la lista

### **Restaurar Backup**

1. Ir a **Backups**
2. Seleccionar backup de la lista
3. Click en **Restaurar**
4. Confirmar
5. Reiniciar aplicación

### **Configurar Backups Automáticos**

1. Ir a **Backups**
2. Click en **⚙️ Backups Automáticos**
3. Ver información y estado
4. Habilitar/Deshabilitar según necesidad

---

## 📷 Usar Lector de Códigos

### **Configurar Lector**

1. Conectar lector USB o Bluetooth
2. Configurar para enviar **Enter** al final
3. Probar en Notepad (debe escribir y dar Enter)

### **Usar en Inventario**

1. Ir a **Inventario**
2. Campo de búsqueda tiene foco automático
3. Escanear código de barras del producto
4. Producto aparece automáticamente

### **Usar en Transacciones**

1. Ir a **Ingresos/Egresos/Préstamos**
2. Click en **Nuevo**
3. Campo de búsqueda de producto
4. Escanear código
5. Producto se selecciona automáticamente
6. Ingresar cantidad
7. Agregar más productos

---

## 🎨 Personalización

### **Cambiar Tamaño de Página**

En tablas con paginación:
1. Ver selector de tamaño (10, 25, 50, 100)
2. Seleccionar cantidad deseada
3. Tabla se actualiza automáticamente

### **Búsqueda Rápida**

- Escribe en el campo de búsqueda
- Espera 500ms (debounce)
- Resultados aparecen automáticamente
- No necesitas dar click en buscar

---

## 🔐 Gestión de Permisos

### **Ver Permisos de un Rol**

1. Ir a **Roles**
2. Seleccionar rol
3. Ver tabla de permisos
4. Checkboxes muestran permisos activos

### **Modificar Permisos**

1. Seleccionar rol
2. Check/uncheck permisos
3. Click en **Guardar Cambios**
4. Usuarios con ese rol se actualizan automáticamente

### **Permisos por Módulo**

```
- DASHBOARD: Ver estadísticas
- INVENTARIO: Gestión de productos
- INGRESOS: Registrar ingresos
- EGRESOS: Registrar egresos
- PRESTAMOS: Gestión de préstamos
- USUARIOS: Gestión de usuarios
- ROLES: Gestión de roles
- PERSONAS: Gestión de personas
- REPORTES: Generar y exportar
- BACKUPS: Crear y restaurar
- AUDITORIA: Ver historial
- CONFIGURACION: Ajustes del sistema
```

---

## 🆘 Solución de Problemas

### **No puedo conectar a MySQL**

1. Verificar que MySQL está corriendo
2. Verificar host, puerto, usuario, contraseña
3. Verificar que la base de datos existe
4. Verificar permisos del usuario

### **Error al crear backup**

1. Verificar que `mysqldump` está en PATH
2. En Windows: Agregar `C:\Program Files\MySQL\MySQL Server 8.0\bin` al PATH
3. Verificar que `gzip` está instalado
4. Reiniciar la aplicación

### **Usuario SYSTEM aparece en lista**

- Esto es un error que ya está corregido
- El usuario SYSTEM debe ser invisible
- Verificar que tienes la versión más reciente

### **Lector de códigos no funciona**

1. Verificar que el lector está configurado para enviar Enter
2. Probar en Notepad primero
3. Verificar que el campo de búsqueda tiene foco
4. Algunos lectores necesitan configuración especial

### **Reportes no se generan**

1. Verificar permisos (GENERATE, EXPORT)
2. Verificar que hay datos para el reporte
3. Ver logs en `logs/app.log`

---

## 📖 Documentación Adicional

- **[FEATURES.md](FEATURES.md)** - Lista completa de características
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Arquitectura del sistema
- **[DATABASE.md](DATABASE.md)** - Estructura de base de datos
- **[SECURITY.md](SECURITY.md)** - Seguridad y permisos
- **[PLAN_MEJORAS_BUGS.md](PLAN_MEJORAS_BUGS.md)** - Mejoras planificadas

---

## 💬 Soporte

Si encuentras problemas:
1. Revisar logs en `logs/app.log`
2. Consultar documentación técnica
3. Revisar issues en GitHub
4. Contactar al equipo de desarrollo

---

## ✅ Checklist de Configuración

```
☑️ MySQL instalado y configurado
☑️ Base de datos creada
☑️ Aplicación compilada sin errores
☑️ Configuración inicial completada
☑️ Usuario root creado
☑️ Primer login exitoso
☑️ Backups automáticos habilitados
☑️ mysqldump y gzip configurados
☑️ Lector de códigos probado (opcional)
```

---

**¡Listo para usar!** 🎉

**Fecha:** 24 de Noviembre de 2025  
**Versión:** 1.0-SNAPSHOT

