@echo off
setlocal

rem %~dp0 is expanded pathname of the current script under NT
set XV_HOME=%~dp0..

set OPTS=-Xmx64m
if not exist "%XV_HOME%/bin/setenv.bat" goto rungradle
call "%XV_HOME%/bin/setenv.bat"
java %OPTS% -classpath "%CLASSPATH%" org.mitre.xml.validate.XmlValidate -kml "-home=%XV_HOME%" "-map=%XV_HOME%\ns.map" %*
goto end

:rungradle
echo Must run "gradle setup" from XmlValidate home directory to configure batch file environment
:end