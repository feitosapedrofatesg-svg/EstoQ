# estoQ — Modelo de dados

Modelo idealizado de controle de estoque por **lotes** e **movimentações**,
com foco em desperdício, conferência física (balanço) e CMV.

## Entidades

### Usuario (`usuarios`)
Acesso por PIN (6 dígitos) — login por perfil.

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK |
| `ativo` | bool | soft-delete |
| `nome` | varchar | |
| `pin` | varchar | hash (BCrypt) |
| `perfil` | enum | `ADMIN` / `COZINHA` / `NUTRICIONISTA` |

### Sessao (`sessoes`)
Sessões de login (token Bearer).

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK |
| `token` | varchar | único |
| `usuarioId` | FK → `usuarios` | |
| `criadoEm` / `expiraEm` | datetime | |

### Categoria (`categorias`)
Classificação do cardápio/catálogo.

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK |
| `ativo` | bool | soft-delete |
| `nome` | varchar(60) | único, obrigatório |
| `descricao` | varchar(200) | |

### Produto (`produtos`)
| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK |
| `ativo` | bool | soft-delete |
| `nome` | varchar(120) | único |
| `unidadeMedida` | enum | `KG`, `G`, `L`, `ML`, `UN`, `PCT`, `CARTELA`, `CX` |
| `categoriaId` | FK → `categorias` | n-para-1 |

Campos **transientes** (derivados, não persistidos):
- `saldoAtual` = Σ lotes disponíveis + Σ embalagens abertas restantes
- `estoqueMinimo` = valor lido de `ParametroEstoque`

### ParametroEstoque (`parametros_estoque`)
Parâmetros de reposição e consumo médio por produto (1:1 com produto).

| Campo | Tipo | Notas |
|---|---|---|
| `produtoId` | FK → `produtos` | 1:1 |
| `estoqueMinimo` | numeric | nível de alerta |
| `estoqueMedio` | numeric | 1,5 × mínimo (calculado) |
| `estoqueMaximo` | numeric | 2 × mínimo (calculado) |
| `consumoMedioDiario` | numeric | Σ consumo no período ÷ dias |
| `tempoReposicaoDias` | int | dias até repor (padrão 3) |
| `periodoAnaliseDias` | int | janela de consumo (padrão 7) |
| `dataAtualizacao` | datetime | |

`recalcular(produto)` deriva mínimo/médio/máximo a partir do consumo médio diário.

### Lote (`lotes`)
Recebimento de um produto; unidade de rastreio de vencimento e FIFO.

| Campo | Tipo | Notas |
|---|---|---|
| `codigo` | varchar | gerado na entrada (`E<id>`/`AJ`/`S`) |
| `produtoId` | FK → `produtos` | |
| `quantidadeInicial` / `quantidadeAtual` | numeric | |
| `dataEntrada` | date | |
| `dataValidade` | date | opcional |
| `precoUnitario` | numeric | = total pago ÷ quantidade |

Regras: `estaDisponivel()` = ativo e `quantidadeAtual > 0`;
`estaVencido()` = hoje > `dataValidade`; saídas em FIFO por vencimento.

### MovimentacaoEstoque (`movimentacoes_estoque`)
Tabela única (single-table) para entradas, consumos, desperdícios e ajustes.
Coluna discriminadora `tipo_movimentacao`.

| Campo (comum) | Tipo | Notas |
|---|---|---|
| `dataHora` | datetime | |
| `produtoId` | FK → `produtos` | |
| `usuarioId` | FK → `usuarios` | quem lançou |
| `loteId` | FK → `lotes` | lote de origem/destino |
| `quantidade` | numeric(12,3) | |
| `quantidadeAnterior` / `quantidadePosterior` | numeric | saldo do produto |
| `observacao` | varchar | |

Subtipos:
- **Entrada** (`ENTRADA`): gera um `Lote`; campos `valorTotalPago`,
  `unidadeCompra`, `dataValidade`, `precoUnitario`, `loteGeradoId`.
