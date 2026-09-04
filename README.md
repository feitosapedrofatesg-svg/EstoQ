# estoQ — Gestão de Estoque e CMV para Cozinha

Sistema para substituir a planilha "Planilha de cálculo de CMV Real
DEZEMBRO 22.xls": cadastro de produtos e categorias, controle de estoque por
**lotes e movimentações** (entrada, consumo, desperdício, ajuste), conferência
física (balanço), alertas e relatório de **CMV**.

## Stack

- **Backend**: Java 21 · Spring Boot 4 · PostgreSQL (dev) / H2 (prod)
- **Frontend**: React 18 · Vite · TypeScript — porta **5173**
- **Banco**: PostgreSQL 16 via Docker (`docker-compose.yml`)

## Início rápido

```bash
docker compose up -d postgres

cd backend
mvn spring-boot:run        # dev na porta 8082

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
- O jar roda com o perfil Spring `prod` (`--spring.profiles.active=prod`); o perfil
  padrão (`dev`) continua no PostgreSQL/Docker.
- Frontend vira **PWA**: o tablet instala um ícone e roda em tela cheia.

Login por PIN: **Admin** `000000` (tudo) · **Cozinha** `111111` (movimentações).

## Funcionalidades

- Catálogo de produtos (196 itens iniciais) com categorias e unidade de medida.
- Entradas geram **lotes** (com validade e preço unitário).
- Consumo com prioridade para embalagens abertas e depois **FIFO** por validade;
  sobras devolvidas ao estoque.
- Desperdício com motivo (vencimento, deterioração, preparo, sobra) e valor de prejuízo.
- **Conferência física**: balanço periódico, contagem e ajustes automáticos.
- Parâmetros de estoque por produto (mínimo, consumo médio, tempo de reposição).
- Alertas: estoque baixo, vencimento/vencidos, balanço pendente, diferenças.
- Relatório de **CMV** (consumo + desperdício ÷ vendas), relatórios por tipo e
  importação/exportação em CSV.
- Login por PIN com perfis `ADMIN`, `COZINHA` e `NUTRICIONISTA`.

## Documentação

Detalhes em [`documentacao/`](documentacao/):

- [Arquitetura](documentacao/arquitetura.md)
- [Modelo de dados](documentacao/modelo-de-dados.md)
- [API REST](documentacao/api.md)
- [Manual de uso](documentacao/manual-de-uso.md)
- [Diagrama de classes (PlantUML)](documentacao/diagrama-de-classes.puml)

## Estrutura

```
backend/src/main/java/com/estoq/
├── core/
│   ├── conf/        # configurações (cors, web, security, docs, seed)
│   ├── controllers/ # GenericController (CRUD REST genérico)
│   ├── domains/     # BaseModel
│   ├── dtos/        # BaseDTO
│   ├── exceptions/  # BusinessException + handler global
│   ├── helpers/     # IGenericAdapter, NumeroUtil
│   ├── repositories/# IGenericRepository
│   ├── services/    # GenericService + IGenericService
│   └── validations/ # GenericValidation + IGenericValidation
├── business/        # módulos auto-contidos (produto, lote, movimentacao,
│                    # balanco, relatorio, categoria, usuario, sessao...)
└── EstoqApplication.java
frontend/            # SPA React (Vite)
documentacao/        # arquitetura, modelo, API, manual
```

> Cada domínio de `business/` reúne `Model`, `DTO`/`View`/`Request`, `Adapter`,
> `Validation`, `IValidation`, `Service`, `Repository` e `Controller` (padrão
> PIAds3 do curso de ADS). Não há camada `api/` separada.

## Nota

As **vendas** informadas na consulta de CMV são valores manuais (não vêm da
planilha). O CMV é derivado dos custos reais das movimentações (preço do lote
de origem no consumo e custo médio no desperdício) — ver o
[manual](documentacao/manual-de-uso.md).