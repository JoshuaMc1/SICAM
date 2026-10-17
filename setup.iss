; ------------------------------------------------------------
; Instalador para SICAM v2.0
; Incluye JDK, JavaFX, .bat de lanzamiento y MySQL opcional
; Versión mejorada con rutas relativas y validaciones
; ------------------------------------------------------------

; Definir constantes para rutas relativas
#define MyAppName "SICAM"
#define MyAppVersion "1.0"
#define MyAppPublisher "JoshuaMc1"
#define MyAppURL "https://github.com/JoshuaMc1/InventarioMunicipal"
#define MyAppExeName "SICAM-1.0-SNAPSHOT.jar"
#define MyAppLauncher "run.bat"

[Setup]
; Información básica de la aplicación
AppId={{8B2F4A3D-9C1E-4F6A-B2D3-1A4E5F6C7D8E}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}/issues
AppUpdatesURL={#MyAppURL}/releases

; Configuración de instalación
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
LicenseFile=LICENSE.txt
InfoBeforeFile=README.txt
OutputDir=dist
OutputBaseFilename={#MyAppName}Setup-{#MyAppVersion}
SetupIconFile=src\main\resources\images\favicon.ico
UninstallDisplayIcon={app}\favicon.ico

; Configuración de compresión y seguridad
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern polar includetitlebar
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

; Requisitos mínimos
MinVersion=10.0.17763

; ----------------------------------------------------------------
; Tipos de instalación
[Types]
Name: "full"; Description: "Instalación completa (incluye MySQL Installer)"
Name: "compact"; Description: "Instalación compacta (solo aplicación)"
Name: "custom"; Description: "Instalación personalizada"; Flags: iscustom

; ----------------------------------------------------------------
; Componentes
[Components]
Name: "core"; Description: "Archivos principales de {#MyAppName}"; Types: full compact custom; Flags: fixed
Name: "jdk"; Description: "Java Development Kit 25"; Types: full compact custom; Flags: fixed
Name: "javafx"; Description: "JavaFX Runtime"; Types: full compact custom; Flags: fixed
Name: "gzip"; Description: "GZIP (requerido para backups)"; Types: full compact custom; Flags: fixed
Name: "shortcuts"; Description: "Accesos directos"; Types: full compact; Flags: checkablealone
Name: "vcredist"; Description: "Visual C++ Redistributable (requerido para MySQL)"; Types: full custom
Name: "mysql"; Description: "MySQL (herramienta de gestión de BD)"; Types: full custom

; ----------------------------------------------------------------
; Archivos a copiar
[Files]
; Aplicación principal
Source: "D:\Java\InventarioMunicipal\target\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion; Components: core

; JDK 25 - usando ruta absoluta
Source: "E:\sdks\Java\openjdk-25.0.1\*"; DestDir: "{app}\jdk"; Flags: recursesubdirs createallsubdirs ignoreversion; Components: jdk

; JavaFX SDK - usando ruta absoluta
Source: "E:\sdks\Java\javafx-sdk-25.0.1\*"; DestDir: "{app}\javafx-sdk"; Flags: recursesubdirs createallsubdirs ignoreversion; Components: javafx

; Archivos de la aplicación
Source: "D:\Java\InventarioMunicipal\src\main\resources\images\favicon.ico"; DestDir: "{app}"; Flags: ignoreversion; Components: core
Source: "D:\Java\InventarioMunicipal\{#MyAppLauncher}"; DestDir: "{app}"; DestName: "{#MyAppLauncher}"; Flags: ignoreversion; Components: core

; Archivos de documentación
Source: "D:\Java\InventarioMunicipal\README.txt"; DestDir: "{app}"; Flags: ignoreversion; Components: core
Source: "D:\Java\InventarioMunicipal\LICENSE.txt"; DestDir: "{app}"; Flags: ignoreversion; Components: core
Source: "D:\Java\InventarioMunicipal\docs\*"; DestDir: "{app}\docs"; Flags: recursesubdirs createallsubdirs ignoreversion; Components: core

; GZIP (obligatorio para backups)
Source: "D:\Instaladores\gzip-1.3.12-1\*"; DestDir: "{app}\gzip"; Flags: recursesubdirs createallsubdirs ignoreversion; Components: gzip

; Visual C++ Redistributable (necesario para MySQL)
Source: "D:\Instaladores\VC_redist.x64.exe"; DestDir: "{app}\vcredist"; Flags: ignoreversion; Components: vcredist

; MySQL Workbench (opcional)
Source: "D:\Instaladores\mysql-installer-community-8.0.msi"; DestDir: "{app}\mysql"; Flags: ignoreversion; Components: mysql

; Crear carpeta de logs se hará automáticamente

; ----------------------------------------------------------------
; Crear accesos directos
[Icons]
; Menú Inicio
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppLauncher}"; WorkingDir: "{app}"; IconFilename: "{app}\favicon.ico"; Components: shortcuts
Name: "{group}\Desinstalar {#MyAppName}"; Filename: "{uninstallexe}"; Components: shortcuts
Name: "{group}\Documentación"; Filename: "{app}\README.txt"; Components: shortcuts

; Escritorio (opcional)
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppLauncher}"; WorkingDir: "{app}"; IconFilename: "{app}\favicon.ico"; Tasks: desktopicon; Components: shortcuts

