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
set SRC_DIR=src\main\java
set OUT_DIR=out

if not exist "%GUROBI_JAR%" (
  echo [ERROR] Gurobi Java jar not found: "%GUROBI_JAR%"
  exit /b 1
)

if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

if exist sources.tmp del sources.tmp
for /R "%SRC_DIR%" %%f in (*.java) do (
  echo %%f>> sources.tmp
)

echo [1] Compiling PDPTW BCP Java foundation...
javac --release 17 -encoding UTF-8 -cp "%GUROBI_JAR%" -d "%OUT_DIR%" @sources.tmp
set JAVAC_ERROR=%ERRORLEVEL%
del sources.tmp

if %JAVAC_ERROR% neq 0 (
  echo [ERROR] Compilation failed.
  exit /b %JAVAC_ERROR%
)

echo [OK] Compilation successful.
endlocal
