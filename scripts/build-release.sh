#!/usr/bin/env bash
# Gera o pacote de ENTREGA (distribuicao/): build do frontend + jar com o perfil prod.
# Uso: ./scripts/build-release.sh
#
# Segurança:
#  - build SEMPRE limpo (mvn clean) para não misturar classes velhas no jar;
#  - a pasta dados/ (banco em uso) NUNCA é apagada: se existir, é movida para
#    distribuicao/dados_backup_<ts> antes de gerar o pacote limpo.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "== 1/3 build do frontend (PWA) =="
(cd frontend && npm run build)

echo "== 2/3 build do jar (frontend embutido, build limpo) =="
(cd backend && mvn -q -DskipTests clean package)

echo "== 3/3 montando distribuicao/ =="
mkdir -p distribuicao
cp backend/target/estoq.jar distribuicao/estoq.jar

if [ -d distribuicao/dados ] && [ -n "$(ls -A distribuicao/dados 2>/dev/null)" ]; then
  TS=$(date +%Y%m%d-%H%M%S)
  mv distribuicao/dados "distribuicao/dados_backup_$TS"
  echo "Dados existentes preservados em distribuicao/dados_backup_$TS (não foram apagados)."
elif [ -d distribuicao/dados ]; then
  rmdir distribuicao/dados
fi

echo "Pacote gerado em distribuicao/:"
ls -lh distribuicao/estoq.jar
echo "o banco (pasta dados/) será criado no primeiro iniciar."