export interface Produto {
  id: string;
  active: boolean;
  nome: string;
  unidade: string;
  categoria: string;
  estoqueMinimo: number;
}

export interface Periodo {
  id: string;
  active: boolean;
  nome: string;
  dataInicio: string;
  dataFim: string;
  vendas: number | null;
  status: "ABERTO" | "FECHADO";
}

export interface Compra {
  id: string;
  produtoId: string;
  periodoId: string;
  quantidade: number;
  precoUnitario: number;
  dataCompra: string;
}

export interface CompraView extends Compra {
  produtoNome: string;
  unidade: string;
  periodoNome: string;
  total: number;
}

export interface EstoquePeriodo {
  id: string;
  periodoId: string;
  produtoId: string;
  quantidadeInicial: number;
  valorUnitarioInicial: number | null;
  quantidadeFinal: number;
  valorUnitarioFinal: number | null;
}

export interface EstoqueView {
  id: string;
  periodoId: string;
  periodoNome: string;
  produtoId: string;
  produtoNome: string;
  unidade: string;
  quantidadeInicial: number;
  valorUnitarioInicial: number | null;
  valorInicial: number;
  quantidadeFinal: number;
  valorUnitarioFinal: number | null;
  valorFinal: number;
}

export interface ItemRelatorioCMV {
  produtoId: string;
  produtoNome: string;
  unidade: string;
  categoria: string;
  estoqueInicialQtd: number;
  estoqueInicialValor: number;
  comprasQtd: number;
  comprasValorUnitarioMedio: number;
  comprasValor: number;
  estoqueFinalQtd: number;
  estoqueFinalValorUnitario: number;
  estoqueFinalValor: number;
  consumoQtd: number;
  consumoValor: number;
}

export interface RelatorioCMVPeriodo {
  periodoId: string;
  periodoNome: string;
  dataInicio: string;
  dataFim: string;
  vendas: number;
  metaCmv: number;
  totalEstoqueInicial: number;
  totalCompras: number;
  totalEstoqueFinal: number;
  totalConsumo: number;
  cmv: number;
  itens: ItemRelatorioCMV[];
}

export interface RelatorioCMVMensal {
  ano: number;
  mes: number;
  vendas: number;
  totalEstoqueInicial: number;
  totalCompras: number;
  totalEstoqueFinal: number;
  totalConsumo: number;
  cmv: number;
  periodos: RelatorioCMVPeriodo[];
}

export interface AlertaEstoque {
  produtoId: string;
  produtoNome: string;
  unidade: string;
  categoria: string;
  estoqueAtual: number;
  estoqueMinimo: number;
  consumoMedioSemanal: number;
  status: "REPOR" | "ATENCAO" | "OK" | "SEM_DADOS";
}

export interface Dashboard {
  cmvDoMes: number;
  cmvMeta: number;
  consumoDoMes: number;
  vendasDoMes: number;
  totalProdutos: number;
  produtosComEstoqueBaixo: number;
  periodosAbertos: number;
  comprasNoMes: number;
  principaisAlertas: AlertaEstoque[];
}

export interface ConsumoMatriz {
  periodos: { periodoId: string; periodoNome: string; dataInicio: string; dataFim: string }[];
  itens: {
    produtoId: string;
    produtoNome: string;
    unidade: string;
    categoria: string;
    consumoPorPeriodo: (number | null)[];
    consumoTotal: number;
    estoqueMinimo: number;
  }[];
  consumoTotal: number;
}

export interface ResultadoImportacao {
  periodos: number;
  compras: number;
  estoques: number;
  produtosCriados: number;
  produtosAtualizados: number;
  mensagem: string;
}

export interface ItemRecibo {
  descricao: string;
  quantidade: number;
  precoUnitario: number;
}

export interface LeitorReciboResultado extends ResultadoImportacao {
  dataCompra: string | null;
  totalItens: number;
  itens: ItemRecibo[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export type Perfil = "ADMIN" | "COZINHA";

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

export type TipoUso = "USADO" | "ABERTO";

export interface ConsumoDiario {
  id: string;
  data: string;
  produtoId: string;
  produtoNome: string;
  unidade: string;
  tipo: TipoUso;
  quantidade: number;
  usuarioId: string;
  usuarioNome: string;
  dataHoraRegistro: string;
}