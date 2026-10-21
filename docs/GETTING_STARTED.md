# Instalación, ejecución y actualización

Revisado el 21 de octubre de 2026 para SICAM 1.0-SNAPSHOT.

## Entorno verificado

- JDK 25 y Maven 3.9.11. Compruebe tanto `java -version` como `mvn -version`: PATH y JAVA_HOME pueden apuntar a instalaciones distintas.
- JavaFX 25.0.1, resuelto por Maven. El nivel de compilación `source/target=14` no determina la versión mínima de las dependencias de ejecución; no use esa configuración como prueba de compatibilidad con Java 14/21.
- MySQL Server 8.0 o superior y una base dedicada con `utf8mb4`.
- `mysqldump` y `gzip` en PATH si se utilizarán respaldos.
- Entorno gráfico para ejecutar la aplicación y las pruebas de interfaz JavaFX.

El entorno de trabajo validado es Windows. El código contempla comandos de respaldo para otros sistemas, pero esa presencia no acredita una distribución probada en cada plataforma.

## Instalación nueva

```bash
git clone https://github.com/JoshuaMc1/SICAM.git
cd SICAM
mvn clean test
mvn javafx:run
```

Antes de iniciar, un administrador de MySQL debe preparar una base vacía para SICAM, por ejemplo:

```sql
CREATE DATABASE sicam CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Use un usuario MySQL con acceso a esa base y permisos suficientes para las migraciones. Los eventos de vencimiento necesitan además `EVENT`; activar el programador global puede requerir privilegios administrativos del servidor. No confunda el usuario de MySQL con las cuentas ROOT/ADMIN de la aplicación.

El asistente inicial solicita host, puerto, base, usuario y contraseña de MySQL. Las migraciones crean el esquema y el catálogo inicial de roles/permisos. El asistente permite crear la cuenta ROOT; la cuenta SYSTEM es técnica y no sirve para iniciar sesión. Las cuentas adicionales creadas/restablecidas desde Usuarios deben cambiar su contraseña cuando corresponda.

En sesiones posteriores se muestra un módulo permitido por el rol; no siempre se abre Inicio. Un usuario sin DASHBOARD.READ puede entrar directamente en Inventario si tiene acceso a ese módulo.

## Compilación y distribución

```bash
mvn clean package
mvn javafx:run
```

El empaquetado utiliza Maven Shade y declara `com.joshuamc1.sicam.App` como clase principal. El JAR se genera bajo `target/`; la distribución debe incluir un entorno Java compatible y las bibliotecas nativas JavaFX para la plataforma correspondiente. Compruebe la ejecución de la distribución antes de entregarla; compilar con target 14 no la convierte en una distribución compatible con Java 14.

## Ubicaciones actuales

| Dato | Ubicación predeterminada |
| --- | --- |
| Configuración de MySQL | `~/.sicam/mysql.config` |
| Respaldos | `~/.sicam/backups/` |
| Control de respaldos automáticos | `~/.sicam/.cache/backup_control.properties` |
| Logs | `logs/app.log`, relativo al directorio de ejecución |
| Evidencias de QA locales | `qa-evidence/`, `evidence/` y `docs/qa/` |

En Windows, `~` es normalmente `%USERPROFILE%`. AppConfig permite cambiar la carpeta de configuración con `app.secure.dir` o `APP_SECURE_DIR`. El código actual de respaldos calcula su carpeta desde el nombre final de esa ruta bajo el perfil del usuario; no presuponga que conserva todo el directorio personalizado. El control automático utiliza la carpeta completa de AppConfig y guarda `.cache/backup_control.properties` dentro de ella. Si el archivo nuevo no existe y se encuentra el de `~/.inventario_municipal/.cache/`, copia sus opciones y fechas a la nueva ubicación, sin borrar el original. Si ya existe configuración nueva, esta tiene prioridad.

El archivo de conexión es un archivo Properties con valores cifrados, no un JSON. No lo publique ni lo incluya en evidencias.

## Respaldos

1. Verifique `mysqldump --version` y `gzip --version` en el entorno desde el que inicia SICAM.
2. Cree un respaldo manual desde Backup con BACKUP.CREATE y compruebe el archivo resultante.
3. Habilite los automáticos si desea el intento de respaldo al primer inicio del día y al cierre normal. Por defecto están deshabilitados.
4. Compruebe la descompresión y restauración en una base separada con herramientas de MySQL. El módulo no implementa restauración.

Un cierre forzado, fallo del proceso o corte de energía no garantiza un respaldo de cierre. Los respaldos automáticos se asocian a SYSTEM en la tabla `backups`; su creación automática no genera adicionalmente un evento de auditoría en BackupService.

## Actualización de una instalación con datos

No trate esta versión como una actualización de datos automáticamente segura. [DATABASE.md](DATABASE.md) describe las migraciones reales y sus limitaciones:

- `schema_migrations` registra nombres de archivos aplicados; editar un archivo ya aplicado no vuelve a ejecutarlo.
- La matriz corregida de permisos se incorporó a las migraciones originales. Una instalación anterior no recibe esas asignaciones solo reemplazando el JAR.
- `20251124120000_migrate_to_multiproduct_transactions.sql` elimina y recrea `transactions` y `transaction_items`; no migra los datos anteriores.
- Regenerar toda la base, incluida `schema_migrations`, solo corresponde a una base de pruebas desechable y con respaldo cuando proceda. No es el procedimiento de actualización de una instalación operativa.

Para una base operativa: obtenga y compruebe un respaldo, pruebe sobre una copia y prepare una migración de datos/permisos específica. Esta revisión documental no implementa esa migración ni ejecuta cambios en bases de datos.

## Diagnóstico

| Síntoma | Comprobaciones |
| --- | --- |
| La aplicación no inicia | JAVA_HOME, `java -version`, `mvn -version`, JavaFX y logs |
| No conecta a MySQL | Servicio, host/puerto, base, credenciales y conectividad; no desactive el firewall como solución general |
| Falla una migración | Archivo señalado, esquema real, permisos MySQL y `schema_migrations`; no elimine datos como primer intento |
| No se ven botones o módulos | Permisos vigentes y restricciones de identidad de rol en [AUTORIZACION.md](AUTORIZACION.md) |
| Reporte de actividad vacío | Compruebe si el usuario registró transacciones; sus acciones de gestión se consultan en Auditoría |
| No se genera un reporte | Permisos de Reportes y de los módulos consultados, filtros y logs |
| Falla un respaldo | PATH de mysqldump/gzip, conexión, directorio de salida y logs |
| Vencidos no se guardan automáticamente | Programador, permisos EVENT y zona horaria del servidor; consulte [EVENTOS_AUTOMATICOS.md](EVENTOS_AUTOMATICOS.md) |
| Advertencias de fuentes PDF | Verifique que el empaquetado conserve `fonts/DejaVuSans.ttf` y `fonts/DejaVuSans-Bold.ttf`; los exportadores de Reportes y Auditoría usan esas fuentes |

Al cambiar la configuración de MySQL, utilice el flujo de configuración del sistema y conserve una copia privada de la configuración si necesita diagnóstico. No es necesario borrar la base para corregir una conexión.
