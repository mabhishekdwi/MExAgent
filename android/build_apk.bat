@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-11.0.16.1
set ANDROID_HOME=C:\Users\Abishek dwivedi\AppData\Local\Android\Sdk
set ANDROID_SDK_ROOT=C:\Users\Abishek dwivedi\AppData\Local\Android\Sdk
set PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%

echo Building MExAgent APK...
cd /d "%~dp0"
call gradlew.bat assembleDebug
echo Build exit code: %ERRORLEVEL%
