import { useEffect, useState } from "react";
import { api, download, formatDate, formatDateTime, formatMoney, formatPercent, formatQtd, hojeIso, inicioMesIso } from "../api";
import { useAuth } from "../auth";
import { Badge, Gauge, Stat, useAsyncData } from "../components";
import { CountUp, useToast, CardsSkeleton } from "../ux";
import type { AlertaView, CMVReportDTO, DashboardDTO, EstoquePlanilhaDTO, RelatorioViewDTO, TipoRelatorio } from "../types";

const tiposRelatorio: { valor: TipoRelatorio; label: string }[] = [
  { valor: "ESTOQUE_ATUAL", label: "Estoque atual" },
  { valor: "PROXIMO_VENCIMENTO", label: "Próximo vencimento" },
  { valor: "VENCIDOS", label: "Vencidos" },
  { valor: "PRODUTOS_ABERTOS", label: "Produtos abertos" },
  { valor: "DESPERDICIO", label: "Desperdício" },
  { valor: "CONSUMO_MEDIO", label: "Consumo médio" },
];

const mesLabel = (m: number) =>
  ["Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"][m - 1] || String(m);

function escHtml(s: string | number | null | undefined): string {
  return String(s ?? "").replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c] as string
  );
}

function abrirImpressao(titulo: string, sub: string, corpoHtml: string): boolean {
  const w = window.open("", "_blank", "width=940,height=680");
  if (!w) {
    return false;
  }
  const html = `<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<title>EstoQ — ${escHtml(titulo)}</title>
<style>
  body { font-family: Arial, Helvetica, sans-serif; font-size: 12px; color: #222; }
  h1 { font-size: 16px; margin: 0 0 2px; }
  .sub { color: #666; margin: 0 0 12px; font-size: 11px; }
  table { border-collapse: collapse; width: 100%; }
  th, td { border: 1px solid #bbb; padding: 5px 7px; text-align: left; }
  th { background: #f1f1f1; }
  .num { text-align: right; font-variant-numeric: tabular-nums; }
  tfoot td { font-weight: bold; background: #f8f8f8; }
  .resumo { margin-top: 12px; font-weight: bold; }
  .resumo-linhas { margin-top: 4px; color: #444; }
  .avaliacao { margin-top: 12px; padding: 8px 10px; border: 1px solid #bbb; background: #fafafa; }
  .avaliacao strong { display: block; margin-bottom: 2px; }
  .empty { color: #777; font-style: italic; }
  @media print { .no-print { display: none; } }
</style>
</head>
<body>
  <h1>EstoQ — ${escHtml(titulo)}</h1>
  <div class="sub">${sub}</div>
  ${corpoHtml}
  <script>
    window.onload = function () { window.focus(); window.print(); };
  <\/script>
</body>
</html>`;
  w.document.write(html);
  w.document.close();
  return true;
}

function imprimirRelatorio(r: RelatorioViewDTO) {
  const titulo = tiposRelatorio.find((t) => t.valor === r.tipo)?.label || r.tipo;
  const totalQtd = r.linhas.reduce((s, l) => s + (l.quantidade ?? 0), 0);
  const totalValor = r.linhas.reduce((s, l) => s + (l.valor ?? 0), 0);
  const corpo =
    r.linhas.length === 0
      ? `<tr><td colspan="7" class="empty">Nenhuma linha gerada.</td></tr>`
      : r.linhas
          .map(
            (l) =>
              `<tr><td><strong>${escHtml(l.chave)}</strong></td><td>${escHtml(l.detalhe) || "—"}</td>` +
              `<td>${escHtml(l.unidadeMedida) || "—"}</td><td class="num">${formatQtd(l.quantidade)}</td>` +
              `<td class="num">${formatMoney(l.valor)}</td><td>${formatDate(l.data)}</td>` +
              `<td>${escHtml(l.status)}</td></tr>`
          )
          .join("");

  const tabela = `<table>
    <thead><tr><th>Item</th><th>Detalhe</th><th>Unidade</th><th class="num">Quantidade</th><th class="num">Valor</th><th>Data</th><th>Status</th></tr></thead>
    <tbody>${corpo}</tbody>
    <tfoot><tr><td colspan="3">Total</td><td class="num">${formatQtd(totalQtd)}</td><td class="num">${formatMoney(totalValor)}</td><td colspan="2"></td></tr></tfoot>
  </table>`;
  return abrirImpressao(
    titulo,
    `Período: ${formatDate(r.dataInicio)} a ${formatDate(r.dataFim)} · Gerado em ${formatDateTime(r.dataGeracao)} · ${r.linhasGeradas} linha(s)`,
    tabela
  );
}

