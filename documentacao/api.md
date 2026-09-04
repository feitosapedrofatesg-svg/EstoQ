# estoQ — API REST

Base URL: `http://localhost:8081` (produção, H2) / `http://localhost:8082` (dev, PostgreSQL).
Swagger UI (se habilitado): `http://localhost:8081/swagger-ui.html`.

## Autenticação (todas as rotas exigem login)

Toda rota `/api/**` exige `Authorization: Bearer <token>`. O papel `COZINHA`
acessa apenas: consumo (`POST /api/movimentacoes/consumo`), abertura de
embalagem (`POST /api/produtos-abertos/abrir`), sobra (`POST
/api/movimentacoes/sobra`) e leituras (GET) de `/api/produtos`, `/api/lotes`,
`/api/produtos-abertos`, `/api/movimentacoes`, `/api/categorias` e
`/api/auth/{me,logout}`. `NUTRICIONISTA` e `ADMIN` usam as demais rotas.

| Método | Caminho | Descrição |
|---|---|---|
| POST | `/api/auth/login` | `{"pin":"000000"}` → `{token, nome, perfil}` |
| POST | `/api/auth/logout` | Encerra sessão |
| GET | `/api/auth/me` | Usuário logado |

PINs iniciais: Admin `000000` · Cozinha `111111`.

## Categorias

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/categorias?page=0&size=500` | Lista paginada |
| GET | `/api/categorias/{id}` | Detalhe |
| POST | `/api/categorias` | Cria (`{"nome":"Aves","descricao":...}`) |
| PUT | `/api/categorias/{id}` | Atualiza |
| DELETE | `/api/categorias/{id}` | Bloqueada se houver produtos vinculados |

## Produtos

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/produtos?page=0&size=500` | Lista paginada (com `saldoAtual`) |
| GET | `/api/produtos/{id}` | Detalhe |
| GET | `/api/produtos/categorias` | Nomes das categorias |
| GET | `/api/produtos/opcoes-unidade` | Valores de `UnidadeMedida` |
| POST | `/api/produtos` | Cria produto |
| PUT | `/api/produtos/{id}` | Atualiza |
| DELETE | `/api/produtos/{id}` | Remove (soft delete) |

Corpo (`ProdutoDTO`):

```json
{
  "nome": "Café em pó - 500g",
  "unidadeMedida": "UN",
  "categoriaId": "<uuid>",
  "categoriaNome": "Bebidas",
  "estoqueMinimo": 7,
  "saldoAtual": 12.5
}
```

`estoqueMinimo` enviado no produto é espelhado em `ParametroEstoque`.

## Lotes

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/lotes` | Todos os lotes ativos ordenados por validade |
| GET | `/api/lotes/disponiveis?produtoId=` | Lotes com saldo (`> 0`) |
| GET | `/api/lotes/vencendo?produtoId=&dias=7` | Vencem em até `dias` |
| GET | `/api/lotes/vencidos?produtoId=` | Vencidos |

`LoteView`: `{ id, codigo, produtoId, produtoNome, unidadeMedida,
quantidadeInicial, quantidadeAtual, dataEntrada, dataValidade, precoUnitario,
vencido, disponivel, diasParaVencimento }`.

## Movimentações

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/movimentacoes?produtoId=&inicio=YYYY-MM-DD&fim=YYYY-MM-DD` | Lista |
| POST | `/api/movimentacoes/entrada` | Recebe produto → cria lote |
| POST | `/api/movimentacoes/consumo` | Consumo (emb. abertas 1º, depois FIFO) |
| POST | `/api/movimentacoes/desperdicio` | Desperdício com motivo |
| POST | `/api/movimentacoes/ajuste` | Ajuste (diferença ±, opcional do balanço) |
| POST | `/api/movimentacoes/sobra` | Devolve sobra de embalagem ao estoque |
| POST | `/api/movimentacoes/{id}/reverter` | Reverte entrada/consumo/desperdício |

Corpos de exemplo:

```json
// entrada
{ "produtoId": "<uuid>", "quantidade": 5, "valorTotalPago": 87.5,
  "unidadeCompra": "KG", "dataValidade": "2026-10-01", "observacao": "" }
// consumo
{ "produtoId": "<uuid>", "quantidade": 1.5, "produtoAbertoId": null, "observacao": "" }
// desperdício (motivo ∈ VENCIMENTO|DETERIORACAO|PREPARO_INCORRETO|SOBRA_NAO_APROVEITADA|OUTRO)
{ "produtoId": "<uuid>", "quantidade": 0.8, "motivo": "DETERIORACAO",
  "descricaoMotivo": "", "loteId": null, "observacao": "" }
// ajuste
{ "produtoId": "<uuid>", "diferenca": -2.3, "justificativa": "Conferência", "observacao": "" }
// sobra
{ "produtoAbertoId": "<uuid>", "quantidade": 0.5 }
```

`MovimentacaoView`: `{ id, tipo, dataHora, produtoId, produtoNome,
unidadeMedida, quantidade, quantidadeAnterior, quantidadePosterior,
observacao, usuarioNome, loteId, loteCodigo, motivo, valorPrejuizo,
custoConsumo, diferencaApurada }`.

