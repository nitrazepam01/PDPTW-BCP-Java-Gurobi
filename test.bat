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
set MAIN_OUT=out
set TEST_OUT=out-test
set TEST_SRC=src\test\java

call build.bat
if ERRORLEVEL 1 exit /b %ERRORLEVEL%

if not exist "%TEST_OUT%" mkdir "%TEST_OUT%"

if exist test-sources.tmp del test-sources.tmp
for /R "%TEST_SRC%" %%f in (*.java) do (
  echo %%f>> test-sources.tmp
)

echo [2] Compiling tests...
javac --release 17 -encoding UTF-8 -cp "%MAIN_OUT%;%GUROBI_JAR%" -d "%TEST_OUT%" @test-sources.tmp
set JAVAC_ERROR=%ERRORLEVEL%
del test-sources.tmp

if %JAVAC_ERROR% neq 0 (
  echo [ERROR] Test compilation failed.
  exit /b %JAVAC_ERROR%
)

echo [3] Running tests...
java -Djava.library.path="%GUROBI_BIN%" -cp "%MAIN_OUT%;%TEST_OUT%;%GUROBI_JAR%" org.pdptw.TestRunner
exit /b %ERRORLEVEL%