function imprimirCmv(c: CMVReportDTO) {
  const fp = (num: number, den: number) => formatPercent(num && den ? num / den : 0);
  const totalEi = c.itens.reduce((s, i) => s + i.estoqueInicialQtd, 0);
  const totalEiV = c.itens.reduce((s, i) => s + i.estoqueInicialValor, 0);
  const totalEn = c.itens.reduce((s, i) => s + i.entradasQtd, 0);
  const totalEnV = c.itens.reduce((s, i) => s + i.entradasValor, 0);
  const totalEf = c.itens.reduce((s, i) => s + i.estoqueFinalQtd, 0);
  const totalEfV = c.itens.reduce((s, i) => s + i.estoqueFinalValor, 0);
  const totalCoQ = c.itens.reduce((s, i) => s + i.consumoQtd, 0);
  const totalDeQ = c.itens.reduce((s, i) => s + i.desperdicioQtd, 0);
  const corpo =
    c.itens.length === 0
      ? `<tr><td colspan="16" class="empty">Sem movimentações no período.</td></tr>`
      : c.itens
          .map(
            (i) =>
              `<tr><td><strong>${escHtml(i.produtoNome)}</strong></td><td>${escHtml(i.categoriaNome) || "—"}</td>` +
              `<td>${escHtml(i.unidadeMedida) || "—"}</td><td class="num">${formatQtd(i.estoqueInicialQtd)}</td>` +
              `<td class="num">${formatMoney(i.estoqueInicialValor)}</td><td class="num">${formatQtd(i.entradasQtd)}</td>` +
              `<td class="num">${formatMoney(i.entradasValor)}</td><td class="num">${formatQtd(i.estoqueFinalQtd)}</td>` +
              `<td class="num">${formatMoney(i.estoqueFinalValor)}</td><td class="num">${formatQtd(i.consumoQtd)}</td>` +
              `<td class="num">${formatMoney(i.consumoValor)}</td><td class="num">${formatQtd(i.desperdicioQtd)}</td>` +
              `<td class="num">${formatMoney(i.desperdicioValor)}</td><td class="num">${formatMoney(i.totalValor)}</td>` +
              `<td class="num">${fp(i.desperdicioValor, i.totalValor)}</td><td class="num">${fp(i.totalValor, c.totalGeral)}</td></tr>`
          )
          .join("");

  const tabela = `<table>
    <thead><tr>
      <th>Produto</th><th>Categoria</th><th>Unidade</th><th class="num">Est. Inicial (Qtd)</th><th class="num">Est. Inicial (R$)</th>
      <th class="num">Entradas (Qtd)</th><th class="num">Entradas (R$)</th><th class="num">Est. Final (Qtd)</th><th class="num">Est. Final (R$)</th>
      <th class="num">Consumo (Qtd)</th><th class="num">Consumo (R$)</th><th class="num">Desp. (Qtd)</th><th class="num">Desp. real (R$)</th>
      <th class="num">CMV (R$)</th><th class="num">Desp % CMV</th><th class="num">% CMV total</th>
    </tr></thead>
    <tbody>${corpo}</tbody>
    <tfoot><tr>
      <td colspan="3">Total</td>
      <td class="num">${formatQtd(totalEi)}</td><td class="num">${formatMoney(totalEiV)}</td>
      <td class="num">${formatQtd(totalEn)}</td><td class="num">${formatMoney(totalEnV)}</td>
      <td class="num">${formatQtd(totalEf)}</td><td class="num">${formatMoney(totalEfV)}</td>
      <td class="num">${formatQtd(totalCoQ)}</td><td class="num">${formatMoney(c.totalConsumo)}</td>
      <td class="num">${formatQtd(totalDeQ)}</td><td class="num">${formatMoney(c.totalDesperdicio)}</td>
      <td class="num">${formatMoney(c.totalGeral)}</td>
      <td class="num">${formatPercent(c.totalGeral ? c.totalDesperdicio / c.totalGeral : 0)}</td>
      <td class="num">100%</td>
    </tr></tfoot>
  </table>`;
  const resumo = `Consumo: ${formatMoney(c.totalConsumo)} · Desperdício real: ${formatMoney(c.totalDesperdicio)} · ` +
    `Desp. % do CMV: ${formatPercent(c.totalGeral ? c.totalDesperdicio / c.totalGeral : 0)} · CMV total: ${formatMoney(c.totalGeral)}` +
    (c.vendas ? ` · Vendas: ${formatMoney(c.vendas)} (% CMV: ${formatPercent(c.cmv)})` : "");
  const avaliacao = `<div class="avaliacao"><strong>${escHtml(c.avaliacao === "SEM_VENDAS" ? "Sem vendas" : "Avaliação")}</strong>${escHtml(c.mensagem)}</div>`;
  return abrirImpressao(
    "Relatório CMV",
    `Período: ${formatDate(c.dataInicio)} a ${formatDate(c.dataFim)} · Gerado em ${formatDateTime(new Date().toISOString())}`,
    tabela + `<p class="resumo">${escHtml(resumo)}</p>` + avaliacao
  );
}

