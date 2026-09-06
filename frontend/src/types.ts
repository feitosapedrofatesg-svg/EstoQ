export interface Produto {
  id: string;
  ativo: boolean;
  nome: string;
  unidadeMedida: string;
  categoriaId: string | null;
  categoriaNome: string | null;
  estoqueMinimo: number;
  saldoAtual: number;
  precoUnitario: number | null;
}

export interface Categoria {
  id: string;
  ativo: boolean;
  nome: string | null;
  descricao: string | null;
}

export interface LoteView {
  id: string;
  codigo: string;
  produtoId: string;
  produtoNome: string;
  unidadeMedida: string;
  quantidadeInicial: number;
  quantidadeAtual: number;
  dataEntrada: string;
  dataValidade: string | null;
  precoUnitario: number | null;
  vencido: boolean;
  disponivel: boolean;
  diasParaVencimento: number;
}

export interface ProdutoAbertoView {
  id: string;
  produtoId: string;
  produtoNome: string;
  unidadeMedida: string;
  dataAbertura: string;
  quantidadeAberta: number;
  quantidadeUtilizada: number;
  quantidadeRestante: number;
  finalizado: boolean;
}

export type TipoMovimentacao = "ENTRADA" | "CONSUMO" | "DESPERDICIO" | "AJUSTE";

export interface MovimentacaoView {
  id: string;
  tipo: TipoMovimentacao;
  dataHora: string;
  produtoId: string;
  produtoNome: string;
  unidadeMedida: string;
  quantidade: number;
  quantidadeAnterior: number | null;
  quantidadePosterior: number | null;
  observacao: string | null;
  usuarioNome: string | null;
  loteId: string | null;
  loteCodigo: string | null;
  motivo: string | null;
  valorPrejuizo: number | null;
  custoConsumo: number | null;
  diferencaApurada: number | null;
}

export interface AlertaView {
  id: string;
  tipo: string;
  mensagem: string;
  dataGeracao: string;
  perfilDestino: string;
  visualizado: boolean;
  produtoNome: string | null;
  loteCodigo: string | null;
}

export interface ParametroEstoqueView {
  id: string;
  produtoId: string;
  produtoNome: string;
  unidadeMedida: string;
  estoqueMinimo: number;
  estoqueMedio: number;
  estoqueMaximo: number;
  consumoMedioDiario: number;
  tempoReposicaoDias: number | null;
  periodoAnaliseDias: number | null;
  dataAtualizacao: string | null;
  saldoAtual: number;
}

export interface ConfiguracaoBalancoView {
  id: string | null;
  periodicidade: string;
  diaExecucao: number;
  proximaExecucao: string | null;
  balancosEmAndamento: number;
  pendente: boolean;
}

export interface ItemBalancoView {
  id: string;
  balancoId: string;
  produtoId: string;
  produtoNome: string;
  unidadeMedida: string;
  quantidadeSistema: number;
  quantidadeFisica: number;
  diferenca: number;
}

export type StatusBalanco = "PENDENTE" | "EM_ANDAMENTO" | "CONCLUIDO" | "CANCELADO";

export interface BalancoView {
  id: string;
  dataHora: string;
  tipo: string;
  status: StatusBalanco;
  responsavelNome: string | null;
  itens: ItemBalancoView[];
  qtdItens: number;
  totalDiferenca: number;
}

export interface CMVItemDTO {
  produtoId: string;
  produtoNome: string;
  categoriaNome: string | null;
  unidadeMedida: string | null;
  estoqueInicialQtd: number;
  estoqueInicialValor: number;
  entradasQtd: number;
  entradasValor: number;
  estoqueFinalQtd: number;
  estoqueFinalValor: number;
  consumoQtd: number;
  consumoValor: number;
  desperdicioQtd: number;
  desperdicioValor: number;
  totalValor: number;
}

