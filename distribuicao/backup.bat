@echo off
rem estoQ — copia de seguranca do banco (Windows).
rem Recomendado: rodar TODO DIA ao encerrar o expediente.
rem Ideal: feche a janela do estoQ (Ctrl+C) antes de executar.
cd /d "%~dp0"
if not exist dados (
  echo A pasta dados ainda nao existe. Inicie o sistema ao menos uma vez antes.
  pause
  exit /b 1
)
if not exist backups mkdir backups
for /f "tokens=1-3 delims=/ " %%a in ("%date%") do set DIA=%%c%%b%%a
for /f "tokens=1-2 delims=: " %%a in ("%time%") do set HOR=%%a%%b
copy /y "dados\estoq.mv.db" "backups\estoq-%DIA%-%HOR%.mv.db"
echo.
echo Backup salvo na pasta backups. Backup diario evitar perder dados.
pause