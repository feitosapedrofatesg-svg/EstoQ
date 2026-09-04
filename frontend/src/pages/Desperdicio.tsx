import { useState } from "react";
import { api, carregarProdutos, formatDateTime, formatMoney, formatQtd, hojeIso, inicioMesIso } from "../api";
import { useAsyncData } from "../components";
import type { LoteView, MovimentacaoView, Produto } from "../types";

const motivoLabel: Record<string, string> = {
  VENCIMENTO: "Vencimento",
  DETERIORACAO: "Deterioração",
  PREPARO_INCORRETO: "Preparo incorreto",
  SOBRA_NAO_APROVEITADA: "Sobra não aproveitada",
  OUTRO: "Outro",
};

export default function Desperdicio() {
  const [produtoId, setProdutoId] = useState("");
  const [quantidade, setQuantidade] = useState("1");
  const [motivo, setMotivo] = useState("VENCIMENTO");
  const [descricaoMotivo, setDescricaoMotivo] = useState("");
  const [loteId, setLoteId] = useState("");
  const [observacao, setObservacao] = useState("");
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState("");
  const [refresh, setRefresh] = useState(0);

  const [inicio, setInicio] = useState(inicioMesIso());
  const [fim, setFim] = useState(hojeIso());

  const { data: produtos } = useAsyncData<Produto[]>(() => carregarProdutos(), []);
  const { data: lotes } = useAsyncData<LoteView[]>(
    () => (produtoId ? api.get(`/api/lotes/disponiveis?produtoId=${produtoId}`) : Promise.resolve([])),
    [produtoId]
  );
  const { data: movs, loading } = useAsyncData<MovimentacaoView[]>(
    () => api.get(`/api/movimentacoes?inicio=${inicio}&fim=${fim}`),
    [inicio, fim, refresh]
  );

  const desperdicios = (movs || []).filter((m) => m.tipo === "DESPERDICIO");

  async function registrar() {
    setSalvando(true);
    setErro("");
    try {
      await api.post("/api/movimentacoes/desperdicio", {
        produtoId,
        quantidade: parseFloat(quantidade.replace(",", ".")),
        motivo,
        descricaoMotivo: motivo === "OUTRO" ? descricaoMotivo : null,
        loteId: loteId || null,
        observacao: observacao || null,
      });
      setProdutoId("");
      setQuantidade("1");
      setMotivo("VENCIMENTO");
      setDescricaoMotivo("");
      setLoteId("");
      setObservacao("");
      setRefresh((k) => k + 1);
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao registrar desperdício.");
    } finally {
      setSalvando(false);
    }
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Desperdício</h1>
          <p>Registre perdas de estoque e acompanhe o valor do prejuízo</p>
        </div>
      </div>

      <div className="card card-pad" style={{ marginBottom: 20 }}>
        <h3 style={{ marginTop: 0 }}>Registrar desperdício</h3>
        <div className="form-row three">
          <div className="field">
            <label>Produto *</label>
            <select value={produtoId} onChange={(e) => { setProdutoId(e.target.value); setLoteId(""); }}>
              <option value="">Selecione…</option>
              {(produtos || []).map((p) => (
                <option key={p.id} value={p.id}>{p.nome}</option>
              ))}
            </select>
          </div>
          <div className="field">
            <label>Quantidade *</label>
            <input type="number" min="0" step="1" value={quantidade} onChange={(e) => setQuantidade(e.target.value)} />
          </div>
          <div className="field">
            <label>Motivo *</label>
            <select value={motivo} onChange={(e) => setMotivo(e.target.value)}>
              {Object.entries(motivoLabel).map(([v, l]) => (
                <option key={v} value={v}>{l}</option>
              ))}
            </select>
          </div>
          <div className="field">
            <label>Lote (opcional)</label>
            <select value={loteId} disabled={!produtoId || (lotes || []).length === 0} onChange={(e) => setLoteId(e.target.value)}>
              <option value="">Automático (FIFO)</option>
              {(lotes || []).map((l) => (
                <option key={l.id} value={l.id}>
                  {l.codigo} · {formatQtd(l.quantidadeAtual)} {l.unidadeMedida}
                </option>
              ))}
            </select>
          </div>
          {motivo === "OUTRO" && (
            <div className="field">
              <label>Descrição do motivo</label>
              <input value={descricaoMotivo} onChange={(e) => setDescricaoMotivo(e.target.value)} />
            </div>
          )}
          <div className="field">
            <label>Observação</label>
            <input value={observacao} onChange={(e) => setObservacao(e.target.value)} />
          </div>
        </div>
        {erro && <div className="form-error">{erro}</div>}
        <div className="form-actions" style={{ marginTop: 0 }}>
          <button
            className="btn primary"
            disabled={salvando || !produtoId || !quantidade || parseFloat(quantidade) <= 0 || !motivo}
            onClick={registrar}
          >
            {salvando ? "Salvando…" : "Registrar desperdício"}
          </button>
        </div>
      </div>

      <div className="card card-pad">
        <div className="filtro-line">
          <label className="muted small">Período:</label>
          <input type="date" value={inicio} onChange={(e) => setInicio(e.target.value)} />
          <input type="date" value={fim} onChange={(e) => setFim(e.target.value)} />
        </div>

        {loading ? (
          <div className="muted">Carregando…</div>
        ) : desperdicios.length === 0 ? (
          <div className="empty">Nenhum desperdício registrado no período.</div>
        ) : (
          <div className="table-wrap">
            <table className="tbl">
              <thead>
                <tr>
                  <th>Data/hora</th>
                  <th>Produto</th>
                  <th className="num">Quantidade</th>
                  <th>Motivo</th>
                  <th>Lote</th>
                  <th className="num">Prejuízo</th>
                  <th>Observação</th>
                </tr>
              </thead>
              <tbody>
                {desperdicios.slice().reverse().map((m) => (
                  <tr key={m.id}>
                    <td>{formatDateTime(m.dataHora)}</td>
                    <td><strong>{m.produtoNome}</strong></td>
                    <td className="num">{formatQtd(m.quantidade)} {m.unidadeMedida}</td>
                    <td>{m.motivo ? motivoLabel[m.motivo] || m.motivo : "—"}</td>
                    <td>{m.loteCodigo || "—"}</td>
                    <td className="num">{formatMoney(m.valorPrejuizo)}</td>
                    <td className="small muted">{m.observacao || ""}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}