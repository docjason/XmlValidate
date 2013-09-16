@echo off
setlocal

rem %~dp0 is expanded pathname of the current script under NT
set XV_HOME=%~dp0..

if not exist "%XV_HOME%\build\libs\jdom-1.1.3.jar" goto rungradle
set OPTS=-Xmx64m
set SCHEMA=http://www.opengis.net/kml/2.2
set SCHEMALOC=%XV_HOME%\schemas\kml22.xsd
set CLASSPATH=%XV_HOME%\build\libs\*
java %OPTS% -classpath "%CLASSPATH%" org.mitre.xml.validate.XmlValidate -kmz -ns=%SCHEMA% "-schema=%SCHEMALOC%" %*
goto end

:rungradle
echo Must run "gradle setup" from XmlValidate home directory to configure batch file environment
:end