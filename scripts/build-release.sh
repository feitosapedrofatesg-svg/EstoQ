#!/usr/bin/env bash
# Gera o pacote de ENTREGA (distribuicao/): build do frontend + jar com o perfil prod.
# Uso: ./scripts/build-release.sh
set -euo pipefail
cd "$(dirname "$0")/.."

echo "== 1/3 build do frontend (PWA) =="
(cd frontend && npm run build)

echo "== 2/3 build do jar (frontend embutido) =="
(cd backend && mvn -q -DskipTests package)

echo "== 3/3 montando distribuicao/ =="
mkdir -p distribuicao
cp backend/target/estoq.jar distribuicao/estoq.jar
rm -rf distribuicao/dados

echo "Pacote gerado em distribuicao/:"
ls -lh distribuicao/estoq.jar
echo "o banco (pasta dados/) será criado no primeiro iniciar."