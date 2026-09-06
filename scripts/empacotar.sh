#!/usr/bin/env bash
# Gera o instalador DESKTOP do estoQ (.deb + app-image) com JRE embutido.
#
# Pipeline: frontend (dist) -> backend (estoq.jar) -> launcher (estoq-launcher.jar)
#           -> jpackage (programa + Java 21 embutido + .desktop).
#
# Uso: ./scripts/empacotar.sh
set -euo pipefail
cd "$(dirname "$0")/.."

NOME="EstoQ"
NOME_PKG="estoq"
VERSAO="1.0.0"
MANTENEDOR="estoq@localhost"

STAGE="dist/stage"
DEST="dist/install"
RUNTIME="dist/runtime"
ICON="frontend/public/icons/icon-512.png"

for cmd in node npm mvn jpackage; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "ERRO: '$cmd' não encontrado."; exit 1; }
done
[ -f "$ICON" ] || { echo "ERRO: ícone não encontrado ($ICON)."; exit 1; }

echo "== 1/4 build do frontend (React -> dist/) =="
(cd frontend && npm run build)

echo "== 2/4 build do backend (jar com frontend embutido) =="
(cd backend && mvn -q -DskipTests package)

echo "== 3/4 build do launcher =="
(cd launcher && mvn -q -DskipTests package)

echo "== 3b/4 runtime Java embutido (jlink, inclui bin/java p/ o subprocesso do backend) =="
rm -rf "$RUNTIME"
MODS=$(java --list-modules | sed 's/@.*//' | sort -u | tr '\n' ',' | sed 's/,$//')
jlink --add-modules "$MODS" --output "$RUNTIME" \
  --strip-debug --no-header-files --no-man-pages --compress zip-6

echo "== 4/4 empacotando com jpackage (JRE embutido) =="
rm -rf "$STAGE" "$DEST"
mkdir -p "$STAGE" "$DEST"
cp backend/target/estoq.jar        "$STAGE/estoq.jar"
cp launcher/target/estoq-launcher.jar "$STAGE/estoq-launcher.jar"

for tipo in app-image deb; do
  echo "---- jpackage --type $tipo ----"
  ARGS_BASE=(
    --type "$tipo"
    --name "$NOME"
    --app-version "$VERSAO"
    --vendor "EstoQ"
    --description "Sistema de Gestão de Estoque para Cozinha (CMV)"
    --input "$STAGE"
    --main-jar estoq-launcher.jar
    --main-class com.estoq.launch.EstoQLauncher
    --icon "$ICON"
    --runtime-image "$RUNTIME"
    --dest "$DEST"
  )
  if [ "$tipo" = "app-image" ]; then
    jpackage "${ARGS_BASE[@]}"
  else
    jpackage "${ARGS_BASE[@]}" \
      --linux-shortcut \
      --linux-package-name "$NOME_PKG" \
      --linux-deb-maintainer "$MANTENEDOR" \
      --linux-app-category Office
    # pós-processa o .deb: corrige o Categories do atalho do menu (jpackage deixa "Unknown")
    DEB="$(ls "$DEST"/*.deb | head -1)"
    DIRE="dist/deb-fix"
    rm -rf "$DIRE"
    mkdir -p "$DIRE"
    dpkg-deb -R "$DEB" "$DIRE"
    sed -i 's/^Categories=.*/Categories=Office;/' "$DIRE/opt/estoq/lib/estoq-EstoQ.desktop"
    # dependência de OCR para leitura de cupom por foto
    sed -i 's/^Depends:.*/&, tesseract-ocr, tesseract-ocr-por/' "$DIRE/DEBIAN/control"
    rm -f "$DEB"
    dpkg-deb -b "$DIRE" "$DEB"
  fi
done

echo
echo "Concluído. Artefatos em $DEST/:"
ls -lh "$DEST"
echo
echo "  • Para testar sem instalar: jpackage criou a pasta 'EstoQ/' (app-image)."
echo "  • Instalação: sudo dpkg -i $DEST/estoq_${VERSAO}_amd64.deb"
echo "  • Os dados ficam em ~/.local/share/estoq/dados (não são tocados em atualizações)."