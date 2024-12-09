@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  analysis-of-endpoint-connections startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and ANALYSIS_OF_ENDPOINT_CONNECTIONS_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\analysis-of-endpoint-connections-1.0.0.jar;%APP_HOME%\lib\javaparser-symbol-solver-core-3.26.2.jar;%APP_HOME%\lib\javaparser-core-serialization-3.26.2.jar;%APP_HOME%\lib\javaparser-core-3.26.2.jar;%APP_HOME%\lib\spring-boot-starter-web-3.3.5.jar;%APP_HOME%\lib\spring-boot-starter-json-3.3.5.jar;%APP_HOME%\lib\jackson-datatype-jsr310-2.18.1.jar;%APP_HOME%\lib\jackson-annotations-2.18.1.jar;%APP_HOME%\lib\jackson-datatype-jdk8-2.18.1.jar;%APP_HOME%\lib\jackson-module-parameter-names-2.18.1.jar;%APP_HOME%\lib\jackson-core-2.18.1.jar;%APP_HOME%\lib\jackson-dataformat-yaml-2.18.1.jar;%APP_HOME%\lib\jackson-databind-2.18.1.jar;%APP_HOME%\lib\spring-boot-starter-3.3.5.jar;%APP_HOME%\lib\spring-boot-starter-logging-3.3.5.jar;%APP_HOME%\lib\logback-classic-1.5.12.jar;%APP_HOME%\lib\log4j-to-slf4j-2.23.1.jar;%APP_HOME%\lib\jul-to-slf4j-2.0.16.jar;%APP_HOME%\lib\slf4j-api-2.0.16.jar;%APP_HOME%\lib\javassist-3.30.2-GA.jar;%APP_HOME%\lib\guava-33.3.0-jre.jar;%APP_HOME%\lib\jakarta.json-api-2.1.3.jar;%APP_HOME%\lib\snakeyaml-2.3.jar;%APP_HOME%\lib\spring-boot-starter-tomcat-3.3.5.jar;%APP_HOME%\lib\spring-webmvc-6.1.14.jar;%APP_HOME%\lib\spring-web-6.1.14.jar;%APP_HOME%\lib\logback-core-1.5.12.jar;%APP_HOME%\lib\failureaccess-1.0.2.jar;%APP_HOME%\lib\listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar;%APP_HOME%\lib\jsr305-3.0.2.jar;%APP_HOME%\lib\checker-qual-3.43.0.jar;%APP_HOME%\lib\error_prone_annotations-2.28.0.jar;%APP_HOME%\lib\spring-boot-autoconfigure-3.3.5.jar;%APP_HOME%\lib\spring-boot-3.3.5.jar;%APP_HOME%\lib\jakarta.annotation-api-2.1.1.jar;%APP_HOME%\lib\spring-context-6.1.14.jar;%APP_HOME%\lib\spring-aop-6.1.14.jar;%APP_HOME%\lib\spring-beans-6.1.14.jar;%APP_HOME%\lib\spring-expression-6.1.14.jar;%APP_HOME%\lib\spring-core-6.1.14.jar;%APP_HOME%\lib\tomcat-embed-websocket-10.1.31.jar;%APP_HOME%\lib\tomcat-embed-core-10.1.31.jar;%APP_HOME%\lib\tomcat-embed-el-10.1.31.jar;%APP_HOME%\lib\micrometer-observation-1.12.11.jar;%APP_HOME%\lib\spring-jcl-6.1.14.jar;%APP_HOME%\lib\micrometer-commons-1.12.11.jar;%APP_HOME%\lib\log4j-api-2.23.1.jar


@rem Execute analysis-of-endpoint-connections
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %ANALYSIS_OF_ENDPOINT_CONNECTIONS_OPTS%  -classpath "%CLASSPATH%"  %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable ANALYSIS_OF_ENDPOINT_CONNECTIONS_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%ANALYSIS_OF_ENDPOINT_CONNECTIONS_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
