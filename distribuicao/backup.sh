#!/usr/bin/env bash
# estoQ — cópia de segurança do banco (Linux).
# Recomendado: rodar TODO DIA ao encerrar o expediente.
# Obs.: idealmente com o sistema PARADO (Ctrl+C no iniciar.sh).
# Se rodar com o sistema ligado, a cópia ainda funciona (modo AUTO_SERVER),
# mas o ideal é parar antes.
cd "$(dirname "$0")"

if [ ! -d dados ]; then
  echo "A pasta dados/ ainda não existe. Inicie o sistema ao menos uma vez antes."
  exit 1
fi

TS=$(date +%Y%m%d-%H%M)
mkdir -p backups
cp dados/estoq.mv.db "backups/estoq-$TS.mv.db" 2>/dev/null && echo "Backup salvo: backups/estoq-$TS.mv.db" || echo "Falha ao copiar o banco."

echo "Total de backups em backups/: $(ls backups/ 2>/dev/null | wc -l)"