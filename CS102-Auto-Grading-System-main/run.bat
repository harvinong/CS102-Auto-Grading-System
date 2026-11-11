@echo off
setlocal
set "ROOT=%~dp0"
java -cp "%ROOT%classes;%ROOT%lib\*" autogradesys.App %*
endlocal