; ----------------------------------------------------------------
; Tareas adicionales
[Tasks]
Name: "desktopicon"; Description: "Crear icono en el &escritorio"; GroupDescription: "Iconos adicionales:"
Name: "quicklaunchicon"; Description: "Crear icono en la barra de &inicio rápido"; GroupDescription: "Iconos adicionales:"; Flags: unchecked; OnlyBelowVersion: 6.1

; ----------------------------------------------------------------
; Entradas del registro
[Registry]
Root: HKLM; Subkey: "Software\{#MyAppPublisher}\{#MyAppName}"; ValueType: string; ValueName: "InstallPath"; ValueData: "{app}"; Flags: uninsdeletekey
Root: HKLM; Subkey: "Software\{#MyAppPublisher}\{#MyAppName}"; ValueType: string; ValueName: "Version"; ValueData: "{#MyAppVersion}"; Flags: uninsdeletekey
Root: HKLM; Subkey: "Software\{#MyAppPublisher}\{#MyAppName}"; ValueType: string; ValueName: "GzipPath"; ValueData: "{app}\gzip"; Flags: uninsdeletekey
Root: HKLM; Subkey: "Software\Microsoft\Windows\CurrentVersion\App Paths\{#MyAppLauncher}"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppLauncher}"; Flags: uninsdeletekey
Root: HKLM; Subkey: "Software\Microsoft\Windows\CurrentVersion\App Paths\{#MyAppLauncher}"; ValueType: string; ValueName: "Path"; ValueData: "{app}"; Flags: uninsdeletekey

; Agregar gzip al PATH del sistema (CORREGIDO - ya no borra el PATH)
Root: HKLM; Subkey: "SYSTEM\CurrentControlSet\Control\Session Manager\Environment"; ValueType: expandsz; ValueName: "Path"; ValueData: "{olddata};{app}\gzip\bin"; Check: NeedsAddPath('{app}\gzip\bin')

; ----------------------------------------------------------------
; Ejecutar al finalizar instalación
[Run]
; Instalar Visual C++ Redistributable si fue seleccionado y no está instalado
Filename: "{app}\vcredist\VC_redist.x64.exe"; Parameters: "/install /passive /norestart"; Components: vcredist; Flags: postinstall skipifsilent waituntilterminated; Description: "Instalar Visual C++ Redistributable"; Check: not IsVCRedistInstalled

; Instalar MySQL Workbench si fue seleccionado (modo interactivo)
Filename: "msiexec.exe"; Parameters: "/i ""{app}\mysql\mysql-installer-community-8.0.msi"""; Components: mysql; Flags: postinstall skipifsilent waituntilterminated; Description: "Instalar MySQL"; Check: CheckMySQLRequirements

; Ejecutar la aplicación
Filename: "{app}\{#MyAppLauncher}"; Flags: postinstall skipifsilent shellexec; Description: "Ejecutar {#MyAppName} ahora"; WorkingDir: "{app}"

; Abrir documentación
Filename: "{app}\README.txt"; Flags: nowait postinstall skipifsilent shellexec unchecked; Description: "Ver documentación"

; ----------------------------------------------------------------
; Archivos a eliminar en desinstalación
[UninstallDelete]
Type: files; Name: "{app}\*.log"
Type: files; Name: "{app}\logs\*.log"
Type: dirifempty; Name: "{app}\logs"
Type: files; Name: "{app}\*.tmp"
Type: dirifempty; Name: "{app}\gzip"
Type: dirifempty; Name: "{app}\vcredist"
Type: dirifempty; Name: "{app}\mysql"

; ----------------------------------------------------------------
; Funciones de código Pascal para validaciones
[Code]
// Función para verificar si Visual C++ Redistributable está instalado
function IsVCRedistInstalled(): Boolean;
var
  Version: String;
begin
  // Verificar si está instalado VC++ Redistributable 2015-2022 x64
  Result := RegQueryStringValue(HKLM, 'SOFTWARE\Microsoft\VisualStudio\14.0\VC\Runtimes\x64', 'Version', Version);
  if not Result then
    Result := RegQueryStringValue(HKLM, 'SOFTWARE\WOW6432Node\Microsoft\VisualStudio\14.0\VC\Runtimes\x64', 'Version', Version);
  
  if Result then
    Log('Visual C++ Redistributable detectado: ' + Version)
  else
    Log('Visual C++ Redistributable no encontrado');
