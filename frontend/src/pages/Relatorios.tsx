import { useState } from "react";
import { api, formatDate, formatMoney, formatPercent, formatQtd } from "../api";
import { Badge, Gauge, Stat, useAsyncData } from "../components";
import type { ConsumoMatriz, Dashboard, RelatorioCMVMensal, RelatorioCMVPeriodo } from "../types";

export default function Relatorios() {
  const { data: dashboard } = useAsyncData<Dashboard>(() => api.get("/api/relatorios/dashboard"), []);
  const { data: periodos } = useAsyncData<any[]>(() => api.get("/api/periodos/listar"), []);
  const { data: dadosPorPeriodo } = useAsyncData<Record<string, RelatorioCMVPeriodo>>(
    () =>
      api
        .get<RelatorioCMVMensal>("/api/relatorios/cmv/mensal?mes=12&ano=2023")
        .then((m: RelatorioCMVMensal) => {
          const map: Record<string, RelatorioCMVPeriodo> = {};
          m.periodos.forEach((p) => (map[p.periodoId] = p));
          return map;
        }),
    []
  );
  const { data: matriz } = useAsyncData<ConsumoMatriz>(() => api.get("/api/relatorios/consumo/matriz"), []);
  const { data: alertas } = useAsyncData<any[]>(() => api.get("/api/relatorios/alertas-estoque"), []);

  const [aba, setAba] = useState<"visao" | "cmv" | "consumo" | "alertas">("visao");
  const [selPeriodo, setSelPeriodo] = useState("");

  const periodoAtivo = selPeriodo && dadosPorPeriodo?.[selPeriodo] ? selPeriodo : "";
  const relAtivo: RelatorioCMVPeriodo | undefined = (periodoAtivo && dadosPorPeriodo?.[periodoAtivo]) || undefined;

  if (!dashboard || !periodos || !dadosPorPeriodo || !matriz || !alertas) return <div className="muted">Carregando…</div>;

  const mensal = dadosPorPeriodo ? Object.values(dadosPorPeriodo) : [];
  const cmvMensal = mensal.length
    ? mensal.reduce((s, p) => s + p.totalConsumo, 0) / Math.max(1, mensal.reduce((s, p) => s + p.vendas, 0))
    : 0;

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Relatórios</h1>
          <p>CMV semanal, mensal, matriz de consumo e alertas de reposição</p>
        </div>
      </div>

      <div className="page-actions" style={{ marginBottom: 16 }}>
        {(["visao", "cmv", "consumo", "alertas"] as const).map((t) => (
          <button key={t} className={`btn${aba === t ? " primary" : ""}`} onClick={() => setAba(t)}>
            {t === "visao" ? "Visão geral" : t === "cmv" ? "CMV" : t === "consumo" ? "Matriz de consumo" : "Alertas de estoque"}
          </button>
        ))}
      </div>

      {aba === "visao" && (
        <>
          <div className="grid cards">
            <div
              className="card card-pad"
              style={{ display: "flex", alignItems: "center", gap: 20, flexWrap: "wrap", gridColumn: "span 2" }}
            >
              <Gauge
                percent={dashboard.cmvDoMes / dashboard.cmvMeta}
                label={formatPercent(dashboard.cmvDoMes)}
              />
              <div style={{ flex: "1 1 180px", minWidth: 0 }}>
                <div className="label muted small" style={{ textTransform: "uppercase", fontWeight: 700 }}>CMV do mês</div>
                <div className="small" style={{ marginTop: 4 }}>Meta ≤ {formatPercent(dashboard.cmvMeta)}</div>
                <div className="small" style={{ marginTop: 6 }}>
                  <Badge status={dashboard.cmvDoMes !== null && dashboard.cmvDoMes <= dashboard.cmvMeta ? "OK" : "ATENCAO"} />
                </div>
              </div>
            </div>
            <Stat label="Consumo (R$)" value={formatMoney(dashboard.consumoDoMes)} />
            <Stat label="Vendas (R$)" value={formatMoney(dashboard.vendasDoMes)} />
            <Stat
              label="Estoque em risco"
              value={dashboard.produtosComEstoqueBaixo}
              hint="produtos para repor"
              tone={dashboard.produtosComEstoqueBaixo > 0 ? "danger" : "ok"}
            />
            <Stat label="Produtos" value={dashboard.totalProdutos} />
            <Stat label="Períodos abertos" value={dashboard.periodosAbertos} />
            <Stat label="Compras no mês" value={dashboard.comprasNoMes} />
          </div>

          <div className="card card-pad" style={{ marginTop: 18 }}>
            <h3 style={{ marginTop: 0 }}>Alertas de reposição</h3>
            {dashboard.principaisAlertas.length === 0 ? (
              <div className="empty">Nenhum alerta. Estoque saudável!</div>
            ) : (
              <ul className="alert-list">
                {dashboard.principaisAlertas.map((a) => (
                  <li key={a.produtoId}>
                    <span>
                      <strong>{a.produtoNome}</strong>{" "}
                      <span className="muted small">
                        · mínimo {a.estoqueMinimo} {a.unidade} · consumo médio {a.consumoMedioSemanal}
                      </span>
                    </span>
                    <span style={{ display: "flex", gap: 8, alignItems: "center" }}>
                      <span className="small">{a.estoqueAtual} {a.unidade}</span>
                      <Badge status={a.status} />
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </>
      )}

      {aba === "cmv" && (
        <div className="grid two">
          <div className="card card-pad">
            <h3 style={{ marginTop: 0 }}>CMV Dezembro/2023</h3>
            <Gauge percent={cmvMensal / 0.7} label={formatPercent(cmvMensal)} />
            <p className="small muted">Meta de referência: 70%</p>
            <table className="tbl">
              <tbody>
                <tr><td>Consumo total</td><td className="num"><strong>{formatMoney(mensal.reduce((s, p) => s + p.totalConsumo, 0))}</strong></td></tr>
                <tr><td>Vendas totais</td><td className="num"><strong>{formatMoney(mensal.reduce((s, p) => s + p.vendas, 0))}</strong></td></tr>
                <tr><td>Períodos apurados</td><td className="num">{mensal.length}</td></tr>
              </tbody>
            </table>
            <div style={{ marginTop: 10 }}>
              {mensal.map((p) => (
                <button key={p.periodoId} className={`btn small${relAtivo?.periodoId === p.periodoId ? " primary" : ""}`} style={{ margin: 2 }} onClick={() => setSelPeriodo(p.periodoId)}>
                  {p.periodoNome}
                </button>
              ))}
            </div>
          </div>

          <div className="card card-pad">
            {relAtivo ? (
              <>
                <h3 style={{ marginTop: 0 }}>{relAtivo.periodoNome}</h3>
                <p className="small muted">
                  {formatDate(relAtivo.dataInicio)} a {formatDate(relAtivo.dataFim)} · vendas {formatMoney(relAtivo.vendas)}
                </p>
                <table className="tbl">
                  <thead>
                    <tr>
                      <th>Produto</th>
                      <th className="num">Consumo qtd.</th>
                      <th className="num">Consumo (R$)</th>
                    </tr>
                  </thead>
                  <tbody>
                    {relAtivo.itens.slice(0, 12).map((i) => (
                      <tr key={i.produtoId}>
                        <td>{i.produtoNome}</td>
                        <td className="num">{formatQtd(i.consumoQtd)}</td>
                        <td className="num">{formatMoney(i.consumoValor)}</td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr>
                      <td>Total (CMV {formatPercent(relAtivo.cmv)})</td>
                      <td className="num">{formatQtd(relAtivo.itens.reduce((s, i) => s + i.consumoQtd, 0))}</td>
                      <td className="num">{formatMoney(relAtivo.totalConsumo)}</td>
                    </tr>
                  </tfoot>
                </table>
                <p className="small muted">
                  Estoque inicial {formatMoney(relAtivo.totalEstoqueInicial)} + compras {formatMoney(relAtivo.totalCompras)} − estoque final {formatMoney(relAtivo.totalEstoqueFinal)} = consumo.
                </p>
              </>
            ) : (
              <div className="empty">Selecione um período para ver o detalhamento (clique nos botões acima).</div>
            )}
          </div>
        </div>
      )}

      {aba === "consumo" && (
        <div className="table-wrap">
          <table className="tbl">
            <thead>
              <tr>
                <th>Produto</th>
                <th>Categoria</th>
                {matriz.periodos.map((p) => (
                  <th key={p.periodoId} className="num">{p.periodoNome}</th>
                ))}
                <th className="num">Total</th>
                <th className="num">Mínimo</th>
              </tr>
            </thead>
            <tbody>
              {matriz.itens.map((i) => (
                <tr key={i.produtoId}>
                  <td><strong>{i.produtoNome}</strong></td>
                  <td>{i.categoria}</td>
                  {i.consumoPorPeriodo.map((c, idx) => (
                    <td key={idx} className="num">{c === null ? "—" : formatQtd(c)}</td>
                  ))}
                  <td className="num"><strong>{formatQtd(i.consumoTotal)}</strong></td>
                  <td className="num">{formatQtd(i.estoqueMinimo)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={2}>Total geral</td>
                {matriz.periodos.map((p, idx) => (
                  <td key={p.periodoId} className="num">
                    {formatQtd(matriz.itens.reduce((s, i) => s + ((i.consumoPorPeriodo[idx] || 0)), 0))}
                  </td>
                ))}
                <td className="num">{formatQtd(matriz.consumoTotal)}</td>
                <td></td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}

      {aba === "alertas" && (
        <div className="table-wrap">
          <table className="tbl">
            <thead>
              <tr>
                <th>Produto</th>
                <th>Categoria</th>
                <th className="num">Estoque atual</th>
                <th className="num">Mínimo</th>
                <th className="num">Consumo médio</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {alertas.map((a) => (
                <tr key={a.produtoId}>
                  <td><strong>{a.produtoNome}</strong></td>
                  <td>{a.categoria}</td>
                  <td className="num">{formatQtd(a.estoqueAtual)}</td>
                  <td className="num">{formatQtd(a.estoqueMinimo)}</td>
                  <td className="num">{formatQtd(a.consumoMedioSemanal)}</td>
                  <td><Badge status={a.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}