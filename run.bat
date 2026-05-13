@echo off
chcp 65001 >nul
setlocal

if not defined GUROBI_HOME (
  echo [ERROR] GUROBI_HOME is not set.
  echo Set it to your Gurobi win64 directory, for example:
  echo   set GUROBI_HOME=D:\Application_install\gurobi\win64
  exit /b 1
)

set GUROBI_JAR=%GUROBI_HOME%\lib\gurobi.jar
set GUROBI_BIN=%GUROBI_HOME%\bin
set OUT_DIR=out

if not exist "%GUROBI_JAR%" (
  echo [ERROR] Gurobi Java jar not found: "%GUROBI_JAR%"
  exit /b 1
)

if not exist "%OUT_DIR%" (
  call build.bat
  if ERRORLEVEL 1 exit /b %ERRORLEVEL%
)

java -Djava.library.path="%GUROBI_BIN%" -cp "%OUT_DIR%;%GUROBI_JAR%" org.pdptw.cli.Main %*
set JAVA_EXIT=%ERRORLEVEL%
endlocal & exit /b %JAVA_EXIT%