export interface CMVReportDTO {
  dataInicio: string;
  dataFim: string;
  vendas: number | null;
  metaCmv: number;
  totalConsumo: number;
  totalDesperdicio: number;
  totalGeral: number;
  cmv: number | null;
  avaliacao: string;
  mensagem: string;
  itens: CMVItemDTO[];
}

export interface DashboardDTO {
  ano: number;
  mes: number;
  metaCmv: number;
  cmvMes: number;
  consumoMes: number;
  desperdicioMes: number;
  vendasMes: number;
  metaDesperdicio: number;
  desperdicioPct: number;
  totalProdutos: number;
  produtosComEstoqueBaixo: number;
  lotesVencendo: number;
  lotesVencidos: number;
  entradasSemValor: number;
  balancoPendente: boolean;
  alertasPendentes: number;
  sessoesAtivas: number;
  ultimoBackup: string | null;
  backupEmDia: boolean;
  principaisAlertas: AlertaView[];
  ultimosEventos: AuditLogView[];
}

export interface VendaMes {
  ano: number;
  mes: number;
  valorVendas: number;
}

export interface AuditLogView {
  id: string;
  dataHora: string;
  acao: string;
  entidade: string;
  entidadeId: string | null;
  descricao: string;
  usuarioNome: string;
}

export interface SessaoView {
  id: string;
  usuarioId: string;
  usuarioNome: string;
  criadoEm: string;
  expiraEm: string;
  origem: string | null;
}

export interface BackupStatus {
  ultimoBackup: string | null;
  arquivo: string | null;
  backupEmDia: boolean;
}

export interface RelatorioLinhaDTO {
  chave: string;
  detalhe: string;
  unidadeMedida: string | null;
  quantidade: number | null;
  valor: number | null;
  data: string | null;
  status: string;
}

export interface RelatorioViewDTO {
  id: string;
  tipo: string;
  dataInicio: string | null;
  dataFim: string | null;
  dataGeracao: string;
  linhasGeradas: number;
  linhas: RelatorioLinhaDTO[];
}

export interface EstoquePlanilhaLinha {
  produto: string;
  categoria: string;
  unidade: string;
  saldo: number;
  estoqueMinimo: number;
  custoMedio: number;
  valorEstoque: number;
  situacao: string;
}

export interface EstoquePlanilhaDTO {
  linhas: EstoquePlanilhaLinha[];
  totalSaldo: number;
  totalMinimo: number;
  totalValor: number;
  totalProdutos: number;
  semEstoque: number;
  paraRepor: number;
  geradoEm: string;
}

export interface RelatorioHistorico {
  id: string;
  tipo: string;
  dataInicio: string | null;
  dataFim: string | null;
  dataGeracao: string;
  linhasGeradas: number;
  ativo: boolean;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export type Perfil = "ADMIN" | "COZINHA" | "NUTRICIONISTA";

export interface Usuario {
  id: string;
  nome: string;
  perfil: Perfil;
  active: boolean;
}

export interface LoginResponse {
  token: string;
  nome: string;
  perfil: Perfil;
}

export type MotivoDesperdicio =
  | "VENCIMENTO"
  | "DETERIORACAO"
  | "PREPARO_INCORRETO"
  | "SOBRA_NAO_APROVEITADA"
  | "OUTRO";

export type PeriodicidadeBalanco = "DIARIA" | "SEMANAL" | "MENSAL";

export type TipoRelatorio = "ESTOQUE_ATUAL" | "PROXIMO_VENCIMENTO" | "VENCIDOS" | "PRODUTOS_ABERTOS" | "DESPERDICIO" | "CONSUMO_MEDIO";

export interface ItemCupomLeituraDTO {
  descricao: string;
  quantidade: number;
  precoUnitario: number | null;
  precoTotal: number | null;
  confianca: number | null;
}

export interface CupomLeituraDTO {
  estabelecimento: string | null;
  data: string | null;
  fonte: "QR_CODE" | "XML_NFC_E" | "OCR";
  baixaConfianca: boolean;
  itens: ItemCupomLeituraDTO[];
}