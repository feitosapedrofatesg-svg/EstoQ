@echo off
rem estoQ — inicia o sistema no Windows.
rem Uso: duplo clique neste arquivo.
cd /d "%~dp0"
if not exist dados mkdir dados

where java >nul 2>nul
if errorlevel 1 (
  echo Java 21 nao encontrado. Instale em: https://adoptium.net (Temurin 21) e tente de novo.
  echo Para conferir: java -version
  pause
  exit /b 1
)

echo ═ estoQ ═ 
echo Subindo o sistema... aguarde ~20 segundos.
echo Depois acesse em qualquer aparelho da rede (tablet, celular, PC).
echo Para parar: feche esta janela ^(Ctrl+C^). Backup: execute backup.bat
java -jar estoq.jar --spring.profiles.active=prod
pause