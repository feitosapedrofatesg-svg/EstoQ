# estoQ — Arquitetura

## Visão geral

O **estoQ** é um sistema de gestão de estoque e apuração de CMV (Custo da
Mercadoria Vendida) para operações de alimentação (cozinha), substituindo a
planilha "Planilha de cálculo de CMV Real DEZEMBRO 22.xls".

A aplicação é composta por:

| Componente | Tecnologia | Porta |
|---|---|---|
| Frontend (SPA/PWA) | React 18 + Vite + TypeScript | 5173 (dev) |
| Backend (API REST) | Java 21 + Spring Boot 4 | 8082 (dev) / 8081 (prod) |
| Banco de dados | PostgreSQL 16 (Docker) — perfil `dev` | 5433 (host) |

### Modo de entrega (`prod`)
No restaurante o sistema roda como **um único jar autossuficiente**
(`distribuicao/estoq.jar`) com banco **H2 embutido** em arquivo (`./dados/`), que
serve também o build do frontend (estático) — sem Docker/Postgres/Node. O jar é
gerado por `scripts/build-release.sh` (perfil Spring `prod`). O frontend é **PWA**
(`vite-plugin-pwa`): o tablet instala o app em tela cheia. Rotas client-side usam
`HashRouter`, evitando fallback de URL no servidor estático.

## Estrutura de pastas

```
estoQ/
├── backend/
│   └── src/main/java/com/estoq/
│       ├── core/          # infraestrutura genérica reutilizável
│       │   ├── conf/      # configurações (cors, web, security, docs, seed)
│       │   ├── controllers/  # GenericController
│       │   ├── domains/      # BaseModel (UUID, ativo, auditoria)
│       │   ├── dtos/         # BaseDTO
│       │   ├── exceptions/   # hierarquia de exceções + handler global
│       │   ├── helpers/      # IGenericAdapter, NumeroUtil
│       │   ├── repositories/ # IGenericRepository
│       │   ├── services/     # GenericService + IGenericService (hooks)
│       │   └── validations/  # GenericValidation + IGenericValidation
│       ├── business/      # regras de negócio (módulos auto-contidos)
│       └── EstoqApplication.java
├── frontend/              # SPA React (Vite)
├── documentacao/          # esta documentação
└── docker-compose.yml     # infraestrutura (PostgreSQL)
```

> **Nota:** diferente de um projeto com camada `api/` separada, este backend segue o
> padrão de **módulos auto-contidos**: cada domínio de `business/` reúne seu
> `Model`, `DTO`, `View`/`Request`, `Adapter`, `Validation`, `Service`,
> `Repository` e `Controller`. O pacote `api/` foi eliminado — os controllers
> REST vivem dentro do próprio módulo de negócio (mesma filosofia do projeto de
> referência **PIAds3** do curso de ADS).

## Backend

### camada `core`
Infraestrutura genérica usada por todos os domínios:

- `conf/cors/CorsConfig` — libera o frontend de dev.
- `conf/web/WebConfig` — registra o `AuthInterceptor` em `/api/**`.
- `conf/security/AuthInterceptor` — autenticação Bearer por sessão.
- `conf/security/PasswordConfig` — `BCryptPasswordEncoder`.
- `conf/docs/OpenApiConfig` — Swagger UI em `/swagger-ui.html`.
- `conf/seed/SeedCatalogConfig` e `conf/seed/SeedUsuariosConfig` — semeadura inicial.
- `domains/BaseModel` — entidade-base com `id` (UUID), `ativo`, auditoria.
- `dtos/BaseDTO` — DTO-base com `id`, `ativo`, `dataHoraCriacao`.
- `services/IGenericService + GenericService` — CRUD genérico (find, insert,
  update, delete, findAllActive paginado e em lista) com hooks de antes/depois.
- `validations/IGenericValidation` — validações por domínio.
- `helpers/IGenericAdapter` — conversão entidade ↔ DTO (com `toDtoPage`).
- `controllers/GenericController` — endpoints REST genéricos (`GET`, `POST`,
  `PUT`, `DELETE` por entidade).
- `exceptions/` — `BusinessException`, `FieldValidationException`,
  `RuleValidationException` e handler global.

### camada `business`
Domínios (módulos auto-contidos, no padrão PIAds3), cada um com `Model`,
`DTO`/`View`/`Request`, `Adapter`, `Validation`, `IValidation`, `Repository`,
`Service` e `Controller`:

