@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem

@if "%DEBUG%"=="" @echo off
@setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
goto execute

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

:execute
"%JAVA_EXE%" -Dorg.gradle.appname=%APP_BASE_NAME% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@endlocal
