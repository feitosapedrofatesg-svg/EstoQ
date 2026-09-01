#!/usr/bin/env bash
# estoQ — inicía o sistema (Windows e Linux usam o mesmo jar).
# Uso: duplo clique (Windows) ou: ./iniciar.sh
cd "$(dirname "$0")"
mkdir -p dados

if ! command -v java >/dev/null 2>&1; then
  echo "Java 21 não encontrado. Instale em: https://adoptium.net (Temurin 21) e tente novamente."
  echo "Para conferir: java -version"
  read -r -p "Pressione ENTER para fechar..." _ || true
  exit 1
fi

IP=$(hostname -I 2>/dev/null | awk '{print $1}')
echo "═ estoQ ═"
echo "Subindo o sistema... aguarde ~20 segundos."
[ -n "$IP" ] && echo "Depois acesse em qualquer dispositivo da rede: http://$IP:8081"
echo "Para parar: Ctrl+C  |  backup a cada dia: ./backup.sh"

exec java -jar estoq.jar --spring.profiles.active=prod