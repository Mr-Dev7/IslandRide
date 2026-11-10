@REM ----------------------------------------------------------------------------
@REM Maven Wrapper Script for Windows
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set "DIR=%~dp0"
if "%DIR%"=="" set "DIR=.\"

if not "%JAVA_HOME%"=="" (
    set "JAVACMD=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVACMD=java.exe"
)

if not exist "%JAVACMD%" (
    if exist "C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot\bin\java.exe" (
        set "JAVACMD=C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot\bin\java.exe"
    ) else if exist "C:\Program Files\Java\jdk-22\bin\java.exe" (
        set "JAVACMD=C:\Program Files\Java\jdk-22\bin\java.exe"
    )
)

set "WRAPPER_JAR=%DIR%.mvn\wrapper\maven-wrapper.jar"
set "WRAPPER_PROP=%DIR%.mvn\wrapper\maven-wrapper.properties"

if exist "%WRAPPER_JAR%" (
    goto runWrapper
)

echo [INFO] Downloading Maven Wrapper Jar...
powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar', '%WRAPPER_JAR%')"

:runWrapper
"%JAVACMD%" -cp "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%DIR%." org.apache.maven.wrapper.MavenWrapperMain %*
if ERRORLEVEL 1 exit /b 1
exit /b 0