end;

// Función para verificar si una ruta ya existe en PATH
function NeedsAddPath(Param: string): Boolean;
var
  OrigPath: string;
  ParamExpanded: string;
begin
  // Leer el PATH actual del sistema
  if not RegQueryStringValue(HKLM, 'SYSTEM\CurrentControlSet\Control\Session Manager\Environment', 'Path', OrigPath) then
  begin
    Result := True;
    Exit;
  end;
  
  // Expandir la ruta del parámetro
  ParamExpanded := ExpandConstant(Param);
  
  // Normalizar las rutas para comparación (sin distinguir mayúsculas/minúsculas)
  OrigPath := Uppercase(OrigPath);
  ParamExpanded := Uppercase(ParamExpanded);
  
  // Buscar si la ruta ya existe en PATH (con diferentes formatos)
  Result := (Pos(';' + ParamExpanded + ';', ';' + OrigPath + ';') = 0) and
            (Pos(';' + ParamExpanded, ';' + OrigPath) = 0) and
            (Pos(ParamExpanded + ';', OrigPath + ';') = 0);
  
  if Result then
    Log('Agregando al PATH: ' + ParamExpanded)
  else
    Log('La ruta ya existe en PATH: ' + ParamExpanded);
end;

// Función para verificar requisitos antes de instalar MySQL
function CheckMySQLRequirements(): Boolean;
begin
  Result := True;
  
  // Verificar si VC++ Redistributable está instalado
  if not IsVCRedistInstalled() then
  begin
    MsgBox('ADVERTENCIA: MySQL requiere Visual C++ Redistributable x64.' + #13#10 + 
           'Se recomienda instalarlo primero seleccionando el componente correspondiente.' + #13#10 + #13#10 +
           'Si continúa sin instalarlo, MySQL podría no funcionar correctamente.', 
           mbInformation, MB_OK);
    Result := MsgBox('¿Desea continuar con la instalación de MySQL de todos modos?', mbConfirmation, MB_YESNO) = IDYES;
  end;
end;

function InitializeSetup(): Boolean;
var
  OrigPath: string;
begin
  Result := True;
  
  // Hacer backup del PATH antes de comenzar (precaución adicional)
  if RegQueryStringValue(HKLM, 'SYSTEM\CurrentControlSet\Control\Session Manager\Environment', 'Path', OrigPath) then
  begin
    RegWriteStringValue(HKLM, 'Software\{#MyAppPublisher}\{#MyAppName}', 'PathBackup', OrigPath);
    Log('Backup del PATH creado exitosamente');
  end;
  
  // Verificar si hay una instalación previa
  if RegKeyExists(HKLM, 'Software\{#MyAppPublisher}\{#MyAppName}') then
  begin
    if MsgBox('Se detectó una instalación previa. ¿Desea continuar?', mbConfirmation, MB_YESNO) = IDNO then
    begin
      Result := False;
      Exit;
    end;
  end;
  
  // Mostrar información sobre los componentes
  Log('Iniciando instalación de {#MyAppName} v{#MyAppVersion}');
end;

function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := False;
  
  // Saltar página de licencia si no existe el archivo
  if (PageID = wpLicense) and not FileExists('LICENSE.txt') then
    Result := True;
end;

// Función para mostrar información post-instalación
procedure CurPageChanged(CurPageID: Integer);
begin
  if CurPageID = wpFinished then
  begin
    Log('Instalación finalizada exitosamente');
  end;
end;

// Función para limpiar el PATH durante la desinstalación
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  OrigPath: string;
  NewPath: string;
  PathToRemove: string;
begin
  if CurUninstallStep = usUninstall then
  begin
    PathToRemove := ExpandConstant('{app}\gzip\bin');
    
    // Leer el PATH actual
    if RegQueryStringValue(HKLM, 'SYSTEM\CurrentControlSet\Control\Session Manager\Environment', 'Path', OrigPath) then
    begin
      // Remover la ruta de gzip del PATH
      NewPath := OrigPath;
      StringChangeEx(NewPath, ';' + PathToRemove, '', True);
      StringChangeEx(NewPath, PathToRemove + ';', '', True);
      StringChangeEx(NewPath, PathToRemove, '', True);
      
      // Solo actualizar si hubo cambios
      if NewPath <> OrigPath then
      begin
        RegWriteExpandStringValue(HKLM, 'SYSTEM\CurrentControlSet\Control\Session Manager\Environment', 'Path', NewPath);
        Log('PATH actualizado durante desinstalación');
      end;
    end;
  end;
end;