## Embalagens abertas

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/produtos-abertos?produtoId=` | Embalagens abertas não finalizadas |
| POST | `/api/produtos-abertos/abrir` | `{produtoId, quantidade, loteId?}` (baixa lote e "abre") |

## Parâmetros de estoque

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/parametros-estoque/produto/{id}` | Parâmetros do produto |
| PUT | `/api/parametros-estoque/produto/{id}` | `{tempoReposicaoDias, periodoAnaliseDias, estoqueMinimo}` |
| POST | `/api/parametros-estoque/produto/{id}/recalcular` | Recalcula níveis pelo consumo |
| POST | `/api/parametros-estoque/recalcular-todos` | Recalcula todos → `{atualizados: n}` |
| GET | `/api/parametros-estoque/produtos-baixos` | `{qtd: n}` abaixo do mínimo |

## Conferência física

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/conferencia/configuracao` | Configuração (ou `null`) |
| PUT | `/api/conferencia/configuracao` | `{periodicidade, diaExecucao}` (`DIARIA\|SEMANAL\|MENSAL`) |
| GET | `/api/conferencia/balancos` | Lista de balanços |
| POST | `/api/conferencia/balancos/iniciar` | Cria balanço com item por produto com saldo |
| GET | `/api/conferencia/balancos/{id}` | Balanço + itens |
| PUT | `/api/conferencia/balancos/{id}/itens/{itemId}` | `{quantidadeFisica}` |
| POST | `/api/conferencia/balancos/{id}/apurar` | Atualiza saldo de sistema e apura diferenças |
| POST | `/api/conferencia/balancos/{id}/confirmar` | Gera ajustes das divergências e conclui |

## Alertas

| Método | Caminho | Descrição |
|---|---|---|
| POST | `/api/alertas/gerar` | Gera alertas (estoque baixo, venc./vencimento, balanço) → `{gerados}` |
| GET | `/api/alertas` | Todos |
| GET | `/api/alertas/pendentes` | Não visualizados |
| GET | `/api/alertas/pendentes/contar` | `{qtd}` |
| PUT | `/api/alertas/{id}/visualizado` | Marca como visto |

## Relatórios

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/relatorios/cmv?inicio=&fim=&vendas=` | CMV do período (consumo + desperdício ÷ vendas) |
| POST | `/api/relatorios/{tipo}?inicio=&fim=` | Gera relatório de um `tipo` (persiste) |
| GET | `/api/relatorios/historico` | Relatórios persistidos |
| GET | `/api/relatorios/dashboard?ano=&mes=` | Indicadores (default 12/2023) |

`tipo` ∈ `ESTOQUE_ATUAL` | `PROXIMO_VENCIMENTO` | `VENCIDOS` |
`PRODUTOS_ABERTOS` | `DESPERDICIO` | `CONSUMO_MEDIO`.

Saída de `cmv`:

```json
{
  "dataInicio": "2026-09-01", "dataFim": "2026-09-30",
  "vendas": 12000, "metaCmv": 0.4,
  "totalConsumo": ..., "totalDesperdicio": ..., "totalGeral": ..., "cmv": 0.35,
  "itens": [ { "produtoId": ..., "produtoNome": ..., "categoriaNome": ...,
               "unidadeMedida": "KG", "entradasQtd": ..., "entradasValor": ...,
               "consumoQtd": ..., "consumoValor": ..., "desperdicioQtd": ...,
               "desperdicioValor": ..., "totalValor": ... } ]
}
```

Imprimir/preencher o BALANÇO e o dashboard:

```json
{ "ano": 2026, "mes": 9, "metaCmv": 0.4, "cmvMes": ..., "consumoMes": ...,
  "desperdicioMes": ..., "totalProdutos": 196, "produtosComEstoqueBaixo": 12,
  "lotesVencendo": 3, "lotesVencidos": 1, "balancoPendente": true,
  "alertasPendentes": 5, "principaisAlertas": [ ...AlertaView... ] }
```

## Integração (CSV)

| Método | Caminho | Descrição |
|---|---|---|
| POST | `/api/integracao/importar-produtos` | `multipart`, campo `arquivo` (CSV `produto;categoria;quantidade;valor;datavalidade;unidade`) |
| POST | `/api/integracao/importar-movimentacoes` | `multipart` (CSV `produto;tipo;quantidade;valor` — `tipo` = `ENTRADA`/`CONSUMO`) |
| GET | `/api/integracao/exportar/estoque` | CSV de produtos com saldo e mínimo |
| GET | `/api/integracao/exportar/cmv?inicio=&fim=` | CSV do CMV |

CSVs aceitam `;` ou `,` como separador; `data` em `dd/MM/yyyy`. Resultado:
`{ importadas, ignoradas, erros[] }`.

## Erros

Formato (HTTP status correspondente):

```json
{ "title": "<razão>", "message": "<mensagem amigável>" }
```

Ex.: produto não encontrado → 404; validação falhou → 400/422; categoria com
produtos → 409; sessão inválida → 401; COZINHA em rota restrita → 403.