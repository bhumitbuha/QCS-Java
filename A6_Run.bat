@echo off
setlocal

:: ========== 1) Point to your JDK and JavaFX SDK ==========
set "JAVA_HOME=C:\Users\Bhumit Buha\.jdks\openjdk-26.0.1"
set "JAVAC=%JAVA_HOME%\bin\javac.exe"
set "JAVA=%JAVA_HOME%\bin\java.exe"
set "JAR=%JAVA_HOME%\bin\jar.exe"
set "JAVADOC=%JAVA_HOME%\bin\javadoc.exe"

:: JavaFX SDK bundled inside this project (relative to this script)
set "JAVA_FX_LIB=%~dp0javafx-sdk-26.0.1\lib"
set "MODULES=javafx.controls,javafx.fxml,javafx.graphics"

:: SQLite JDBC jar (relative to this script)
set "SQLITE_JDBC=%~dp0lib\sqlite-jdbc.jar"


:: ========== 2) Project layout ==========
set "SRCDIR=src"
set "BINDIR=bin"
set "DOCDIR=qcs_doc"
set "MANIFEST=manifest.txt"
set "CLIENT_MAIN=qcs.QCSLoginClient"
set "SERVER_MAIN=qcs.QCSServer"
set "CLIENT_JAR=qcsclient.jar"
set "SERVER_JAR=qcsserver.jar"


:: ========== 3) Clean & prepare ==========
if exist "%BINDIR%" rd /s /q "%BINDIR%"
if exist "%DOCDIR%" rd /s /q "%DOCDIR%"
md "%BINDIR%" 2>nul
md "%DOCDIR%" 2>nul

:: ========== 4) Write the generic manifest ==========
(
  echo Manifest-Version: 1.0
  echo Main-Class: qcs.QCSMain
  echo.
) > "%MANIFEST%"


:: ========== 5) Compile Java sources ==========
echo.
echo ===== Compiling Java sources =====
"%JAVAC%" ^
  --module-path "%JAVA_FX_LIB%" ^
  --add-modules %MODULES% ^
  -cp "%SQLITE_JDBC%" ^
  -d "%BINDIR%" ^
  %SRCDIR%\qcs\*.java
if ERRORLEVEL 1 (
  echo.
  echo COMPILATION FAILED. Check above for errors.
  pause
  exit /b 1
)


:: ========== 6) Copy resources (properties only - images stay in project root) ==========
md "%BINDIR%\qcs" 2>nul
copy /Y "%SRCDIR%\qcs\QCSMessages_en.properties" "%BINDIR%\qcs\" >nul
copy /Y "%SRCDIR%\qcs\QCSMessages_fr.properties" "%BINDIR%\qcs\" >nul


:: ========== 7) Build per-app manifests ==========
set "CLIENT_MAN=client_manifest.txt"
set "SERVER_MAN=server_manifest.txt"

(
  echo Manifest-Version: 1.0
  echo Main-Class: %CLIENT_MAIN%
  echo Class-Path: ..\lib\sqlite-jdbc.jar
  echo.
) > "%CLIENT_MAN%"

(
  echo Manifest-Version: 1.0
  echo Main-Class: %SERVER_MAIN%
  echo Class-Path: ..\lib\sqlite-jdbc.jar
  echo.
) > "%SERVER_MAN%"


:: ========== 8) Build the runnable JARs ==========
pushd "%BINDIR%"
echo.
echo ===== Building %CLIENT_JAR% =====
"%JAR%" cfm "%CLIENT_JAR%" "..\%CLIENT_MAN%" qcs

echo.
echo ===== Building %SERVER_JAR% =====
"%JAR%" cfm "%SERVER_JAR%" "..\%SERVER_MAN%" qcs
popd

if errorlevel 1 (
  echo JAR CREATION FAILED
  pause & exit /b 1
)


:: ========== 9) Generate Javadoc ==========
echo.
echo ===== Generating Javadoc =====
"%JAVADOC%" ^
  --module-path "%JAVA_FX_LIB%" ^
  --add-modules %MODULES% ^
  -cp "%SQLITE_JDBC%" ^
  -Xdoclint:none ^
  -d "%DOCDIR%" ^
  -sourcepath "%SRCDIR%" ^
  -subpackages qcs
if errorlevel 1 echo Javadoc had warnings - continuing anyway


:: ========== 10) Launch Server and Client ==========
echo.
echo ===== Launching Server and Client =====

REM Both apps start with working directory = project root so image paths resolve correctly
start /D "%~dp0." "QCS Server" "%JAVA%" -cp "%~dp0%BINDIR%\%SERVER_JAR%;%SQLITE_JDBC%" %SERVER_MAIN%

start /D "%~dp0." "QCS Client" "%JAVA%" --module-path "%JAVA_FX_LIB%" --add-modules %MODULES% --enable-native-access=javafx.graphics -cp "%~dp0%BINDIR%\%CLIENT_JAR%;%SQLITE_JDBC%" %CLIENT_MAIN%

echo.
echo Done.
pause
endlocal
