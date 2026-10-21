@echo off
setlocal
rem Read the SMS login code from Redis for the given phone number.
rem
rem Required environment variables (see local.env.ps1):
rem   REDIS_HOST / REDIS_PORT / REDIS_PASSWORD
rem
rem Optional:
rem   MEMURAI_CLI or REDIS_CLI  - path or name of the redis command line client
rem                               (default: redis-cli on PATH)

set "PHONE=%~1"
if "%PHONE%"=="" set /p "PHONE=Please input the phone number: "
if "%PHONE%"=="" (
    echo No phone number provided, exit.
    exit /b 1
)

if "%REDIS_HOST%"=="" set "REDIS_HOST=localhost"
if "%REDIS_PORT%"=="" set "REDIS_PORT=6379"

set "CLI=%MEMURAI_CLI%"
if "%CLI%"=="" set "CLI=%REDIS_CLI%"
if "%CLI%"=="" set "CLI=redis-cli"

set "AUTH="
if not "%REDIS_PASSWORD%"=="" set "AUTH=-a %REDIS_PASSWORD% --no-auth-warning"

echo Phone: %PHONE%
echo Code :
"%CLI%" -h %REDIS_HOST% -p %REDIS_PORT% %AUTH% get "login:code:%PHONE%"
echo.
echo ^(code expires in a few minutes; if nothing printed, click "send code" again^)
