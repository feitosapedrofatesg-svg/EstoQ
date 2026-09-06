# estoQ — Visão, Backlog e Roadmap

## 1. Visão do produto

**estoQ** é o controle de estoque e CMV da cozinha — um sistema local (desktop/PWA,
funciona sem internet) que ajuda a cozinha a saber o que tem, o que usou, o que
desperdiçou e quanto custa cada prato.

Para a cozinha de restaurantes, que hoje se vira com planilhas e cadernos, o estoQ
substitui a planilha de movimentações (Produto | Categoria | Unidade | Entrada |
Consumo | Validade) por um fluxo simples de telas, mantendo **a mesma lógica e
mesmos relatórios** que a operação já conhece.

O produto **não é** um sistema de vendas, de pedidos nem de caixa. A integração
com cupom fiscal/PDV hoje é de **leitura** (entrada rápida a partir de um cupom),
com projeção de envio dos dados de consumo de volta ao PDV no roadmap (R3).

### Personas
- **Cozinheiro/Chef de cozinha** — lança entradas, consome itens, abre embalagens,
  confere o balanço diário. Precisa de telas simples e rápidas (PIN curto).
- **Nutricionista/Gerente** — cuida do CMV, desperdício e dos relatórios mensais.
  Precisa de números confiáveis por período.
- **Administrador** — cadastra usuários, controla acessos, audita mudanças e
  garante que os dados estejam sempre copiados (backup).

## 2. Métricas de sucesso
- **CMV %** — consolidado mensal, com meta padrão de 40% e avaliado
  (EXCELENTE/BOM/ATENÇÃO/CRÍTICO) automaticamente.
- **Desperdício %** — participação do desperdício sobre o CMV, com meta
  configurável (padrão 10%).
- **Entradas sem valor** — quantas entradas ainda não têm valor total pago
  (subestimam o custo); meta: zero.
- **Disponibilidade dos dados** — backup automático diário em dia.

## 3. Tema por tipo de usuário

| Persona | Objetivo ao abrir estoQ |
|---|---|
| Cozinha | Registrar entrada/consumo em segundos, ver alertas de validade |
| Nutricionista/Gerente | Abrir o dashboard e ver CMV, desperdício e vendas do mês |
| Admin | Conferir sessões, auditoria, backups e ajustar metas |

## 4. Roadmap e estado de implementação

> **Estado:** implementado no release de produção (136.2026).

### P0 — Corrente crítica de dados (entregue ✔)
- [x] **P0.1 Vendas do mês persistidas** — `/api/vendas/{ano}/{mes}` (GET/PUT).
  O CMV aceita `vendas` por parâmetro; quando o período é um mês calendário
  completo e `vendas` não vem, usa o valor persistido automaticamente.
- [x] **P0.2 Entradas sem valor** — o dashboard conta as entradas sem valor total
  pago; o formulário de entrada avisa antes de lançar sem valor.
- [x] **P0.3 Backup automático diário** — job `@Scheduled` (03:30) gera o dump do
  banco H2 em `backups/` junto ao sistema; também é possível disparar manualmente
  e ver o estado/arquivo no dashboard. Sujeito à propriedade
  `estoq.backup.habilitado`.

### P1 — Confiabilidade e financeiro (entregue ✔)
- [x] **P1.1 Lockout de PIN e trilha de sessões** — 5 falhas bloqueiam o login por
  15 minutos (contabilizando todos os usuários ativos desbloqueados quando o PIN
  não corresponde a ninguém); lista de sessões ativas com origem e revogação;
  sessões expiram em 24h.
- [x] **P1.2 Testes financeiros** — suite Mockito cobrindo FIFO por validade,
  custo do consumo pelo lote principal, uso de embalagem aberta antes dos lotes,
  reversão de entrada intocada (desativa lote), bloqueio de reversão de entrada
  utilizada, reversão de consumo/desperdício creditando lote, e conversão por fator.
- [x] **P1.3 Meta de desperdício configurável** — chave `meta.desperdicio.percentual`
  (padrão 0,10), editável nas Configurações do dashboard; indicador
  "Desperdício vs meta" no dashboard.
- [x] **P1.4 Fator de conversão** — entradas aceitam `fatorConversao` (ex.: 1 CX =
  12 UN) com aviso quando a unidade de compra difere da unidade do produto; a
  quantidade entra convertida no estoque.

### P2 — Auditoria e apresentação (entregue ✔)
- [x] **P2.1 Auditoria de alterações** — registro de login/logout, criação/mudança
  de PIN, entradas, consumos, desperdícios, ajustes, sobras, reversões, produtos
  e metas; últimos eventos visíveis no dashboard e endpoint `/api/auditoria`.
- [x] **P2.2 Botão PDF** — relatórios e CMV têm "Baixar PDF" (`/api/relatorios/pdf/{tipo}`
  e `/api/relatorios/cmv/pdf`), gerado com OpenPDF no padrão dos relatórios de tela.

### R1 — Próximo ciclo (planejado)
- [ ] **Custo do consumo por lote real (running cost)** — hoje o custo do consumo
  usa o preço do lote principal × quantidade total; passar a somar
  `quantidade × preço` em cada lote baixado no FIFO.
- [ ] **Determinação automática do fator de conversão** — sugerir o fator a partir
  das últimas entradas do mesmo produto (aprende com a operação).
- [ ] **Histórico do dashboard** — série mensal de CMV/desperdício/vendas em um gráfico.

### R2 — Multitenancy (depende de decisão externa)
- [ ] Aplicação SaaS com várias cozinhas isoladas (tenant por coluna), se o produto
  deixar de ser local. Exige decisão de produto/hospedagem.

### R3 — Integração com PDV/vendas reais (depende de decisão externa)
- [ ] Receber vendas reais do PDV automaticamente (em vez de digitar o total do mês),
  e enviar consumo/conferência de estoque de volta ao PDV.
  Hoje a integração é somente de **leitura de cupom** (entrada rápida).

## 5. Riscos e mitigação
- **PWA cache do navegador** — o service worker pode servir bundle antigo; usar
  Ctrl+Shift+R após atualizações (documentado no manual).
- **Banco H2 embutido** — o arquivo em `distribuicao/dados/estoq.mv.db` é a única
  fonte de dados em produção; nunca apagar, e o backup automático cobre o diretório.
- **Valores monetários digitados** — o CMV depende de vendas e do preço/valor das
  entradas; entradas sem valor são sinalizadas e o consumo sem custo não depende
  delas (usa o lote).