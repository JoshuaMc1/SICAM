@echo off
cd /d "%~dp0"
SET JAVA_HOME=%~dp0jdk
SET PATH=%JAVA_HOME%\bin;%PATH%
start "" "%JAVA_HOME%\bin\javaw.exe" --module-path "%~dp0javafx-sdk\lib" --add-modules javafx.controls,javafx.fxml -jar "%~dp0SICAM-1.0-SNAPSHOT.jar"