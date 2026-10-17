# 🚀 Características del Sistema - Inventario Municipal

**Fecha de última actualización:** 28 de Noviembre de 2025

---

## 📦 Gestión de Inventario

### **Productos**
- ✅ CRUD completo de productos
- ✅ **Soft Delete:** Eliminación lógica (status = 0, deleted_at, deleted_by)
- ✅ **Restauración:** Solo usuarios con permiso RESTORE
- ✅ **Eliminación permanente:** Solo usuario ROOT
- ✅ Códigos de barras y QR únicos por producto
- ✅ Generación automática de códigos EAN-13
- ✅ Generación aleatoria de códigos con botón 🎲
- ✅ Descarga de códigos en formato PNG (Code128 y QR)
- ✅ Control de stock actual y stock mínimo
- ✅ Alertas de stock bajo
- ✅ Badge de estado (Activo/Eliminado) con colores
- ✅ Búsqueda por ID, nombre o código
- ✅ Compatible con lectores de códigos de barras/QR
- ✅ Paginación eficiente (25 registros por defecto, configurable)
- ✅ Unidades de medida configurables
- ✅ Validaciones de campos obligatorios con helper text
- ✅ Botón de crear habilitado solo si se cumplen validaciones

### **Transacciones Multi-Producto**
- ✅ **Ingresos:** Registro de entrada de múltiples productos
  - Soporte para devoluciones de préstamos
  - Autocompletado de productos y persona del préstamo
  - Tipos: Mismo Producto, Comprado/Pagado, Reemplazado, Otro
- ✅ **Egresos:** Registro de salida de múltiples productos
  - Invalidación de egresos (solo usuarios con permiso)
  - Badge de estado (Válido/Inválido)
- ✅ **Préstamos:** Gestión completa de productos prestados
  - Códigos únicos de préstamo (P-XX20251128-001)
  - Fecha de préstamo y fecha esperada de devolución
  - Estados: Pendiente, Devuelto, Pagado, Reemplazado
  - Historial completo de préstamos
  - Invalidación de préstamos
  - Badge de estado con colores
  - Filtros: Todos, Pendientes, Devueltos, Invalidados
  - Búsqueda por código de préstamo con autocompletado
- ✅ Asociación con personas (DNI, nombre, contacto)
- ✅ Usuarios responsables de cada transacción
- ✅ Notas generales y específicas por producto
- ✅ Cálculo automático de totales
- ✅ Actualización automática de stock
- ✅ Búsqueda por ID de transacción, producto, persona o código
- ✅ Paginación a nivel de repositorio (10, 25, 50, 100 registros)
- ✅ Búsqueda optimizada con debounce (500ms)
- ✅ Botón de refrescar en todas las vistas

---

## 👥 Gestión de Usuarios y Roles

### **Sistema de Roles (RBAC)**
- ✅ **ROOT:** Acceso total al sistema
- ✅ **ADMIN:** Administración general
- ✅ **GESTOR:** Gestión de inventario y transacciones
- ✅ **EDITOR:** Edición de productos y transacciones
- ✅ **OPERADOR:** Operaciones básicas
- ✅ **CONSULTOR:** Solo lectura
- ✅ **Permisos granulares:** 84 permisos (14 módulos x 6 acciones)
- ✅ **Nuevo permiso RESTORE:** Para restaurar datos eliminados
- ✅ CRUD de roles (solo ROOT y ADMIN)
- ✅ Asignación flexible de permisos por rol
- ✅ Validaciones de campos obligatorios

### **Usuarios**
- ✅ CRUD completo de usuarios
- ✅ **Soft Delete:** Eliminación lógica (status = 0)
- ✅ **Activar/Desactivar:** Toggle de estado sin eliminar
- ✅ **Eliminación permanente:** Solo ROOT (botón rojo separado)
- ✅ **Restauración:** Solo usuarios con permiso RESTORE
- ✅ Badge de estado (Activo/Inactivo) con colores
- ✅ Autenticación con BCrypt
- ✅ Roles y permisos granulares
- ✅ Usuario especial **SYSTEM** para operaciones automáticas
- ✅ Validaciones de username único en tiempo real
- ✅ Protección: No se puede crear usuario "SYSTEM"
- ✅ Username deshabilitado al editar (excepto ROOT)
- ✅ Búsqueda por username con debounce
- ✅ Paginación a nivel de repositorio
- ✅ Helper text para campos obligatorios

### **Personas**
- ✅ CRUD completo de personas
- ✅ **Soft Delete:** Eliminación lógica
- ✅ **Restauración:** Solo usuarios con permiso RESTORE
- ✅ **Eliminación permanente:** Solo ROOT
- ✅ Badge de estado (Activo/Eliminado)
- ✅ Registro de personas (empleados, beneficiarios)
- ✅ DNI único con validación en tiempo real
- ✅ Datos de contacto (teléfono, email)
- ✅ Máscara de teléfono (0000-0000)
- ✅ Validación de DNI duplicado
- ✅ Búsqueda por DNI o nombre con debounce
- ✅ Paginación a nivel de repositorio
- ✅ Helper text para campos obligatorios
- ✅ Botón de refrescar tabla

