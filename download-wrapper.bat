@echo off
echo Downloading Gradle Wrapper JAR...
powershell -Command "Invoke-WebRequest -Uri 'https://github.com/gradle/gradle/raw/v8.4.0/gradle/wrapper/gradle-wrapper.jar' -OutFile 'gradle\wrapper\gradle-wrapper.jar'"
echo Gradle Wrapper JAR downloaded successfully!