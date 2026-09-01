import { useMemo, useState } from "react";
import { api, formatDate, formatMoney, formatQtd } from "../api";
import { Modal, useAsyncData } from "../components";
import type { CompraView, Periodo, Produto } from "../types";

export default function Compras() {
  const { data: periodos } = useAsyncData<Periodo[]>(() => api.get("/api/periodos/listar"), []);
  const { data: produtos } = useAsyncData<{ content: Produto[] }>(() => api.get("/api/produtos?size=500"), []);

  const [selPeriodo, setSelPeriodo] = useState("");
  const [abrir, setAbrir] = useState(false);
  const [form, setForm] = useState({ produtoId: "", quantidade: "", precoUnitario: "", dataCompra: "" });
  const [erro, setErro] = useState("");
  const [salvando, setSalvando] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);

  const periodoAtivo = selPeriodo || periodos?.[0]?.id || "";

  const { data: compras, error, loading } = useAsyncData<CompraView[]>(
    () => (periodoAtivo ? api.get(`/api/compras/periodo/${periodoAtivo}`) : Promise.resolve([])),
    [periodoAtivo, refreshKey]
  );

  const total = useMemo(() => (compras || []).reduce((s, c) => s + c.total, 0), [compras]);

  function abrirNova() {
    const hoje = new Date().toISOString().split("T")[0];
    setForm({ produtoId: "", quantidade: "", precoUnitario: "", dataCompra: hoje });
    setAbrir(true);
    setErro("");
  }

  async function salvar() {
    setSalvando(true);
    setErro("");
    try {
      await api.post("/api/compras", {
        periodoId: periodoAtivo,
        produtoId: form.produtoId,
        quantidade: parseFloat(form.quantidade.replace(",", ".")),
        precoUnitario: parseFloat(form.precoUnitario.replace(",", ".")),
        dataCompra: form.dataCompra,
      });
      setAbrir(false);
      setRefreshKey((k) => k + 1);
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao salvar.");
    } finally {
      setSalvando(false);
    }
  }

  async function excluir(id: string) {
    if (!window.confirm("Excluir esta compra?")) return;
    try {
      await api.del(`/api/compras/${id}`);
      setRefreshKey((k) => k + 1);
    } catch (e: unknown) {
      window.alert((e as Error).message || "Erro ao excluir.");
    }
  }

  const periodoFechado = periodos?.find((p) => p.id === periodoAtivo)?.status === "FECHADO";

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Compras</h1>
          <p>Registro de compras por período</p>
        </div>
        <div className="page-actions">
          <select value={periodoAtivo} onChange={(e) => setSelPeriodo(e.target.value)}>
            {(periodos || []).map((p) => (
              <option key={p.id} value={p.id}>{p.nome}</option>
            ))}
          </select>
          <button className="btn primary" onClick={abrirNova} disabled={periodoFechado}>+ Nova compra</button>
        </div>
      </div>

      {loading && <div className="muted">Carregando…</div>}
      {error && <div className="empty">Erro: {error}</div>}
      {!loading && !error && (
        <div className="table-wrap">
          <table className="tbl">
            <thead>
              <tr>
                <th>Produto</th>
                <th className="num">Quantidade</th>
                <th className="num">Preço unit.</th>
                <th className="num">Total</th>
                <th>Data</th>
                {!periodoFechado && <th style={{ width: 90 }}>Ações</th>}
              </tr>
            </thead>
            <tbody>
              {(compras || []).map((c) => (
                <tr key={c.id}>
                  <td><strong>{c.produtoNome}</strong> <span className="muted small">({c.unidade})</span></td>
                  <td className="num">{formatQtd(c.quantidade)}</td>
                  <td className="num">{formatMoney(c.precoUnitario)}</td>
                  <td className="num">{formatMoney(c.total)}</td>
                  <td>{formatDate(c.dataCompra)}</td>
                  {!periodoFechado && (
                    <td>
                      <button className="btn small danger" onClick={() => excluir(c.id)}>Excluir</button>
                    </td>
                  )}
                </tr>
              ))}
              {(compras || []).length === 0 && (
                <tr><td colSpan={6} className="empty">Nenhuma compra neste período.</td></tr>
              )}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={3}>Total do período</td>
                <td className="num">{formatMoney(total)}</td>
                <td colSpan={2}></td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}

      {abrir && (
        <Modal title="Nova compra" onClose={() => setAbrir(false)}>
          <div className="field">
            <label>Produto *</label>
            <select value={form.produtoId} onChange={(e) => setForm({ ...form, produtoId: e.target.value })}>
              <option value="">Selecione…</option>
              {(produtos?.content || []).slice().sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR")).map((p) => (
                <option key={p.id} value={p.id}>{p.nome} ({p.unidade})</option>
              ))}
            </select>
          </div>
          <div className="form-row">
            <div className="field">
              <label>Quantidade *</label>
              <input type="number" min="0" step="0.001" value={form.quantidade} onChange={(e) => setForm({ ...form, quantidade: e.target.value })} />
            </div>
            <div className="field">
              <label>Preço unitário (R$) *</label>
              <input type="number" min="0" step="0.01" value={form.precoUnitario} onChange={(e) => setForm({ ...form, precoUnitario: e.target.value })} />
            </div>
          </div>
          <div className="field">
            <label>Data da compra</label>
            <input type="date" value={form.dataCompra} onChange={(e) => setForm({ ...form, dataCompra: e.target.value })} />
          </div>
          {erro && <div className="form-error">{erro}</div>}
          <div className="form-actions">
            <button className="btn" onClick={() => setAbrir(false)}>Cancelar</button>
            <button className="btn primary" disabled={salvando || !form.produtoId || !form.quantidade} onClick={salvar}>
              {salvando ? "Salvando…" : "Salvar"}
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}