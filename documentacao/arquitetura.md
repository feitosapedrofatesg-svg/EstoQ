# estoQ — Arquitetura

## Visão geral

O **estoQ** é um sistema de gestão de estoque e apuração de CMV (Custo da
Mercadoria Vendida) para operações de alimentação (cozinha), substituindo a
planilha "Planilha de cálculo de CMV Real DEZEMBRO 22.xls".

A aplicação é composta por:

| Componente | Tecnologia | Porta |
|---|---|---|
| Frontend (SPA/PWA) | React 18 + Vite + TypeScript | 5173 (dev) |
| Backend (API REST) | Java 21 + Spring Boot 4 | 8081 |
| Banco de dados | PostgreSQL 16 (Docker) — perfil `dev` | 5433 (host) |

### Modo de entrega (`prod`)
No restaurante o sistema roda como **um único jar autossuficiente**
(`distribuicao/estoq.jar`) com banco **H2 embutido** em arquivo (`./dados/`), que
serve também o build do frontend (estático) — sem Docker/Postgres/Node. O jar é
gerado por `scripts/build-release.sh` (perfil Spring `prod`). O frontend é **PWA**
(`vite-plugin-pwa`): o tablet instala o app em tela cheia. Rotas client-side usam
`HashRouter`, evitando fallback de URL no servidor estático.

## Estrutura de pastas

Estrutura inspirada no projeto `carRepairBack`, adaptada com as pastas extras:

```
estoQ/
├── backend/
│   └── src/main/java/com/estoq/
│       ├── core/          # infraestrutura genérica reutilizável
│       ├── conf/          # configurações (CORS, OpenAPI, seed)
│       ├── business/      # regras de negócio (domínio)
│       ├── api/           # camada de apresentação (controllers REST)
│       ├── integracao/    # importação/exportação (planilha, CSV)
│       └── EstocApplication.java
├── frontend/              # SPA React (Vite)
├── documentacao/          # esta documentação
└── docker-compose.yml     # infraestrutura (PostgreSQL)
```

## Backend

### camada `core`
Infraestrutura genérica usada por todos os domínios:

- `domains/BaseModel` — entidade-base com `id` (UUID), `ativo` e auditoria.
- `dtos/BaseDTO` — DTO-base.
- `services/IGenericService + GenericService` — CRUD genérico (find, insert,
  update, delete, findAllActive paginado).
- `validations/IGenericValidation` — validações por domínio.
- `helpers/IGenericMapper` — conversão entidade ↔ DTO (com `toDtoPage`).
- `controllers/GenericController` — endpoints REST genéricos (`GET`, `POST`,
  `PUT`, `DELETE` por entidade).
- `exceptions/` — `BusinessException` (mensagem + HTTP status) e handler global.

### camada `business`
Domínios de negócio, cada um com `Model`, `DTO`, `Mapper`, `Repository`,
`Service` e `Validation`:

- `produto` — catálogo de itens (nome, unidade, categoria, estoque mínimo).
- `periodo` — períodos de apuração (semanas) com vendas e status (aberto/fechado).
- `compra` — compras por período.
- `estoque` — estoque inicial/final por período (herança automática entre períodos).
- `relatorio` — cálculos de CMV, matriz de consumo e alertas de reposição.
- `usuario` — usuários com perfil `ADMIN`/`COZINHA` e PIN (BCrypt).
- `sessao` — sessões por token (Bearer), expiração configurável (24 h).
- `consumodiario` — registros do Uso Diário (tipo `USADO`/`ABERTO` + quantidade).

### camada `api`
Controllers REST específicos:

- `controllers/` — Compra, Estoque, Produto, Periodo (endpoints customizados).
- `relatorio/RelatorioController` — relatórios e dashboard.
- `dto/` — `CompraView` e `EstoqueView` (projeções com nomes juntos).

### camada `integracao`
- `ImportadorPlanilhaService` — importa a planilha legada (.xls/.xlsx) das abas
  `CMV SEMANA xx`, criando períodos, compras, estoques e produtos.
- `ExportadorCsvService` — exporta o relatório CMV de um período em CSV.
- `IntegracaoController` — expõe `POST /api/integracao/importar-planilha`.

### camada `conf`
- `CorsConfig` — libera o frontend de dev (`http://localhost:5173`).
- `OpenApiConfig` — documentação Swagger UI em `/swagger-ui.html`.
- `SeedCatalogConfig` — insere o catálogo inicial (`seed/produtos.json`),
  196 produtos, quando o banco está vazio.
- `AuthInterceptor` + `WebConfig` — exige `Authorization: Bearer <token>` em
  `/api/**`. Cozinha acessa apenas `/api/consumo-diario/**`, `GET /api/produtos*`
  e `/api/auth/{me,logout}`.
- `PasswordConfig` — `BCryptPasswordEncoder`.
- `SeedUsuariosConfig` — cria os usuários padrão (`000000` Admin, `111111` Cozinha).

## Frontend

SPA React com `react-router-dom`. Rotas:

```
/login          Login por PIN (teclado de 6 dígitos)
/               Dashboard (CMV do mês, alertas, atalhos)
/produtos       CRUD do catálogo
/periodos       CRUD de períodos, fechar período, preparar estoque
/compras        Lançamentos de compra por período
/estoque        Estoque inicial/final por período (edição em grade)
/relatorios     CMV mensal/semanal, matriz de consumo, alertas
/uso-diario     Uso Diário (cozinha) — itens usados/abertos com quantidade
/usuarios       Gestão de PINs (somente admin)
/importar       Upload da planilha legada
```

O frontend consome o backend pela porta 8081 via proxy do Vite
(`/api` → `http://localhost:8081`), evitando CORS em desenvolvimento.

## Fluxo de apuração do CMV

1. Cadastre os produtos (ou use o catálogo inicial).
2. Crie os períodos (semanas) e informe as **vendas** de cada um.
3. Lance as compras de cada período.
4. Informe o **estoque inicial** e **final** por produto (a função
   "preparar estoque" herda o estoque final do período anterior).
5. O sistema calcula o **consumo** = inicial + compras − final e o
   **CMV** = valor do consumo ÷ vendas.

## Decisões e regras

- **Valor unitário do estoque final** = o da última compra do período
  (regra da planilha original). Se não houver compra, usa o valor unitário
  inicial (herdado).
- **Valor unitário do estoque inicial** = valor da última compra do período
  anterior (herança). Para o primeiro período, valor da compra mais antiga
  ou o informado na planilha.
- **Vendas não são importadas da planilha** — são informadas por período
  (os campos da planilha não são confiáveis/estão em branco em alguns casos).
- **Diferença de CMV esperada**: a planilha original soma o estoque final
  com preço em branco (valor 0); o estoQ aplica a regra "última compra", o
  que pode gerar CMV **menor** e mais preciso. Ex.: semana 01 → 0.5568
  (estoQ) vs 0.5698 (planilha).
- **Porta 8081**: a porta 8080 é reservada para outra aplicação no ambiente.