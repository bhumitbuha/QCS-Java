@echo off
setlocal

:: ========== 1) Point to your JDK and JavaFX SDK ==========
set "JAVA_HOME=C:\Users\bhumi\.jdks\openjdk-24.0.1"
set "JAVAC=%JAVA_HOME%\bin\javac.exe"
set "JAVA=%JAVA_HOME%\bin\java.exe"
set "JAR=%JAVA_HOME%\bin\jar.exe"
set "JAVADOC=%JAVA_HOME%\bin\javadoc.exe"
set "MODULES=javafx.controls,javafx.fxml"
set "JAVA_FX_LIB=C:\Users\bhumi\OneDrive\Desktop\QCS\openjfx-24.0.1_windows-x64_bin-sdk\javafx-sdk-24.0.1\lib"
REM SQLite JDBC jar (in lib folder)
set "SQLITE_JDBC=.\lib\sqlite-jdbc.jar"


:: ========== 2) Project layout ==========
set "SRCDIR=src"
set "BINDIR=bin"
set "DOCDIR=qcs_doc"
set "CLIENT_MAIN=qcs.QCSLoginClient"
set "SERVER_MAIN=qcs.QCSServer"

set "CLIENT_JAR=qcsclient.jar"
set "SERVER_JAR=qcsserver.jar"


:: ========== 3) Clean & prepare ==========
if exist "%BINDIR%" rd /s /q "%BINDIR%"
if exist "%DOCDIR%" rd /s /q "%DOCDIR%"
md "%BINDIR%" 2>nul
md "%DOCDIR%" 2>nul

:: ========== 4) Write the manifest ==========
(
  echo Manifest-Version: 1.0
  echo Main-Class: qcs.QCSMain
  echo.
) > "%MANIFEST%"


:: ========== 5) Compile Java sources ==========
echo.
echo ===== Compiling Java sources =====
"%JAVAC%" ^
  --module-path "%JAVA_FX_LIB%" --add-modules %MODULES% ^
-cp "%SQLITE_JDBC%" ^
  -d "%BINDIR%" ^
  %SRCDIR%\qcs\*.java
if ERRORLEVEL 1 (
  echo.
  echo *** COMPILATION FAILED. Check above for errors.
  pause
  exit /b 1
)

:: ========== 6) Copy resources ==========
md "%BINDIR%\qcs" 2>nul
copy /Y "%SRCDIR%\qcs\QCSMessages_en.properties" "%BINDIR%\qcs\" >nul
copy /Y "%SRCDIR%\qcs\QCSMessages_fr.properties" "%BINDIR%\qcs\" >nul

md "%BINDIR%\images" 2>nul
copy /Y "%SRCDIR%\images\*.jpg" "%BINDIR%\images\" >nul

REM ===== 7) Build manifests =====
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

:: ========== 8) Build the runnable JAR ==========
pushd "%BINDIR%"
echo.
echo ===== Building %CLIENT_JAR% =====
"%JAR%" cfm "%CLIENT_JAR%" "..\%CLIENT_MAN%" qcs

echo.
echo ===== Building %SERVER_JAR% =====
"%JAR%" cfm "%SERVER_JAR%" "..\%SERVER_MAN%" qcs
popd

if errorlevel 1 (
  echo *** JAR CREATION FAILED ***
  pause & exit /b 1
)


:: ========== 9) Generate Javadoc ==========
echo.
echo ===== Launching Server and Client =====
pushd "%BINDIR%"

REM Server (Swing) – classpath includes sqlite-jdbc.jar
start "QCS Server" "%JAVA%" -cp "%SERVER_JAR%;..\lib\sqlite-jdbc.jar" %SERVER_MAIN%

REM Client (JavaFX)
start "QCS Client" "%JAVA%" --module-path "%JAVA_FX_LIB%" --add-modules %MODULES% -jar "%CLIENT_JAR%"

popd

echo.
echo Done.
pause
endlocal