function renderImpressaoEstoque(d: EstoquePlanilhaDTO) {
  const corpo =
    d.linhas.length === 0
      ? `<tr><td colspan="9" class="empty">Nenhum produto cadastrado.</td></tr>`
      : d.linhas
          .map(
            (l) =>
              `<tr><td><strong>${escHtml(l.produto)}</strong></td><td>${escHtml(l.categoria) || "—"}</td>` +
              `<td>${escHtml(l.unidade) || "—"}</td><td class="num">${formatQtd(l.saldo)}</td>` +
              `<td class="num">${formatQtd(l.estoqueMinimo)}</td><td class="num">${formatMoney(l.custoMedio)}</td>` +
              `<td class="num">${formatMoney(l.valorEstoque)}</td><td class="num">${formatPercent(l.valorEstoque && d.totalValor ? l.valorEstoque / d.totalValor : 0)}</td>` +
              `<td>${escHtml(l.situacao)}</td></tr>`
          )
          .join("");

  const tabela = `<table>
    <thead><tr>
      <th>Produto</th><th>Categoria</th><th>Unidade</th><th class="num">Saldo</th><th class="num">Estoque mínimo</th>
      <th class="num">Custo médio (R$)</th><th class="num">Valor do estoque (R$)</th><th class="num">% do valor</th><th>Situação</th>
    </tr></thead>
    <tbody>${corpo}</tbody>
    <tfoot><tr>
      <td colspan="3">Total</td>
      <td class="num">${formatQtd(d.totalSaldo)}</td><td class="num">${formatQtd(d.totalMinimo)}</td>
      <td class="num"></td><td class="num">${formatMoney(d.totalValor)}</td><td class="num">100%</td><td></td>
    </tr></tfoot>
  </table>`;
  const resumo =
    `<p class="resumo">Valor total do estoque: ${formatMoney(d.totalValor)}</p>` +
    `<p class="resumo-linhas">Produtos: ${d.totalProdutos} · Sem estoque: ${d.semEstoque} · Para repor: ${d.paraRepor}` +
    `<br/>Legenda: OK = saldo ≥ estoque mínimo; REPOR = saldo &lt; estoque mínimo; SEM ESTOQUE = saldo ≤ 0.</p>`;
  return abrirImpressao(
    "Relatório de Estoque",
    `Gerado em ${escHtml(d.geradoEm)} · ${d.totalProdutos} produto(s)`,
    tabela + resumo
  );
}

