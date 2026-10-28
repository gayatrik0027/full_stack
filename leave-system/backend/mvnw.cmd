@REM ----------------------------------------------------------------------------
@REM Maven Wrapper Script for Windows
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set "DIR=%~dp0"
set "MAVEN_WRAPPER_JAR=%DIR%.mvn\wrapper\maven-wrapper.jar"
set "WRAPPER_PROPERTIES=%DIR%.mvn\wrapper\maven-wrapper.properties"

if exist "%MAVEN_WRAPPER_JAR%" goto run_wrapper

echo [INFO] Downloading Maven Wrapper jar...
powershell -Command "try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object System.Net.WebClient).DownloadFile('https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar', '%MAVEN_WRAPPER_JAR%') } catch { Write-Host $_; exit 1 }"
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Failed to download maven-wrapper.jar.
    exit /b %ERRORLEVEL%
)

:run_wrapper
java -Dmaven.multiModuleProjectDirectory="%DIR%" -cp "%MAVEN_WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
exit /b %ERRORLEVEL%