- `usuario` + `sessao` — acesso por **PIN** (BCrypt) e sessões Bearer (24 h).
- `categoria` — classificação do catálogo (CRUD com trava de exclusão).
- `produto` — catálogo com `unidadeMedida` (enum) e categoria; `saldoAtual` e
  `estoqueMinimo` derivados (transientes).
- `parametro` — estoque mínimo, médio, máximo e consumo médio diário por produto.
- `lote` — recebimentos, vencimento e FIFO.
- `movimentacao` — `MovimentacaoEstoqueModel` (abstract) com `Entrada`,
  `Consumo`, `Desperdicio` e `Ajuste` (single-table); regras de baixa,
  custo do consumo, prejuízo, reversão.
- `produtoaberto` — embalagens abertas (saldo restante, sobras).
- `balanco` — conferência física: `Balanco`, `ItemBalanco`,
  `ConfiguracaoBalanco` e geração de ajustes.
- `alerta` — estoque baixo, vencimento/vencidos, balanço pendente,
  diferenças apuradas.
- `relatorio` — CMV sobre movimentações, relatórios por tipo persistidos e dashboard.
- `sessao` — sessões de autenticação e `AuthController` (login/logout/me).

> Os controllers REST de cada domínio (movimentação, lote, produtoaberto,
> parametro, balanço/conferência, alerta, relatório, categoria, produto,
> usuário e integração CSV) ficam dentro do próprio módulo em `business/`.

### camada `integracao`
- `business/produto/IntegracaoController` — importa **CSV** de produtos (cria
  categorias/produtos e lança entradas) e de movimentações; exporta estoque e
  CMV em CSV.
- `business/produto/ProdutoImportadorHelper` — logística de criar/atualizar
  produtos na importação.

### camada `core/conf`
- `cors/CorsConfig` — libera o frontend de dev (`http://localhost:5173`).
- `docs/OpenApiConfig` — Swagger UI em `/swagger-ui.html`.
- `seed/SeedCatalogConfig` — semeadura inicial: 196 produtos, categorias e
  `ParametroEstoque` por produto (`seed/produtos.json`).
- `security/AuthInterceptor` + `web/WebConfig` — exige
  `Authorization: Bearer <token>` em `/api/**`. Cozinha acessa leituras
  (produtos, lotes, produtos-abertos, movimentações, categorias) e `POST` de
  consumo/abrir embalagem/sobra.
- `security/PasswordConfig` — `BCryptPasswordEncoder`.
- `seed/SeedUsuariosConfig` — usuários padrão (`000000` Admin, `111111` Cozinha).

## Frontend

SPA React com `react-router-dom`. Rotas:

```
/                Dashboard (CMV do mês, alertas, indicadores)
/movimentacoes   Movimentações (consumo/entrada + histórico) — cozinha e admin
/desperdicio     Registro de desperdícios
/lotes           Lotes, vencimentos e embalagens abertas (abrir/sobra)
/produtos        CRUD do catálogo (categoria, unidade, mínimo, saldo)
/conferencia     Balanço físico (config, contagem, apurar, confirmar)
/relatorios      Dashboard, CMV, relatórios por tipo, alertas, CSV
/usuarios        Gestão de PINs e perfis (somente admin)
/login           Login por PIN (teclado de 6 dígitos)
```

O frontend consome o backend via proxy do Vite (`/api` → `http://localhost:8082`),
evitando CORS em desenvolvimento.

## Fluxo operacional

1. Cadastre **categorias** e **produtos** (ou use o catálogo inicial).
2. **Entrada** registra mercadoria e gera **lotes** (preço = total ÷ qtd).
3. **Consumo/Desperdício/Ajuste** baixam em FIFO (desperdício com motivo).
4. **Embalagens abertas** priorizam o consumo antes do lote fechado; sobras voltam.
5. **Conferência** periódica apura o físico; confirmar gera **ajustes** automáticos.
6. **Relatórios**: CMV = Σ consumo + Σ desperdício (período) ÷ vendas; tipos
   por vencimento/estoque/aberto/consumo médio; alertas de reposição.

## Decisões e regras

- Saldo derivado (lotes + embalagens abertas); nenhum contador persistido.
- Consumo custeado pelo **preço do lote de origem**; desperdício pelo **custo
  médio** (média dos lotes disponíveis).
- **Unidade de medida** por enum (`KG, G, L, ML, UN, PCT, CARTELA, CX`), com
  suporte aos valores legados na importação.
- Exclusão de categoria bloqueada com produtos ativos vinculados.
- Portas: 8081 em produção (jar) e 8082 em desenvolvimento (PostgreSQL local);
  a 8080 é reservada para outra aplicação no ambiente.