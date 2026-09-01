# estoQ — Gestão de Estoque e CMV para Cozinha

Sistema para substituir a planilha "Planilha de cálculo de CMV Real
DEZEMBRO 22.xls": cadastro de produtos, períodos, compras, estoque semanal,
relatório de **CMV** (semanal e mensal), matriz de consumo, alertas de
reposição e importação da planilha legada.

## Stack

- **Backend**: Java 21 · Spring Boot 4 · PostgreSQL · Apache POI — porta **8081**
- **Frontend**: React 18 · Vite · TypeScript — porta **5173**
- **Banco**: PostgreSQL 16 via Docker (`docker-compose.yml`)

## Início rápido

```bash
docker compose up -d postgres

cd backend
mvn spring-boot:run

cd frontend
npm install
npm run dev
```

Abra `http://localhost:5173`. Swagger UI: `http://localhost:8081/swagger-ui.html`.

## Entrega (rodar no restaurante)

O sistema pode ser empacotado em **um único arquivo autossuficiente**
(`estoq.jar`) com banco embutido (H2), servindo o próprio site — sem Docker,
PostgreSQL ou Node no ambiente final. Basta **Java 21** no PC (ex.: PC do caixa).

- `./scripts/build-release.sh` — gera o pacote em [`distribuicao/`](distribuicao/)
- [`distribuicao/README-entregavel.md`](distribuicao/README-entregavel.md) — manual de instalação
- O jar roda com o perfil Spring `prod` (`--spring.profiles.active=prod`); o perfil
  padrão (`dev`) continua no PostgreSQL/Docker.
- Frontend vira **PWA**: o tablet instala um ícone e roda em tela cheia.

Login por PIN: **Admin** `000000` (tudo) · **Cozinha** `111111` (uso diário).

## Funcionalidades

- Catálogo de produtos (196 itens iniciais) com estoque mínimo semanal.
- Períodos de apuração (semanas) com vendas e trava de fechamento.
- Lançamento de compras por período.
- Estoque inicial/final por período, com herança automática entre períodos.
- Relatório de CMV (semanal e mensal) e matriz de consumo.
- Alertas de reposição (`REPOR`, `ATENCAO`, `OK`).
- Importação da planilha legada (.xls/.xlsx) e exportação em CSV.
- Login por PIN com dois perfis (Admin e Cozinha) e tela de Uso Diário no tablet.

## Documentação

Detalhes em [`documentacao/`](documentacao/):

- [Arquitetura](documentacao/arquitetura.md)
- [Modelo de dados](documentacao/modelo-de-dados.md)
- [API REST](documentacao/api.md)
- [Manual de uso](documentacao/manual-de-uso.md)

## Estrutura

```
backend/src/main/java/com/estoq/
├── core/          # infraestrutura genérica (CRUD, exceções, helpers)
├── conf/          # configurações (CORS, OpenAPI, seed)
├── business/      # domínios (produto, periodo, compra, estoque, relatorio)
├── api/           # controllers REST e DTOs de projeção
└── integracao/    # importação de planilha e exportação CSV
frontend/          # SPA React (Vite)
documentacao/      # arquitetura, modelo, API, manual
```

## Nota

As **vendas** de cada período são informadas manualmente (não são lidas da
planilha). O CMV calculado pode diferir levemente da planilha antiga porque
o sistema precifica o estoque final pela última compra do período — ver o
[manual](documentacao/manual-de-uso.md).