import { useEffect, useState } from "react";
import { api, download, formatDate, formatDateTime, formatMoney, formatPercent, formatQtd, hojeIso, inicioMesIso } from "../api";
import { Badge, Gauge, Stat, useAsyncData } from "../components";
import type { AlertaView, CMVReportDTO, DashboardDTO, RelatorioViewDTO, TipoRelatorio } from "../types";

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

export default function Relatorios() {
  const agora = new Date();
  const [ano, setAno] = useState(agora.getFullYear());
  const [mes, setMes] = useState(agora.getMonth() + 1);
  const { data: dash, error: dashErro, loading: dashLoading, reload: reloadDash } = useAsyncData<DashboardDTO>(
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

  useEffect(() => {
    gerarCmv();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function gerarAlertas() {
    try {
      await api.post("/api/alertas/gerar");
      reloadDash();
    } catch (e: unknown) {
      window.alert((e as Error).message || "Erro ao gerar alertas.");
    }
  }

  async function marcarVisualizado(a: AlertaView) {
    try {
      await api.put(`/api/alertas/${a.id}/visualizado`);
      reloadDash();
    } catch (e: unknown) {
      window.alert((e as Error).message || "Erro ao marcar alerta.");
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
          <button className="btn" onClick={() => download("/api/integracao/exportar/cmv", "cmv.csv")}>
            Exportar CSV — CMV
          </button>
          <button className="btn" onClick={() => download("/api/integracao/exportar/estoque", "estoque.csv")}>
            Exportar CSV — Estoque
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
          <div className="muted">Carregando…</div>
        ) : dashErro ? (
          <div className="empty">Erro: {dashErro}</div>
        ) : dash ? (
          <>
            <div className="grid cards">
              <Stat
                label="CMV do mês"
                value={formatMoney(dash.cmvMes)}
                hint={`Meta ≤ ${formatPercent(dash.metaCmv)}`}
                tone={dash.cmvMes > 0 && dash.metaCmv > 0 && dash.consumoMes + dash.desperdicioMes > 0 ? "warn" : undefined}
              />
              <Stat label="Consumo (R$)" value={formatMoney(dash.consumoMes)} />
              <Stat label="Desperdício (R$)" value={formatMoney(dash.desperdicioMes)} />
              <Stat
                label="Alertas pendentes"
                value={dash.alertasPendentes}
                tone={dash.alertasPendentes > 0 ? "danger" : "ok"}
              />
              <Stat
                label="Estoque baixo"
                value={dash.produtosComEstoqueBaixo}
                hint="produtos para repor"
                tone={dash.produtosComEstoqueBaixo > 0 ? "danger" : "ok"}
              />
              <Stat
                label="Lotes vencendo"
                value={dash.lotesVencendo}
                hint="em até 7 dias"
                tone={dash.lotesVencendo > 0 ? "warn" : "ok"}
              />
              <Stat
                label="Lotes vencidos"
                value={dash.lotesVencidos}
                tone={dash.lotesVencidos > 0 ? "danger" : "ok"}
              />
              <Stat label="Produtos" value={dash.totalProdutos} />
            </div>

            {dash.balancoPendente && (
              <div className="aviso" style={{ marginTop: 14 }}>
                <strong>Balanço pendente.</strong> Realize a conferência física do estoque pela tela de Conferência.
              </div>
            )}

            <div className="card card-pad" style={{ marginTop: 18 }}>
              <div className="filtro-line" style={{ marginBottom: 0 }}>
                <h3 style={{ margin: 0 }}>Alertas pendentes</h3>
                <button className="btn small" onClick={gerarAlertas}>Gerar alertas</button>
              </div>
              {dash.principaisAlertas.length === 0 ? (
                <div className="empty">Nenhum alerta pendente.</div>
              ) : (
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
              )}
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
          <button className="btn primary" disabled={cmvLoading} onClick={gerarCmv}>
            {cmvLoading ? "Gerando…" : "Calcular"}
          </button>
        </div>
        {cmvErro && <div className="form-error">{cmvErro}</div>}

        {cmv && (
          <>
            <div className="grid cards" style={{ marginBottom: 14, gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))" }}>
              <Stat label="Consumo" value={formatMoney(cmv.totalConsumo)} />
              <Stat label="Desperdício real" value={formatMoney(cmv.totalDesperdicio)} />
              <Stat label="CMV (EI+Entradas−EF)" value={formatMoney(cmv.totalGeral)} />
              <Stat label="Vendas" value={formatMoney(cmv.vendas)} />
              <Stat label="% CMV" value={formatPercent(cmv.cmv)} hint={`Meta ${formatPercent(cmv.metaCmv)}`} />
            </div>
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

      {/* seção relatórios por tipo */}
      <div className="card card-pad">
        <h3 style={{ marginTop: 0 }}>Relatórios por tipo</h3>
        <div className="filtro-line">
          <select value={tipo} onChange={(e) => setTipo(e.target.value as TipoRelatorio)}>
            {tiposRelatorio.map((t) => (
              <option key={t.valor} value={t.valor}>{t.label}</option>
            ))}
          </select>
          <label className="muted small">Período:</label>
          <input type="date" value={iniRep} onChange={(e) => setIniRep(e.target.value)} />
          <input type="date" value={fimRep} onChange={(e) => setFimRep(e.target.value)} />
          <button className="btn primary" disabled={relLoading} onClick={gerarRelatorio}>
            {relLoading ? "Gerando…" : "Gerar"}
          </button>
        </div>
        {relErro && <div className="form-error">{relErro}</div>}

        {rel && (
          <>
            <p className="small muted">
              {rel.tipo} · {formatDate(rel.dataInicio)} a {formatDate(rel.dataFim)} · gerado em{" "}
              {formatDateTime(rel.dataGeracao)} · {rel.linhasGeradas} linha(s)
            </p>
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
              </table>
            </div>
          </>
        )}
      </div>
    </div>
  );
}