---

## 📊 Reportes

### **Reportes de Usuarios**
- ✅ Todos los usuarios (excluyendo root si no es root)
- ✅ Usuarios agrupados por rol
- ✅ Exportación en PDF y CSV

### **Reportes de Inventario**
- ✅ Inventario completo con stock
- ✅ Productos con stock bajo
- ✅ Productos sin stock
- ✅ Productos por unidad de medida
- ✅ Exportación en PDF y CSV

### **Reportes de Movimientos**
- ✅ Todos los movimientos de inventario
- ✅ Movimientos por rango de fechas
- ✅ Movimientos por tipo (ingreso, egreso, préstamo)
- ✅ Detalles de productos en cada transacción
- ✅ Exportación en PDF y CSV

### **Reportes de Auditoría**
- ✅ Exportación completa de auditoría
- ✅ Exportación por rango de fechas
- ✅ Diálogo de selección de opciones
- ✅ Columnas responsivas (usuario, acción, tabla, detalles)

### **Características de Reportes**
- ✅ Encabezado con logo del sistema
- ✅ Título y descripción del reporte
- ✅ Usuario que generó el reporte
- ✅ Fecha y hora de generación
- ✅ Columnas auto-adaptables
- ✅ Formato profesional en PDF
- ✅ CSV compatible con Excel

---

## 💾 Sistema de Backups

### **Backups Automáticos**
- ✅ Backup al iniciar la aplicación (una vez por día)
- ✅ Backup al cerrar la aplicación (siempre)
- ✅ Usuario SYSTEM como responsable
- ✅ Control mediante caché (`.cache/backup_control.properties`)
- ✅ Mensaje: "Copia de seguridad creada por el sistema"
- ✅ Sin auditoría (operación del sistema)
- ✅ Configuración habilitada/deshabilitada desde UI

### **Backups Manuales**
- ✅ Creación manual desde módulo Backups
- ✅ Usuario actual como responsable
- ✅ Mensaje personalizable
- ✅ Con auditoría completa
- ✅ Historial completo en tabla

### **Gestión de Backups**
- ✅ Compresión GZIP automática
- ✅ Listado paginado de backups
- ✅ Información de tamaño, fecha, usuario
- ✅ Restauración de backups
- ✅ Eliminación de backups antiguos
- ✅ Limpieza automática configurable
- ✅ Ubicación: `~/.inventario_municipal/backups/`

---

## 🖨️ Sistema de Impresión de Recibos

### **Impresión de Recibos de Préstamos**
- ✅ **Impresoras Térmicas (58mm y 80mm)**
  - Biblioteca: escpos-coffee
  - Formato compacto optimizado para papel térmico
  - Código de barras CODE128 integrado
  - Encabezado sin logo (optimizado para espacio)
  - Información del préstamo y persona
  - Lista de productos con cantidades
  - Notas generales y específicas por producto
  - Fecha esperada de devolución
  - Espaciado optimizado para ahorro de papel
  - Codificación CP850 para caracteres especiales

- ✅ **Impresoras Normales / PDF**
  - Biblioteca: iText 7
  - Formato A4 profesional
  - Logo de la institución en encabezado
  - Código de barras CODE128 (200x50px, centrado)
  - Título y descripción del préstamo
  - Información completa de la persona
  - Tabla de productos con columnas ajustables
  - Notas por producto y generales
  - Total de artículos prestados
  - Espacio para firma del beneficiario
  - Márgenes optimizados (15-20px entre secciones)
  - Diseño compacto sin desperdiciar papel

### **Características Generales**
- ✅ Detección automática de impresoras disponibles
- ✅ Selección de impresora desde diálogo
- ✅ Vista previa antes de imprimir
- ✅ Guardado de PDF para archivo
- ✅ Impresión en background (no bloquea UI)
- ✅ Diálogo de progreso durante impresión
- ✅ Manejo de errores con mensajes claros
- ✅ Soporte para múltiples productos por préstamo
- ✅ Información del usuario que genera el recibo

### **Opciones de Impresión**
- ✅ Imprimir en impresora térmica
- ✅ Imprimir en impresora normal
- ✅ Guardar como PDF
- ✅ Vista previa del documento
- ✅ Cancelar operación en cualquier momento

---

## 🔍 Búsqueda y Filtrado

### **Búsqueda Inteligente**
- ✅ Búsqueda en inventario (ID, nombre, código)
- ✅ Búsqueda en usuarios (username, nombre)
- ✅ Búsqueda en personas (DNI, nombre)
- ✅ Búsqueda en transacciones (ID, producto, persona)
- ✅ Debounce de 500ms para evitar consultas excesivas
- ✅ Búsqueda automática mientras se escribe

