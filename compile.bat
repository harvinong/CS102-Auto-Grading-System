@echo off
setlocal
set "ROOT=%~dp0"
if exist "%ROOT%classes" rmdir /s /q "%ROOT%classes"
mkdir "%ROOT%classes"
dir /s /b "%ROOT%src\main\java\*.java" > "%ROOT%.java-sources"
javac -encoding UTF-8 -cp "%ROOT%lib\*" -d "%ROOT%classes" @"%ROOT%.java-sources"
del "%ROOT%.java-sources"
endlocal
