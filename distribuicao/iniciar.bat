@echo off
rem estoQ - inicia o sistema no Windows
rem Uso: duplo clique neste arquivo (ou rode no Prompt: iniciar.bat)

rem --- auto-reparo da associacao da extensao .bat (para o duplo clique) ---
assoc .bat >nul 2>nul
if not errorlevel 1 (
  assoc .bat=batfile >nul 2>nul
  ftype batfile="%%1" %%* >nul 2>nul
)

cd /d "%~dp0"
if not exist dados mkdir dados

where java >nul 2>nul
if errorlevel 1 (
  echo Java 21 nao encontrado. Instale em: https://adoptium.net  (Temurin 21) e tente de novo.
  echo Para conferir: java -version
  pause
  exit /b 1
)

echo ====== estoQ ======
echo Subindo o sistema... aguarde ~20 segundos.
echo Acesse neste PC: http://localhost:8081
echo Para parar depois: feche esta janela (ou Ctrl+C).
echo Backup diario: execute backup.bat
java -jar estoq.jar --spring.profiles.active=prod
pause
