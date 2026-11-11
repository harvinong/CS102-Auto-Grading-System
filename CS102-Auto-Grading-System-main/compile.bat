@echo off
setlocal
rem Work from the repository root (this script's folder) whatever folder it is started from.
pushd "%~dp0" || exit /b 1
if exist classes rmdir /s /q classes
mkdir classes
if exist .java-sources del .java-sources
rem javac splits an @file on spaces and treats backslashes as escapes, so each source path is written
rem quoted and with forward slashes; a folder such as "C:\Users\First Last\CS102 Project" then works.
for /r "src\main\java" %%F in (*.java) do call :addSource "%%F"
javac -encoding UTF-8 -cp "lib\*" -d classes @.java-sources
set "STATUS=%ERRORLEVEL%"
del .java-sources
popd
endlocal & exit /b %STATUS%

:addSource
set "SOURCE=%~1"
set "SOURCE=%SOURCE:\=/%"
>> .java-sources echo "%SOURCE%"
exit /b 0
