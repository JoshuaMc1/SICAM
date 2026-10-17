 ================================================================================
    SICAM - Sistema de Control de Activos Municipales
================================================================================

Sistema de gestión de inventario desarrollado en JavaFX con base de datos
MySQL para municipalidades.

Versión: 1.0-SNAPSHOT
Desarrollado por: Joshua MC
Fecha: Noviembre 2025

================================================================================
    CARACTERÍSTICAS PRINCIPALES
================================================================================

  ✓ Gestión de Inventario - Control completo de productos
  ✓ Transacciones - Registro de ingresos, egresos y préstamos
  ✓ Gestión de Usuarios - Sistema de roles y permisos
  ✓ Auditoría - Registro completo de todas las acciones
  ✓ Reportes - Generación de reportes en PDF
  ✓ Base de Datos MySQL - Almacenamiento seguro y confiable
  ✓ Interfaz Intuitiva - Diseño moderno con JavaFX

================================================================================
    REQUISITOS DEL SISTEMA
================================================================================

PARA DESARROLLO:
  - Java JDK 14 o superior
  - Maven 3.6+
  - MySQL Server 8.0+
  - IDE compatible (IntelliJ IDEA, Eclipse, etc.)

PARA PRODUCCIÓN:
  - MySQL Server 8.0+
  - Sistema Operativo: Windows 10/11 (x64)
  - RAM: Mínimo 4GB
  - Espacio en disco: 500MB

================================================================================
    INSTALACIÓN Y USO
================================================================================

--------------------------------------------------------------------------------
1. INSTALACIÓN PARA DESARROLLO
--------------------------------------------------------------------------------

PASO 1: Clonar el Repositorio
  
  git clone https://github.com/JoshuaMc1/InventarioMunicipal
  cd InventarioMunicipal

PASO 2: Configurar MySQL

  CREATE DATABASE inventario_municipal
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

  CREATE USER 'inventario_user'@'localhost' IDENTIFIED BY 'tu_contraseña';
  GRANT ALL PRIVILEGES ON inventario_municipal.* TO 'inventario_user'@'localhost';
  FLUSH PRIVILEGES;

PASO 3: Compilar el Proyecto

  mvn clean compile

PASO 4: Ejecutar la Aplicación

  mvn javafx:run

--------------------------------------------------------------------------------
2. COMPILAR PARA DISTRIBUCIÓN
--------------------------------------------------------------------------------

GENERAR JAR:
  
  mvn clean package

  El JAR se generará en: target/SYCAM-1.0-SNAPSHOT.jar

EJECUTAR JAR:

  java -jar target/SYCAM-1.0-SNAPSHOT.jar

--------------------------------------------------------------------------------
3. CONFIGURACIÓN INICIAL (PRIMERA VEZ)
--------------------------------------------------------------------------------

PASO 1: Configuración de MySQL
  Al iniciar por primera vez, el sistema solicitará:
  - Host del servidor MySQL
  - Puerto (por defecto: 3306)
  - Nombre de la base de datos
  - Usuario y contraseña MySQL

PASO 2: Crear Usuario ROOT
  Después de configurar MySQL, crear el usuario principal:
  - Nombre completo
  - Nombre de usuario
  - Contraseña segura

================================================================================
    ROLES Y PERMISOS
================================================================================

  ROL          DESCRIPCIÓN                      PERMISOS
  -----------  ------------------------------   ------------------------------
  ROOT         Superadministrador               Acceso total al sistema
  Admin        Administrador                    Gestión completa excepto
                                                configuraciones críticas
  Operator     Operador                         Operaciones diarias del
                                                inventario
  Auditor      Auditor                          Solo lectura y generación
                                                de reportes
  Guest        Invitado                         Acceso limitado de consulta

================================================================================
    ESTRUCTURA DEL PROYECTO
================================================================================

SYCAM/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/joshuamc1/sycam/
│   │   │       ├── config/           # Configuraciones
│   │   │       ├── controllers/      # Controladores JavaFX
│   │   │       ├── core/             # Utilidades centrales
│   │   │       ├── models/           # Modelos de datos
│   │   │       ├── repositories/     # Acceso a datos
│   │   │       ├── services/         # Lógica de negocio
│   │   │       └── App.java          # Clase principal
│   │   └── resources/
│   │       ├── css/                  # Estilos
│   │       ├── fxml/                 # Vistas JavaFX
│   │       ├── images/               # Recursos gráficos
│   │       └── migrations/           # Migraciones SQL
│   └── test/                         # Tests unitarios
├── pom.xml                           # Configuración Maven
├── run.bat                           # Script de ejecución
└── README.txt                        # Este archivo

================================================================================
    SEGURIDAD
================================================================================

  - Contraseñas: Hasheadas con BCrypt
  - Credenciales MySQL: Cifradas con AES-256
  - Auditoría: Registro completo de acciones
  - Sesiones: Control de sesiones de usuario

================================================================================
    BASE DE DATOS
================================================================================

TABLAS PRINCIPALES:
  - users         : Usuarios del sistema
  - people        : Personas (para préstamos)
  - products      : Productos del inventario
  - transactions  : Transacciones (entrada/salida/préstamo)
  - audit         : Registro de auditoría

MIGRACIONES:
  Las migraciones se ejecutan automáticamente en la configuración inicial.
  Ubicación: src/main/resources/migrations/

================================================================================
    DEPENDENCIAS PRINCIPALES
================================================================================

  - JavaFX 25.0.1          : Framework UI
  - ActiveJDBC 3.5         : ORM
  - MySQL Connector/J 8.3.0: Driver MySQL
  - BCrypt 0.4             : Hashing de contraseñas
  - Logback 1.4.11         : Logging
  - Apache PDFBox 2.0.29   : Generación de PDFs

================================================================================
    SOLUCIÓN DE PROBLEMAS
================================================================================

ERROR: "Cannot connect to MySQL"
  Solución:
  - Verificar que MySQL esté ejecutándose
  - Verificar credenciales
  - Revisar configuración del firewall

ERROR: "Failed to execute migrations"
  Solución:
  - Verificar permisos del usuario MySQL
  - Asegurar que la base de datos esté vacía

ERROR: "Database not configured"
  Solución:
  - Eliminar configuración: %USERPROFILE%\.sycam\mysql.config
  - Reiniciar aplicación

================================================================================
    ACTUALIZACIÓN DEL SISTEMA
================================================================================

Para actualizar a una nueva versión:
  1. Realizar backup de la base de datos
  2. Instalar nueva versión
  3. Las migraciones nuevas se aplicarán automáticamente

================================================================================
    SOPORTE Y LICENCIA
================================================================================

SOPORTE:
  - GitHub: https://github.com/JoshuaMc1/InventarioMunicipal
  - Issues: https://github.com/JoshuaMc1/InventarioMunicipal/issues

LICENCIA:
  MIT License - Ver archivo LICENSE.txt para más detalles
