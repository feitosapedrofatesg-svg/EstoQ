# estoQ — Manual de uso

## Subir o sistema

```bash
# 1. Banco de dados (PostgreSQL via Docker)
docker compose up -d postgres

# 2. Backend (porta 8081 em prod; dev usa 8082)
cd backend
mvn spring-boot:run

# 3. Frontend (porta 5173)
cd frontend
npm install
npm run dev
```

Acesse `http://localhost:5173`. API em `http://localhost:8082` (dev) /
`http://localhost:8081` (prod), Swagger em `http://localhost:8081/swagger-ui.html`.

## Login (PIN de 6 dígitos)

- **Administrador** — PIN `000000`: acesso total (relatórios, cadastros,
  movimentações, conferência, usuários).
- **Cozinha** — PIN `111111`: só a tela **Movimentações** (registra consumo,
  entrada e sobras), **Produtos** e **Lotes** em leitura.
- **Nutricionista** — criado pelo admin em **Usuários** (mesmo fluxo de PIN).

## Fluxo de uso do dia a dia

### 1. Produtos
- Lista os itens do catálogo (196 itens iniciais semeado automaticamente) com
  **saldo atual** (soma dos lotes + embalagens abertas) e **estoque mínimo**.
- Ao criar/editar informe nome, **categoria** (cadastre antes em *Categorias*),
  **unidade de medida** e estoque mínimo — o mínimo é espelhado em
  *Parâmetros de estoque*.
- **Recalcular parâmetros** recalcula mínimo/médio/máximo a partir do consumo médio.
- O saldo em **vermelho** indica produto abaixo do mínimo (repor).

### 2. Movimentações (operação do dia)
Abas:
- **Consumo** — baixa o item (primeiro das embalagens abertas, depois FIFO por
  validade). Campos: produto, quantidade, embalagem aberta (opcional),
  observação. É a tela principal da **cozinha**.
- **Entrada** — recebimento de mercadoria: produto, quantidade, valor total
  pago, unidade de compra, validade e observação. Gera um **lote**.

Toda movimentação fica registrada (quem, quando, saldo antes/depois). O admin
pode **reverter** entradas/consumos/desperdícios não usados.

### 3. Lotes
- Lista todos os lotes com situação (disponível/vencendo/vencido).
- **Embalagens abertas**: ao abrir uma embalagem (botão "Abrir embalagem"
  baixa do lote e cria um item aberto), o consumo passa a priorizá-la.
- **Registrar sobra** devolve a sobra ao lote de origem (ou cria um novo lote).

### 4. Desperdício
- Registre perdas com **motivo** (vencimento, deterioração, preparo incorreto,
  sobra não aproveitada, outro) — opcionalmente vinculadas a um lote.
- O **valor do prejuízo** (qtd × custo médio) entra no relatório de CMV.

### 5. Conferência (balanço físico)
- Configure a periodicidade (`DIARIA`/`SEMANAL`/`MENSAL`) e o dia de execução.
- **Iniciar balanço** cria um item por produto com saldo; informe a
  **quantidade física** contada em cada item.
- **Apurar** recalcula o saldo de sistema e mostra as diferenças;
  **Confirmar** gera um **ajuste** (movimentação) por item divergente e conclui.

### 6. Relatórios
- **Dashboard**: consumo do mês, desperdício, % CMV (meta 40%), produtos
  abaixo do mínimo, lotes vencendo/vencidos, balanço pendente e alertas.
- **CMV**: produção por período (data início/fim + vendas opcionais) com
  tabela por produto (consumo + desperdício) e % CMV.
- **Relatórios por tipo**: estoque atual, próximos vencimentos, vencidos,
  produtos abertos, desperdício, consumo médio — exportáveis em CSV.

### 7. Alertas
- Em **Relatórios → Dashboard**, "Gerar alertas" cria alertas de estoque baixo,
  vencimento/vencidos, balanço pendente e diferenças apuradas; marque como
  visualizados os já tratados.

### 8. Importar / exportar CSV
- `importar-produtos`: CSV `produto;categoria;quantidade;valor;datavalidade;unidade`
  cria categorias/produtos ausentes e registra as entradas (lotes).
- `importar-movimentacoes`: CSV `produto;tipo;quantidade;valor`
  (`tipo` = `ENTRADA`/`CONSUMO`).
- Exportação de estoque e CMV em CSV (em Relatórios).

## Sobre o CMV

`CMV = Σ custo dos consumos + Σ valor dos desperdícios (no período) ÷ vendas`.

O custo do consumo é **qtd × preço do lote de origem** (FIFO/embalagem aberta);
o valor do desperdício é **qtd × custo médio do produto**. Vendas são
informadas manualmente na consulta.

## Exportação / dados

- CSV de estoque e CMV via `/api/integracao/exportar/estoque` e
  `/api/integracao/exportar/cmv`.
- Recomeçar (dados de teste): `docker compose down -v && docker compose up -d postgres`,
  reinicie o backend — o catálogo e as categorias são semeados novamente.