### **Soporte para Escáneres**
- ✅ Compatible con lectores de códigos de barras
- ✅ Compatible con lectores de códigos QR
- ✅ Detección automática de Enter
- ✅ Búsqueda exacta por código
- ✅ Selección automática si hay un solo resultado
- ✅ Sin configuración adicional requerida

---

## 🔐 Seguridad

### **Autenticación**
- ✅ Contraseñas hasheadas con BCrypt
- ✅ Sesión segura con usuario actual
- ✅ Cierre de sesión con confirmación
- ✅ Usuario root inicial configurable

### **Autorización (RBAC)**
- ✅ 6 roles predefinidos
- ✅ 98 permisos granulares (14 módulos x 7 acciones)
- ✅ Control por módulo y acción:
  - CREATE: Crear nuevos registros
  - READ: Ver/listar registros
  - UPDATE: Editar registros existentes
  - DELETE: Eliminar registros (soft delete)
  - RESTORE: Restaurar registros eliminados (nuevo)
  - EXPORT: Exportar datos
  - GENERATE: Generar reportes
- ✅ Permiso especial INVALIDATE para transacciones
- ✅ Validación en UI (botones deshabilitados/ocultos)
- ✅ Validación en backend (servicios)
- ✅ Filtrado automático de datos según permisos

### **Auditoría**
- ✅ Registro de todas las acciones importantes
- ✅ Usuario, fecha, hora, tabla, acción, detalles
- ✅ Búsqueda por usuario, tabla, acción, fecha
- ✅ Exportación de auditoría completa
- ✅ Integridad referencial

### **Usuario SYSTEM**
- ✅ Usuario especial para operaciones automáticas
- ✅ No puede iniciar sesión (sin contraseña válida)
- ✅ Sin rol ni permisos
- ✅ Deshabilitado (activo = 0)
- ✅ No aparece en lista de usuarios
- ✅ No puede ser creado/editado manualmente
- ✅ Usado para backups automáticos

---

## 🎨 Interfaz de Usuario

### **Dashboard**
- ✅ Estadísticas en tiempo real
- ✅ Total de productos, usuarios, transacciones
- ✅ Productos con stock bajo
- ✅ Transacciones recientes
- ✅ Permisos dinámicos según rol

### **Navegación**
- ✅ Sidebar con módulos accesibles según permisos
- ✅ Breadcrumbs de navegación
- ✅ ScrollPane para pantallas pequeñas
- ✅ Diseño responsive

### **Formularios**
- ✅ Validaciones en tiempo real
- ✅ Helper text para campos obligatorios (rojo)
- ✅ Botones deshabilitados hasta completar campos
- ✅ Máscaras de entrada (teléfono)
- ✅ Generación automática de códigos
- ✅ ComboBox con búsqueda optimizada

### **Tablas**
- ✅ Paginación configurable
- ✅ Búsqueda en columnas
- ✅ Ordenamiento por columnas
- ✅ Acciones por fila (editar, eliminar)
- ✅ Botón de refrescar
- ✅ Indicador de página actual

---

## 🛠️ Tecnologías

### **Backend**
- ✅ Java 21
- ✅ JavaFX 25.0.1
- ✅ ActiveJDBC 3.5-j11
- ✅ MySQL 8.0+
- ✅ BCrypt para contraseñas
- ✅ Apache PDFBox para reportes PDF
- ✅ ZXing para códigos de barras/QR

### **Base de Datos**
- ✅ MySQL con InnoDB
- ✅ Sistema de migraciones
- ✅ Integridad referencial
- ✅ Índices optimizados
- ✅ Transacciones ACID

### **Build**
- ✅ Maven 3.9.11
- ✅ Compilación con Java 14
- ✅ Empaquetado con dependencies
- ✅ Profile de producción

---

## 📱 Requisitos del Sistema

### **Desarrollo**
- Java JDK 21+
- Maven 3.9+
- MySQL 8.0+
- mysqldump (incluido con MySQL)
- gzip (incluido en Linux/Mac, disponible en Windows)

### **Producción**
- Java JRE 21+
- MySQL 8.0+
- mysqldump configurado
- gzip configurado
- 2GB RAM mínimo
- 1GB espacio en disco

---

## 🎯 Próximas Mejoras

### **Planificadas (docs/PLAN_MEJORAS_BUGS.md)**
- Validaciones mejoradas en formularios
- Búsqueda automática sin botón buscar
- Máscaras de entrada adicionales
- Mejoras en el módulo "Acerca de"
- Optimizaciones de carga

---

## ✅ Estado del Proyecto

```
✅ Sistema funcional completo
✅ Gestión de inventario
✅ Transacciones multi-producto
✅ Sistema de roles y permisos
✅ Reportes PDF y CSV
✅ Backups automáticos
✅ Códigos de barras/QR
✅ Soporte para escáneres
✅ Auditoría completa
✅ Seguridad robusta
✅ Documentación completa
```

**Estado:** ✅ **PRODUCCIÓN-READY**

---

**Última actualización:** 24 de Noviembre de 2025  
**Versión:** 1.0-SNAPSHOT

