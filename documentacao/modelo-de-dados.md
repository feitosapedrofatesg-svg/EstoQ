# estoQ — Modelo de dados

## Entidades

### Produto (`produto`)
Catálogo de itens da cozinha.

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK |
| `ativo` | bool | soft-delete |
| `nome` | varchar | único, obrigatório |
| `unidade` | varchar | ex.: `und`, `kg`, `g` |
| `categoria` | varchar | ex.: `Grãos`, `Carnes` |
| `estoqueMinimo` | numeric | estoque mínimo semanal (da aba `CONSUMO`) |

### Periodo (`periodo`)
Período de apuração (tipicamente uma semana).

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK |
| `nome` | varchar | ex.: `Semana 04/12/2023 a 11/12/2023` |
| `dataInicio` | date | |
| `dataFim` | date | |
| `vendas` | numeric | vendas do período (informada manualmente) |
| `status` | enum | `ABERTO` / `FECHADO` |

Fechar um período bloqueia edições de compras/estoque dele.

### Compra (`compra`)
Compra de um produto em um período.

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK |
| `periodoId` | FK → `periodo` | |
| `produtoId` | FK → `produto` | |
| `quantidade` | numeric | |
| `precoUnitario` | numeric | |
| `dataCompra` | date | default = início do período |

Total = `quantidade × precoUnitario`.

### EstoquePeriodo (`estoque_periodo`)
Estoque inicial e final de um produto em um período.

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK |
| `periodoId` | FK → `periodo` | `unique (periodoId, produtoId)` |
| `produtoId` | FK → `produto` | |
| `quantidadeInicial` | numeric | |
| `valorUnitarioInicial` | numeric | herança do período anterior |
| `quantidadeFinal` | numeric | |
| `valorUnitarioFinal` | numeric | = última compra do período |

## Relacionamentos

```
Periodo 1──N Compra N──1 Produto
Periodo 1──N EstoquePeriodo N──1 Produto
         (periodoId, produtoId) único
```

## Regras de valor unitário

- **Estoque final**: valor da última compra do período. Sem compras no
  período, mantém o `valorUnitarioInicial`.
- **Estoque inicial**: valor da última compra do período **anterior**.
  Em períodos novos, `prepararPeriodo` copia `quantidadeFinal` e
  `valorUnitarioFinal` do período anterior como `quantidadeInicial` e
  `valorUnitarioInicial`.

## Cálculo do CMV (consumo)

Para cada produto em um período:

- `consumoQtd = quantidadeInicial + comprasQtd − quantidadeFinal`
- `consumoValor = valorInicial + valorCompras − valorFinal`

Para o período:

- `totalConsumo = Σ consumoValor`
- `cmv = totalConsumo / vendas`

## Catalogação inicial

O catálogo é semeado em `backend/src/main/resources/seed/produtos.json`
(196 produtos com unidade e categoria), inserido automaticamente quando a
tabela está vazia. A importação da planilha cria/atualiza produtos
adicionalmente.