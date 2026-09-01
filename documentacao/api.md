# estoQ — API REST

Base URL: `http://localhost:8081` · Documentação interativa (Swagger UI):
`http://localhost:8081/swagger-ui.html`

## Autenticação (todas as rotas exigem login)

Toda rota `/api/**` exige o header `Authorization: Bearer <token>`.
O papel `COZINHA` só acessa consumo diário, listagem de produtos (GET) e
`/api/auth/{me,logout}`; as demais rotas são do `ADMIN`.

| Método | Caminho | Descrição |
|---|---|---|
| POST | `/api/auth/login` | Corpo `{"pin":"000000"}` → devolve `{token, nome, perfil}` |
| POST | `/api/auth/logout` | Encerra a sessão atual |
| GET | `/api/auth/me` | Usuário logado |
| GET | `/api/usuarios` | Lista usuários (admin) |
| PUT | `/api/usuarios/{id}/pin` | Troca o PIN (`{"pin":"123456"}`, admin) |

PINs iniciais: Admin `000000` · Cozinha `111111`.

## Produtos

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/produtos?size=500` | Lista paginada (`Spring Page`) |
| GET | `/api/produtos/{id}` | Detalhe |
| GET | `/api/produtos/categorias` | Categorias distintas |
| POST | `/api/produtos` | Cria produto |
| PUT | `/api/produtos/{id}` | Atualiza produto |
| DELETE | `/api/produtos/{id}` | Remove (soft delete) |

Corpo (`ProdutoDTO`):

```json
{ "nome": "Arroz 5 Kg", "unidade": "und", "categoria": "Grãos", "estoqueMinimo": 7 }
```

## Períodos

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/periodos/listar` | Lista ordenados (sem paginação) |
| GET | `/api/periodos/{id}` | Detalhe |
| POST | `/api/periodos` | Cria período |
| PUT | `/api/periodos/{id}` | Atualiza período |
| POST | `/api/periodos/{id}/fechar` | Fecha o período (bloqueia edições) |
| DELETE | `/api/periodos/{id}` | Remove |

Corpo (`PeriodoDTO`):

```json
{
  "nome": "Semana 04/12/2023 a 11/12/2023",
  "dataInicio": "2023-12-04",
  "dataFim": "2023-12-11",
  "vendas": 27211.82,
  "status": "ABERTO"
}
```

## Compras

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/compras/periodo/{periodoId}` | Compras do período (`CompraView` com nomes) |
| GET | `/api/compras/{id}` | Detalhe |
| POST | `/api/compras` | Cria compra |
| PUT | `/api/compras/{id}` | Atualiza |
| DELETE | `/api/compras/{id}` | Remove |

Corpo (`CompraDTO`):

```json
{
  "periodoId": "<uuid>",
  "produtoId": "<uuid>",
  "quantidade": 2,
  "precoUnitario": 27.4,
  "dataCompra": "2023-12-04"
}
```

## Estoque

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/estoque/periodo/{periodoId}` | Estoques do período (`EstoqueView`) |
| GET | `/api/estoque/{id}` | Detalhe |
| POST | `/api/estoque` | Cria lançamento |
| PUT | `/api/estoque/{id}` | Atualiza quantidades |
| POST | `/api/estoque/preparar/{periodoId}` | Herda estoque inicial do período anterior → retorna `{ "criados": n }` |
| DELETE | `/api/estoque/{id}` | Remove |

Corpo (`EstoquePeriodoDTO`):

```json
{
  "periodoId": "<uuid>",
  "produtoId": "<uuid>",
  "quantidadeInicial": 2,
  "valorUnitarioInicial": 25.85,
  "quantidadeFinal": 4,
  "valorUnitarioFinal": 27.4
}
```

## Relatórios

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/relatorios/cmv/periodo/{periodoId}` | CMV de um período |
| GET | `/api/relatorios/cmv/mensal?mes=12&ano=2023` | CMV mensal (default 12/2023) |
| GET | `/api/relatorios/consumo/matriz` | Matriz de consumo (produto × período) |
| GET | `/api/relatorios/alertas-estoque` | Alertas de reposição |
| GET | `/api/relatorios/dashboard` | Totais do dashboard |

Saída de `cmv/mensal`:

```json
{
  "ano": 2023, "mes": 12,
  "vendas": ..., "totalEstoqueInicial": ..., "totalCompras": ...,
  "totalEstoqueFinal": ..., "totalConsumo": ..., "cmv": 0.5568,
  "periodos": [ { "periodoId": ..., "periodoNome": ..., "cmv": ..., "itens": [...] } ]
}
```

Saída de `alertas-estoque` (item):

```json
{ "produtoId": ..., "produtoNome": ..., "unidade": ..., "categoria": ...,
  "estoqueAtual": ..., "estoqueMinimo": ..., "consumoMedioSemanal": ...,
  "status": "REPOR" | "ATENCAO" | "OK" | "SEM_DADOS" }
```

## Integração

| Método | Caminho | Descrição |
|---|---|---|
| POST | `/api/integracao/importar-planilha` | `multipart/form-data`, campo `arquivo` (.xls/.xlsx) |
| GET | `/api/integracao/relatorio-cmv/{periodoId}.csv` | Exporta CMV do período em CSV (`Content-Disposition: attachment`) |

Resultado da importação:

```json
{
  "periodos": 4, "compras": 335, "estoques": 436,
  "produtosCriados": 17, "produtosAtualizados": 0,
  "mensagem": "Importação concluída com sucesso."
}
```

## Consumo diário

| Método | Caminho | Descrição |
|---|---|---|
| POST | `/api/consumo-diario` | Registra uso/aberto: `{"data":"2026-08-29","produtoId":"<uuid>","tipo":"USADO","quantidade":2}` |
| GET | `/api/consumo-diario?data=YYYY-MM-DD` | Registros do dia (default hoje) |
| GET | `/api/consumo-diario/hoje` | Registros de hoje |
| GET | `/api/consumo-diario/todas` | Todos os registros |
| GET | `/api/consumo-diario/sumario?data=...` | Totais agrupados por produto+tipo |
| DELETE | `/api/consumo-diario/{id}` | Exclui (cozinha: só os próprios registros de hoje) |

`tipo` ∈ `USADO` | `ABERTO`.

## Erros

Formato de erro (HTTP status correspondente):

```json
{ "title": "<razão>", "message": "<mensagem amigável>" }
```

Ex.: período não encontrado → 404; validação falhou → 400/422; período
fechado → 409 ao tentar editar.