# estoQ — Manual de uso

## Subir o sistema

```bash
# 1. Banco de dados (PostgreSQL via Docker)
docker compose up -d postgres

# 2. Backend (porta 8081)
cd backend
mvn spring-boot:run

# 3. Frontend (porta 5173)
cd frontend
npm install
npm run dev
```

Acesse `http://localhost:5173`. API em `http://localhost:8081`, Swagger em
`http://localhost:8081/swagger-ui.html`.

> A porta 8080 é usada por outra aplicação no ambiente; por isso o backend
> roda na **8081** (configurado em `application.properties`).

## Login (PIN de 6 dígitos)

- **Administrador** — PIN `000000`: acesso total (relatórios, cadastros, importação).
- **Cozinha** — PIN `111111`: tela de **Uso diário** (registra itens usados e abertos do dia).
- O admin pode trocar os PINs em **Usuários** (sidebar).

## Fluxo de uso

### 1. Produtos
- Lista com 196 itens do catálogo inicial (semeado automaticamente).
- Crie edições com o botão **+ Novo produto** (nome, unidade, categoria,
  estoque mínimo semanal).
- Busque por nome e filtre por categoria.

### 2. Períodos
- Crie um período por semana informando datas e **vendas** (R$ vendidos no
  período — não vêm da planilha).
- **Preparar estoque**: preenche os estoques iniciais dos produtos com o
  estoque final do período anterior (automatiza a manutenção semanal).
- **Fechar** bloqueia edições do período (trava o resultado apurado).

### 3. Compras
- Selecione o período, adicione cada compra (produto, quantidade, preço,
  data). O total é calculado automaticamente.
- Período fechado não permite edição.

### 4. Estoque
- Edite em grade o **estoque inicial** e **final** de cada produto na semana.
- A coluna "consumo sugerido" mostra inicial − final.
- **Salvar alterações** persiste tudo de uma vez.

### 5. Relatórios
- **CMV**: uma aba com o CMV do mês (12/2023 por padrão) e o detalhe por
  período (clique nos períodos). A tabela detalhada mostra consumo por
  produto, e o rodapé mostra CMV e totais (inicial + compras − final).
- **Matriz de consumo**: consumo de cada produto em cada período.
- **Alertas de estoque**: status por produto em relação ao mínimo e ao
  consumo médio semanal (`REPOR`, `ATENCAO`, `OK`, `SEM_DADOS`).

### 6. Uso diário (cozinha / tablet)
- A **cozinha** informa os **itens usados no dia** e os **itens abertos**
  (embalagens abertas): escolhe o produto, ajusta a quantidade e toca em
  **"Usado hoje"** ou **"Item aberto"**.
- Cada registro guarda produto, quantidade, tipo e quem fez (auditoria).
- O **admin** vê os mesmos registros em **Uso diário** pelo menu e pode
  excluir qualquer um; a cozinha só pode excluir os que criou no dia.

### 7. Importar planilha
- Envie a planilha legada do dez/2022 (.xls/.xlsx). O sistema lê as abas
  `CMV SEMANA xx`, importa períodos, compras e estoques, e cadastra produtos
  novos. As **vendas** continuam sendo informadas manualmente em Períodos.

## Significado dos status de alerta

| Status | Significado |
|---|---|
| `REPOR` | estoque atual ≤ estoque mínimo → comprar |
| `ATENCAO` | estoque atual acima do mínimo, mas abaixo do consumo médio semanal |
| `OK` | estoque saudável (≥ consumo médio) |
| `SEM_DADOS` | sem estoque/consumo suficiente para calcular |

## Sobre o CMV

`CMV = (estoque inicial + compras − estoque final) ÷ vendas`.

Se o CMV vier **diferente** do valor da planilha antiga, a provável causa é:
a planilha deixava o preço do estoque final em branco (somava como 0); o
estoQ usa o preço da última compra do período (regra da própria planilha,
anotada nas abas). Resultado: um CMV **menor e mais exato**. Ex.: semana 01
→ 0.5568 (estoQ) vs 0.5698 (planilha).

## Exportação / dados

- Relatório CMV de um período pode ser exportado em CSV:
  `GET http://localhost:8081/api/integracao/relatorio-cmv/{periodoId}.csv`.
- Para recomeçar do zero (dados de teste): `docker compose down -v && docker compose up -d postgres` e reinicie o backend — o catálogo é semeado novamente.