- **Consumo** (`CONSUMO`): baixa primeiro de embalagens abertas e depois em
  FIFO; campo `custoConsumo` (qtd × preço do lote de origem) e
  `produtoAbertoId`.
- **Desperdício** (`DESPERDICIO`): `motivo` + `descricaoMotivo`;
  `valorPrejuizo` = qtd × custo médio.
- **Ajuste** (`AJUSTE`): `diferencaApurada` (+ cria lote de crédito,
  − baixa em FIFO) e `justificativa`; origem opcional em `ItemBalanco`.

### ProdutoAberto (`produtos_abertos`)
Embalagens abertas ainda com saldo (conceito de "fração de lote em uso").

| Campo | Tipo | Notas |
|---|---|---|
| `produtoId` | FK → `produtos` | |
| `loteId` | FK → `lotes` | lote de origem |
| `dataAbertura` | datetime | |
| `quantidadeAberta` / `quantidadeUtilizada` | numeric | |
| `finalizado` | bool | restante zerado |

`quantidadeRestante = quantidadeAberta − quantidadeUtilizada`.
`registrarSobra(qtd)` devolve sobra ao lote (ou cria novo lote de crédito).

### Balanco (`balancos`) e ItemBalanco (`balanco_itens`)
Conferência física periódica. `ItemBalanco.diferenca = quantidadeFisica − quantidadeSistema`.

### ConfiguracaoBalanco (`configuracoes_balanco`)
Regras de periodicidade: `PeriodicidadeBalanco` (`DIARIA`, `SEMANAL`, `MENSAL`),
dia de execução e `proximaExecucao`.

### Alerta (`alertas`)
Alertas gerados por lotes vencendo/vencidos, estoque baixo, balanço pendente
e diferenças apuradas. Duplicidade evitada enquanto não visualizado.

### Relatorio (`relatorios`)
Histórico de relatórios gerados por tipo: `ESTOQUE_ATUAL`, `PROXIMO_VENCIMENTO`,
`VENCIDOS`, `PRODUTOS_ABERTOS`, `DESPERDICIO`, `CONSUMO_MEDIO`.

## Relacionamentos

```
Categoria 1──N Produto 1──1 ParametroEstoque (derivado)
Produto   1──N Lote
Produto   1──N ProdutoAberto N──1 Lote
Produto   1──N MovimentacaoEstoque
         (MovimentacaoEstoque: Entrada/Consumo/Desperdicio/Ajuste herdam)
MovimentacaoEstoque N──1 Usuario;  N──0..1 Lote
Balanco 1──N ItemBalanco N──1 Produto
ConfiguracaoBalanco 1──N Balanco
Alerta N──0..1 Produto / Lote / Balanco
```

## Regras de negócio principais

- **Saldo do produto** = Σ `quantidadeAtual` (lotes disponíveis) + Σ
  `quantidadeRestante` (embalagens abertas não finalizadas).
- **Consumo**: prioriza embalagens abertas e depois lotes em FIFO por
  `dataValidade` (nulos por último). Saldo insuficiente bloqueia a operação.
- **CMV** (por período) = Σ `custoConsumo` dos consumos + Σ `valorPrejuizo`
  dos desperdícios; percentual = CMV ÷ vendas (meta 40%).
- **Conferência**: iniciar gera item por produto com saldo > 0; confirmar
  gera um `Ajuste` (movimentação) para cada item divergente.
- **Produtos com categoria**: a exclusão de uma categoria é bloqueada quando
  há produtos ativos vinculados.

## Catalogação inicial

`backend/src/main/resources/seed/produtos.json` (196 produtos com unidade e
categoria) semeia o banco vazio: cria as categorias e um `ParametroEstoque`
por produto. A importação CSV de produtos usa a mesma lógica
(cria categoria/produto quando ausentes e registra as entradas como lotes).