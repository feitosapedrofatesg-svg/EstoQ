param(
    [ValidateSet("msi", "app-image")]
    [string]$Tipo = "msi"
)

# Empacota o estoQ para Windows: frontend -> backend -> launcher -> runtime jlink
# (com java.exe) -> jpackage (.msi com atalhos, ou app-image portátil).
# Uso: .\empacotar-win.ps1            (gera o .msi; requer WiX 3.0+ instalado)
#      .\empacotar-win.ps1 -Tipo app-image   (sem WiX: gera a pasta portátil)
# Pré-requisitos no PC: Temurin JDK 21 (inclui jpackage/jlink), Node 20+, Maven.

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
$root = Get-Location

$Versao = "1.0.0"
$Nome = "EstoQ"

function ErrMsg($m) { Write-Host "ERRO: $m" -ForegroundColor Red; exit 1 }

function VerificarComando($c) {
    if (-not (Get-Command $c -ErrorAction SilentlyContinue)) {
        Write-Host "  => '$c' NAO encontrado" -ForegroundColor Yellow
        ErrMsg "Falta $c. Use o Temurin JDK 21 (add ao PATH), Node 20+, NodeJS e Maven."
    }
    Write-Host "  => $c OK" -ForegroundColor Green
}

Write-Host "== 0/5 verificando ferramentas ==" -ForegroundColor Cyan
VerificarComando "node"
VerificarComando "npm"
VerificarComando "mvn"
VerificarComando "java"
VerificarComando "jlink"
VerificarComando "jpackage"

Write-Host "`n== 1/5 build do frontend (React -> dist/) ==" -ForegroundColor Cyan
Push-Location "frontend"
try {
    npm install
    npm run build
} finally { Pop-Location }

Write-Host "`n== 2/5 build do backend (jar com frontend embutido) ==" -ForegroundColor Cyan
Push-Location "backend"
try { mvn -q -DskipTests package } finally { Pop-Location }

Write-Host "`n== 3/5 build do launcher ==" -ForegroundColor Cyan
Push-Location "launcher"
try { mvn -q -DskipTests package } finally { Pop-Location }

Write-Host "`n== 4/5 runtime Java embutido (jlink, inclui java.exe) ==" -ForegroundColor Cyan
$runtime = Join-Path $root "dist\runtime"
if (Test-Path $runtime) { Remove-Item -Recurse -Force $runtime }
$mods = ((& java --list-modules) | ForEach-Object { ($_ -split "@")[0] }) |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
    Sort-Object -Unique
$all = $mods -join ","
& jlink --add-modules $all --output $runtime --strip-debug --no-header-files --no-man-pages --compress zip-6
if ($LASTEXITCODE -ne 0) { ErrMsg "jlink falhou." }

Write-Host "`n== 5/5 empacotando com jpackage ($Tipo) ==" -ForegroundColor Cyan
$stage = Join-Path $root "dist\stage"
$dest  = Join-Path $root "dist\install"
if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
if (Test-Path $dest)  { Remove-Item -Recurse -Force $dest }
New-Item -ItemType Directory -Force -Path $stage, $dest | Out-Null
Copy-Item (Join-Path $root "backend\target\estoq.jar")        (Join-Path $stage "estoq.jar")
Copy-Item (Join-Path $root "launcher\target\estoq-launcher.jar") (Join-Path $stage "estoq-launcher.jar")

$icon = Join-Path $root "frontend\public\icons\EstoQ.ico"
if (-not (Test-Path $icon)) { ErrMsg "Icone nao encontrado ($icon)." }

$argsJpkg = @(
    "--type", $Tipo,
    "--name", $Nome,
    "--app-version", $Versao,
    "--vendor", "EstoQ",
    "--description", "Sistema de Gestão de Estoque para Cozinha (CMV)",
    "--input", $stage,
    "--main-jar", "estoq-launcher.jar",
    "--main-class", "com.estoq.launch.EstoQLauncher",
    "--icon", $icon,
    "--runtime-image", $runtime,
    "--dest", $dest
)
if ($Tipo -eq "msi") {
    $argsJpkg += "--win-shortcut"
    $argsJpkg += "--win-menu"
}
& jpackage @argsJpkg
if ($LASTEXITCODE -ne 0) {
    ErrMsg "jpackage falhou. Para gerar o .msi o jpackage exige o WiX 3.0+ instalado.`n        Instale o WiX (release 3.x do wixtoolset) e rode de novo,`n        ou use: .\empacotar-win.ps1 -Tipo app-image"
}

Write-Host "`nConcluído. Artefatos em dist\install\:" -ForegroundColor Green
Get-ChildItem $dest | Format-Table Name, Length
Write-Host "  . Instalação: clique duplo no .msi (ou dpkg-equivalente no Windows)."
Write-Host "  . Sem instalar: use a pasta dist\install\$Nome\bin\$Nome.exe"
Write-Host "  . Os dados ficam em %LOCALAPPDATA%\estoq (não são tocados em atualizações)."