export default function Relatorios() {
  const { toast } = useToast();
  const { auth } = useAuth();
  const admin = auth?.perfil === "ADMIN";
  const agora = new Date();
  const [ano, setAno] = useState(agora.getFullYear());
  const [mes, setMes] = useState(agora.getMonth() + 1);
  const { data: dash, error: dashErro, loading: dashLoading, reload: reloadDash, setData: setDash } =
    useAsyncData<DashboardDTO>(
      () => api.get(`/api/relatorios/dashboard?ano=${ano}&mes=${mes}`),
      [ano, mes]
    );

  // CMV
  const [iniCmv, setIniCmv] = useState(inicioMesIso());
  const [fimCmv, setFimCmv] = useState(hojeIso());
  const [vendasCmv, setVendasCmv] = useState("");
  const [cmv, setCmv] = useState<CMVReportDTO | null>(null);
  const [cmvLoading, setCmvLoading] = useState(false);
  const [cmvErro, setCmvErro] = useState("");

  async function gerarCmv() {
    setCmvLoading(true);
    setCmvErro("");
    try {
      const params = new URLSearchParams({ inicio: iniCmv, fim: fimCmv });
      if (vendasCmv) params.set("vendas", vendasCmv.replace(",", "."));
      setCmv(await api.get<CMVReportDTO>(`/api/relatorios/cmv?${params}`));
    } catch (e: unknown) {
      setCmvErro((e as Error).message || "Erro ao gerar CMV.");
    } finally {
      setCmvLoading(false);
    }
  }

  // relatório por tipo
  const [tipo, setTipo] = useState<TipoRelatorio>("ESTOQUE_ATUAL");
  const [iniRep, setIniRep] = useState(inicioMesIso());
  const [fimRep, setFimRep] = useState(hojeIso());
  const [rel, setRel] = useState<RelatorioViewDTO | null>(null);
  const [relLoading, setRelLoading] = useState(false);
  const [relErro, setRelErro] = useState("");

  async function gerarRelatorio() {
    setRelLoading(true);
    setRelErro("");
    try {
      const params = new URLSearchParams({ inicio: iniRep, fim: fimRep });
      setRel(await api.post<RelatorioViewDTO>(`/api/relatorios/${tipo}?${params}`));
    } catch (e: unknown) {
      setRelErro((e as Error).message || "Erro ao gerar relatório.");
    } finally {
      setRelLoading(false);
    }
  }

  // vendas do mês, meta de desperdício, meta de CMV e backup
  const [vendasMesInput, setVendasMesInput] = useState("");
  const [metaDespInput, setMetaDespInput] = useState("10");
  const [metaCmvInput, setMetaCmvInput] = useState("40");
  const [salvandoSistema, setSalvandoSistema] = useState(false);
  const [pdfLoading, setPdfLoading] = useState(false);
  const [mensagemSistema, setMensagemSistema] = useState<{ tipo: "ok" | "danger"; msg: string } | null>(null);

  useEffect(() => {
    if (dash) {
      setVendasMesInput(dash.vendasMes ? String(dash.vendasMes) : "");
      setMetaDespInput(
        Math.round(dash.metaDesperdicio * 1000) / 10 + ""
      );
      setMetaCmvInput(Math.round(dash.metaCmv * 1000) / 10 + "");
    }
  }, [dash]);

  async function salvarVendasMes() {
    setSalvandoSistema(true);
    setMensagemSistema(null);
    try {
      await api.put(`/api/vendas/${ano}/${mes}`, { valor: vendasMesInput.replace(",", ".") || null });
      setMensagemSistema({ tipo: "ok", msg: "Vendas do mês salvas. O % de CMV usa automaticamente esse valor." });
      reloadDash();
    } catch (e: unknown) {
      setMensagemSistema({ tipo: "danger", msg: (e as Error).message || "Erro ao salvar vendas do mês." });
    } finally {
      setSalvandoSistema(false);
    }
  }

  async function salvarMetaDesperdicio() {
    setSalvandoSistema(true);
    setMensagemSistema(null);
    try {
      await api.put("/api/configuracoes/meta.desperdicio.percentual", {
        valor: metaDespInput.replace(",", ".") || "10",
      });
      setMensagemSistema({ tipo: "ok", msg: "Meta de desperdício atualizada." });
      reloadDash();
    } catch (e: unknown) {
      setMensagemSistema({ tipo: "danger", msg: (e as Error).message || "Erro ao salvar a meta de desperdício." });
    } finally {
      setSalvandoSistema(false);
    }
  }

  async function salvarMetaCmv() {
    setSalvandoSistema(true);
    setMensagemSistema(null);
    try {
      await api.put("/api/configuracoes/meta.cmv.percentual", {
        valor: metaCmvInput.replace(",", ".") || "40",
      });
      setMensagemSistema({ tipo: "ok", msg: "Meta de % CMV atualizada." });
      reloadDash();
    } catch (e: unknown) {
      setMensagemSistema({ tipo: "danger", msg: (e as Error).message || "Erro ao salvar a meta de % CMV." });
    } finally {
      setSalvandoSistema(false);
    }
  }

  async function fazerBackup() {
    setSalvandoSistema(true);
    setMensagemSistema(null);
    try {
      const r = await api.post<{ sucesso: boolean; arquivo?: string; mensagem?: string }>("/api/sistema/backup");
      if (r && r.sucesso) {
        setMensagemSistema({ tipo: "ok", msg: `Backup criado: ${r.arquivo || ""}` });
      } else {
        setMensagemSistema({ tipo: "danger", msg: (r && r.mensagem) || "Falha ao fazer o backup." });
      }
      reloadDash();
    } catch (e: unknown) {
      setMensagemSistema({ tipo: "danger", msg: (e as Error).message || "Erro ao fazer o backup." });
    } finally {
      setSalvandoSistema(false);
    }
  }

  async function baixarPdf(rota: string, nome: string) {
    setPdfLoading(true);
    try {
      await download(rota, nome, { method: "POST" });
    } catch (e: unknown) {
      setMensagemSistema({ tipo: "danger", msg: (e as Error).message || "Erro ao gerar o PDF." });
    } finally {
      setPdfLoading(false);
    }
  }

  async function pdfCmv() {
    const p = new URLSearchParams({ inicio: iniCmv, fim: fimCmv });
    if (vendasCmv) p.set("vendas", vendasCmv.replace(",", "."));
    await baixarPdf(`/api/relatorios/cmv/pdf?${p}`, "cmv.pdf");
  }

  async function pdfRel(t: TipoRelatorio) {
    const p = new URLSearchParams({ inicio: iniRep, fim: fimRep });
    await baixarPdf(`/api/relatorios/pdf/${t}?${p}`, "relatorio.pdf");
  }

  useEffect(() => {
    gerarCmv();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const cmvPeriodoInvalido = iniCmv > fimCmv;
  const relPeriodoInvalido = iniRep > fimRep;

  async function imprimirEstoque() {
    try {
      const d = await api.get<EstoquePlanilhaDTO>("/api/integracao/planilha/estoque");
      if (!renderImpressaoEstoque(d)) toast("Permita pop-ups para imprimir o estoque.", "info");
    } catch (e: unknown) {
      toast((e as Error).message || "Erro ao imprimir o estoque.", "danger");
    }
  }

  async function gerarAlertas() {
    try {
      await api.post("/api/alertas/gerar");
      reloadDash();
      toast("Alertas gerados a partir do estoque atual.");
    } catch (e: unknown) {
      toast((e as Error).message || "Erro ao gerar alertas.", "danger");
    }
  }

  async function marcarVisualizado(a: AlertaView) {
    try {
      await api.put(`/api/alertas/${a.id}/visualizado`);
      setDash((d) =>
        d
          ? {
              ...d,
              alertasPendentes: Math.max(0, d.alertasPendentes - 1),
              principaisAlertas: d.principaisAlertas.filter((x) => x.id !== a.id),
            }
          : d
      );
    } catch (e: unknown) {
      toast((e as Error).message || "Erro ao marcar alerta.", "danger");
    }
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Relatórios</h1>
          <p>Dashboard, CMV, alertas e relatórios operacionais</p>
        </div>
        <div className="page-actions">
          <button
            className="btn"
            onClick={() => {
              if (!cmv) {
                toast("Calcule o CMV primeiro.", "info");
                return;
              }
              if (!imprimirCmv(cmv)) toast("Permita pop-ups para imprimir o relatório.", "info");
            }}
          >
            Imprimir — CMV
          </button>
          <button
            className="btn"
            onClick={() => {
              const p = new URLSearchParams({ inicio: iniCmv, fim: fimCmv });
              if (vendasCmv) p.set("vendas", vendasCmv.replace(",", "."));
              download(`/api/integracao/exportar/cmv?${p}`, "cmv.csv");
            }}
          >
            Exportar CSV — CMV
          </button>
          <button className="btn" onClick={() => void imprimirEstoque()}>
            Imprimir — Estoque
          </button>
          <button className="btn" onClick={() => download("/api/integracao/exportar/estoque", "estoque.csv")}>
            Exportar CSV — Estoque
          </button>
          <button className="btn" disabled={pdfLoading || !cmv} onClick={() => void pdfCmv()}>
            PDF — CMV
          </button>
          <button className="btn" disabled={pdfLoading} onClick={() => void pdfRel("ESTOQUE_ATUAL")}>
            PDF — Estoque
          </button>
        </div>
      </div>

      {/* seção dashboard */}
      <div className="card card-pad" style={{ marginBottom: 22 }}>
        <div className="filtro-line">
          <h3 style={{ margin: 0, marginRight: "auto" }}>Dashboard {mesLabel(mes)}/{ano}</h3>
          <input
            type="number"
            min="2000"
            max="2100"
            value={ano}
            style={{ width: 90 }}
            onChange={(e) => setAno(parseInt(e.target.value || "0", 10))}
          />
          <select value={mes} onChange={(e) => setMes(parseInt(e.target.value, 10))}>
            {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].map((m) => (
              <option key={m} value={m}>{mesLabel(m)}</option>
            ))}
          </select>
        </div>

        {dashLoading ? (
          <CardsSkeleton n={6} />
        ) : dashErro ? (
          <div className="empty">Erro: {dashErro}</div>
        ) : dash ? (
          <>
            <div className="grid cards">
              <Stat
                label="CMV do mês"
                value={<CountUp value={dash.cmvMes} format={formatMoney} />}
                hint={`Meta ≤ ${formatPercent(dash.metaCmv)}`}
                tone={dash.cmvMes > 0 && dash.metaCmv > 0 && dash.consumoMes + dash.desperdicioMes > 0 ? "warn" : undefined}
              />
              <Stat
                label="Consumo (R$)"
                value={<CountUp value={dash.consumoMes} format={formatMoney} />}
              />
              <Stat
                label="Desperdício (R$)"
                value={<CountUp value={dash.desperdicioMes} format={formatMoney} />}
              />
              <Stat
                label="Alertas pendentes"
                value={<CountUp value={dash.alertasPendentes} />}
                tone={dash.alertasPendentes > 0 ? "danger" : "ok"}
              />
              <Stat
                label="Estoque baixo"
                value={<CountUp value={dash.produtosComEstoqueBaixo} />}
                hint="produtos para repor"
                tone={dash.produtosComEstoqueBaixo > 0 ? "danger" : "ok"}
              />
              <Stat
                label="Lotes vencendo"
                value={<CountUp value={dash.lotesVencendo} />}
                hint="em até 7 dias"
                tone={dash.lotesVencendo > 0 ? "warn" : "ok"}
              />
              <Stat
                label="Lotes vencidos"
                value={<CountUp value={dash.lotesVencidos} />}
                tone={dash.lotesVencidos > 0 ? "danger" : "ok"}
              />
              <Stat label="Produtos" value={<CountUp value={dash.totalProdutos} />} />
              <Stat
                label="Vendas (R$)"
                value={<CountUp value={dash.vendasMes} format={formatMoney} />}
                hint={`informado em ${mesLabel(mes)}`}
                tone={dash.vendasMes > 0 ? "ok" : "warn"}
              />
              <Stat
                label="% CMV"
                value={dash.vendasMes > 0 ? <CountUp value={dash.cmvMes / dash.vendasMes} format={formatPercent} /> : "—"}
                hint={`Meta ≤ ${formatPercent(dash.metaCmv)}`}
                tone={dash.vendasMes > 0 && dash.cmvMes / dash.vendasMes > dash.metaCmv ? "danger" : "ok"}
              />
              <Stat
                label="Desperdício vs meta"
                value={dash.desperdicioPct ? <CountUp value={dash.desperdicioPct} format={formatPercent} /> : "—"}
                hint={`meta ≤ ${formatPercent(dash.metaDesperdicio)}`}
                tone={dash.desperdicioPct > dash.metaDesperdicio ? "danger" : "ok"}
              />
              <Stat
                label="Último backup"
                value={dash.ultimoBackup ? formatDateTime(dash.ultimoBackup) : "nunca"}
                hint="automático diario às 03:30"
                tone={dash.backupEmDia ? "ok" : "danger"}
              />
              <Stat
                label="Sessões ativas"
                value={<CountUp value={dash.sessoesAtivas} />}
                hint="acessos logados"
                tone={dash.sessoesAtivas > 0 ? "ok" : "warn"}
              />
            </div>

            {mensagemSistema && (
              <div className={mensagemSistema.tipo === "ok" ? "aviso ok" : "aviso danger"} style={{ marginTop: 14 }}>
                {mensagemSistema.msg}
              </div>
            )}

            {dash.entradasSemValor > 0 && (
              <div className="aviso" style={{ marginTop: 14 }}>
                <strong>{dash.entradasSemValor} entrada(s) sem valor total pago.</strong> O CMV subestima o custo até
                o valor ser preenchido na tela de Entradas (ou via planilha de movimentações).
              </div>
            )}

            {dash.vendasMes <= 0 && (
              <div className="aviso" style={{ marginTop: 14 }}>
                <strong>Informe as vendas do mês</strong> para que o % de CMV seja calculado corretamente no
                dashboard e no CMV do período.
              </div>
            )}

            <div className="card card-pad" style={{ marginTop: 18 }}>
              <div className="filtro-line" style={{ marginBottom: 0, flexWrap: "wrap" }}>
                <h3 style={{ margin: 0, marginRight: "auto" }}>Gestão</h3>
                <label className="muted small">Vendas do mês (R$):</label>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={vendasMesInput}
                  style={{ width: 130 }}
                  onChange={(e) => setVendasMesInput(e.target.value)}
                />
                <button className="btn small" disabled={salvandoSistema} onClick={salvarVendasMes}>
                  {salvandoSistema ? "Salvando…" : "Salvar vendas"}
                </button>
                <label className="muted small" style={{ marginLeft: 8 }}>Meta de desperdício (%):</label>
                <input
                  type="number"
                  min="0"
                  step="0.1"
                  value={metaDespInput}
                  style={{ width: 80 }}
                  onChange={(e) => setMetaDespInput(e.target.value)}
                />
                <button className="btn small" disabled={salvandoSistema} onClick={salvarMetaDesperdicio}>
                  Salvar meta
                </button>
                <label className="muted small" style={{ marginLeft: 8 }}>Meta % CMV:</label>
                <input
                  type="number"
                  min="0"
                  step="0.1"
                  value={metaCmvInput}
                  style={{ width: 80 }}
                  onChange={(e) => setMetaCmvInput(e.target.value)}
                />
                <button className="btn small" disabled={salvandoSistema} onClick={salvarMetaCmv}>
                  Salvar meta
                </button>
                {admin && (
                  <button className="btn small" disabled={salvandoSistema} onClick={fazerBackup}>
                    {salvandoSistema ? "Aguarde…" : "Fazer backup agora"}
                  </button>
                )}
              </div>
              <p className="small muted" style={{ marginBottom: 0, marginTop: 8 }}>
                O % de CMV do dashboard usa as vendas informadas acima. O backup cria um arquivo em{" "}
                <code>backups/</code> junto ao sistema.
              </p>
            </div>
          </>
        ) : null}
      </div>

      {/* seção CMV */}
      <div className="card card-pad" style={{ marginBottom: 22 }}>
        <h3 style={{ marginTop: 0 }}>CMV (Custo de Mercadorias Vendidas)</h3>
        <div className="filtro-line">
          <label className="muted small">Período:</label>
          <input type="date" value={iniCmv} onChange={(e) => setIniCmv(e.target.value)} />
          <input type="date" value={fimCmv} onChange={(e) => setFimCmv(e.target.value)} />
          <label className="muted small" style={{ marginLeft: 8 }}>Vendas (R$):</label>
          <input
            type="number"
            min="0"
            step="0.01"
            value={vendasCmv}
            style={{ width: 130 }}
            placeholder="opcional"
            onChange={(e) => setVendasCmv(e.target.value)}
          />
          <button className="btn primary" disabled={cmvLoading || cmvPeriodoInvalido} onClick={gerarCmv}>
            {cmvLoading ? "Gerando…" : "Calcular"}
          </button>
        </div>
        {cmvErro && <div className="form-error">{cmvErro}</div>}
        {cmvPeriodoInvalido && (
          <div className="aviso" style={{ marginTop: 10 }}>
            Período inválido: a data inicial é posterior à data final. Ajuste o intervalo para calcular o CMV.
          </div>
        )}

        {cmv && (
          <>
            <div className="grid cards" style={{ marginBottom: 14, gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))" }}>
              <Stat label="Consumo" value={formatMoney(cmv.totalConsumo)} />
              <Stat label="Desperdício real" value={formatMoney(cmv.totalDesperdicio)} />
              <Stat label="CMV (EI+Entradas−EF)" value={formatMoney(cmv.totalGeral)} />
              <Stat label="Vendas" value={formatMoney(cmv.vendas)} />
              <Stat label="% CMV" value={formatPercent(cmv.cmv)} hint={`Meta ${formatPercent(cmv.metaCmv)}`} />
            </div>
            {cmv.mensagem && (
              <div className="aviso" style={{ marginBottom: 14 }}>
                <Badge status={cmv.avaliacao} /> <span>{cmv.mensagem}</span>
              </div>
            )}
            <div style={{ display: "flex", gap: 24, alignItems: "center", marginBottom: 14, flexWrap: "wrap" }}>
              <Gauge
                percent={cmv.cmv ? cmv.cmv / cmv.metaCmv : 0}
                label={cmv.cmv != null ? `${(cmv.cmv * 100).toLocaleString("pt-BR", { maximumFractionDigits: 2 })}%` : "—"}
              />
              <div className="small">
                <span className="muted">Período apurado: {formatDate(cmv.dataInicio)} a {formatDate(cmv.dataFim)}</span>
                <br />
                CMV = Estoque Inicial + Entradas − Estoque Final (fórmula da planilha). Desperdício real = o que saiu do estoque sem ser lançado como consumo.
              </div>
            </div>
            <div className="table-wrap">
              <table className="tbl">
                <thead>
                  <tr>
                    <th>Produto</th>
                    <th>Categoria</th>
                    <th className="num">Est. Inicial</th>
                    <th className="num">Entradas (R$)</th>
                    <th className="num">Est. Final</th>
                    <th className="num">Consumo (R$)</th>
                    <th className="num">Desp. real (R$)</th>
                    <th className="num">CMV (R$)</th>
                  </tr>
                </thead>
                <tbody>
                  {cmv.itens.map((i) => (
                    <tr key={i.produtoId}>
                      <td><strong>{i.produtoNome}</strong></td>
                      <td>{i.categoriaNome || "—"}</td>
                      <td className="num">{formatQtd(i.estoqueInicialQtd)} {i.unidadeMedida || ""}</td>
                      <td className="num">{formatMoney(i.entradasValor)}</td>
                      <td className="num">{formatQtd(i.estoqueFinalQtd)} {i.unidadeMedida || ""}</td>
                      <td className="num">{formatMoney(i.consumoValor)}</td>
                      <td className="num">{formatMoney(i.desperdicioValor)}</td>
                      <td className="num">{formatMoney(i.totalValor)}</td>
                    </tr>
                  ))}
                  {cmv.itens.length === 0 && (
                    <tr><td colSpan={8} className="empty">Sem movimentações no período.</td></tr>
                  )}
                </tbody>
                <tfoot>
                  <tr>
                    <td colSpan={2}>Total</td>
                    <td className="num">{formatQtd(cmv.itens.reduce((s, i) => s + i.estoqueInicialQtd, 0))}</td>
                    <td className="num">{formatMoney(cmv.itens.reduce((s, i) => s + i.entradasValor, 0))}</td>
                    <td className="num">{formatQtd(cmv.itens.reduce((s, i) => s + i.estoqueFinalQtd, 0))}</td>
                    <td className="num">{formatMoney(cmv.totalConsumo)}</td>
                    <td className="num">{formatMoney(cmv.totalDesperdicio)}</td>
                    <td className="num">{formatMoney(cmv.totalGeral)}</td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </>
        )}
      </div>

      {/* auditoria recente + alertas pendentes (abaixo do CMV) */}
      {dash && dash.ultimosEventos.length > 0 && (
        <div className="card card-pad" style={{ marginBottom: 22 }}>
          <div className="filtro-line" style={{ marginBottom: 0 }}>
            <h3 style={{ margin: 0 }}>Auditoria recente</h3>
            <button className="btn small" onClick={reloadDash}>Atualizar</button>
          </div>
          <ul className="alert-list">
            {dash.ultimosEventos.map((ev) => (
              <li key={ev.id}>
                <span>
                  <strong>{ev.acao}</strong> · {ev.entidade}
                  {ev.descricao ? ` — ${ev.descricao}` : ""}
                  <span className="muted small"> · {ev.usuarioNome} · {formatDateTime(ev.dataHora)}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {dash && dash.balancoPendente && (
        <div className="aviso" style={{ marginTop: 14 }}>
          <strong>Balanço pendente.</strong> Realize a conferência física do estoque pela tela de Conferência.
        </div>
      )}

      {dash && dash.principaisAlertas.length > 0 && (
        <div className="card card-pad" style={{ marginBottom: 22 }}>
          <div className="filtro-line" style={{ marginBottom: 0 }}>
            <h3 style={{ margin: 0 }}>Alertas pendentes</h3>
            <button className="btn small" onClick={gerarAlertas}>Gerar alertas</button>
          </div>
          <ul className="alert-list">
            {dash.principaisAlertas.map((a) => (
              <li key={a.id}>
                <span>
                  <strong>{a.mensagem}</strong>{" "}
                  <span className="muted small">· {formatDateTime(a.dataGeracao)}</span>{" "}
                  {a.produtoNome && <span className="small">{a.produtoNome}</span>}
                </span>
                <span style={{ display: "flex", gap: 8, alignItems: "center" }}>
                  <Badge status={a.tipo} />
                  <button className="btn small" onClick={() => marcarVisualizado(a)}>Marcar visualizado</button>
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* seção relatórios por tipo */}
      <div className="card card-pad">
        <h3 style={{ marginTop: 0 }}>Relatórios por tipo</h3>
        <div className="filtro-line">
          <select
            value={tipo}
            onChange={(e) => {
              setTipo(e.target.value as TipoRelatorio);
              setRel(null);
            }}
          >
            {tiposRelatorio.map((t) => (
              <option key={t.valor} value={t.valor}>{t.label}</option>
            ))}
          </select>
          <label className="muted small">Período:</label>
          <input type="date" value={iniRep} onChange={(e) => setIniRep(e.target.value)} />
          <input type="date" value={fimRep} onChange={(e) => setFimRep(e.target.value)} />
          <button className="btn primary" disabled={relLoading || relPeriodoInvalido} onClick={gerarRelatorio}>
            {relLoading ? "Gerando…" : "Gerar"}
          </button>
        </div>
        {relErro && <div className="form-error">{relErro}</div>}
        {relPeriodoInvalido && (
          <div className="aviso" style={{ marginTop: 10 }}>
            Período inválido: a data inicial é posterior à data final. Ajuste o intervalo para gerar o relatório.
          </div>
        )}

        {rel && (
          <>
            <p className="small muted">
              {rel.tipo} · {formatDate(rel.dataInicio)} a {formatDate(rel.dataFim)} · gerado em{" "}
              {formatDateTime(rel.dataGeracao)} · {rel.linhasGeradas} linha(s)
            </p>
            <div className="page-actions" style={{ marginBottom: 10, justifyContent: "flex-start" }}>
              <button
                className="btn"
                onClick={() => {
                  if (!imprimirRelatorio(rel)) toast("Permita pop-ups para imprimir o relatório.", "info");
                }}
              >
                Imprimir relatório
              </button>
            </div>
            <div className="table-wrap">
              <table className="tbl">
                <thead>
                  <tr>
                    <th>Item</th>
                    <th>Detalhe</th>
                    <th>Unidade</th>
                    <th className="num">Quantidade</th>
                    <th className="num">Valor</th>
                    <th>Data</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {rel.linhas.map((l, idx) => (
                    <tr key={l.chave + idx}>
                      <td><strong>{l.chave}</strong></td>
                      <td>{l.detalhe || "—"}</td>
                      <td>{l.unidadeMedida || "—"}</td>
                      <td className="num">{formatQtd(l.quantidade)}</td>
                      <td className="num">{formatMoney(l.valor)}</td>
                      <td>{formatDate(l.data)}</td>
                      <td><Badge status={l.status} /></td>
                    </tr>
                  ))}
                  {rel.linhas.length === 0 && (
                    <tr><td colSpan={7} className="empty">Nenhuma linha gerada.</td></tr>
                  )}
                </tbody>
                <tfoot>
                  <tr>
                    <td colSpan={3}>Total</td>
                    <td className="num">{formatQtd(rel.linhas.reduce((s, l) => s + (l.quantidade ?? 0), 0))}</td>
                    <td className="num">{formatMoney(rel.linhas.reduce((s, l) => s + (l.valor ?? 0), 0))}</td>
                    <td colSpan={2}></td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </>
        )}
      </div>
    </div>
  );
}