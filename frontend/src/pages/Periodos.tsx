import { useState } from "react";
import { api, formatDate, formatMoney } from "../api";
import { Badge, Modal, useAsyncData } from "../components";
import type { Periodo } from "../types";

export default function Periodos() {
  const { data, error, loading, reload } = useAsyncData<Periodo[]>(
    () => api.get("/api/periodos/listar"),
    []
  );
  const [editando, setEditando] = useState<Periodo | null>(null);
  const [novo, setNovo] = useState(false);
  const [form, setForm] = useState({
    nome: "",
    dataInicio: "",
    dataFim: "",
    vendas: "",
  });
  const [erro, setErro] = useState("");
  const [salvando, setSalvando] = useState(false);

  if (loading) return <div className="muted">Carregando…</div>;
  if (error) return <div className="empty">Erro: {error}</div>;

  function abrirNovo() {
    setForm({ nome: "", dataInicio: "", dataFim: "", vendas: "" });
    setNovo(true);
    setErro("");
  }

  function abrirEdicao(p: Periodo) {
    setForm({
      nome: p.nome,
      dataInicio: p.dataInicio,
      dataFim: p.dataFim,
      vendas: p.vendas === null ? "" : String(p.vendas),
    });
    setEditando(p);
    setErro("");
  }

  async function salvar() {
    setSalvando(true);
    setErro("");
    try {
      const body = {
        nome: form.nome,
        dataInicio: form.dataInicio,
        dataFim: form.dataFim,
        vendas: form.vendas === "" ? null : parseFloat(form.vendas.replace(",", ".")),
        status: "ABERTO",
      };
      if (editando) await api.put(`/api/periodos/${editando.id}`, body);
      else await api.post("/api/periodos", body);
      setEditando(null);
      setNovo(false);
      reload();
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao salvar.");
    } finally {
      setSalvando(false);
    }
  }

  async function fechar(p: Periodo) {
    if (!window.confirm(`Fechar o período "${p.nome}"? Isso bloqueia edições de compras e estoque.`)) return;
    try {
      await api.post(`/api/periodos/${p.id}/fechar`);
      reload();
    } catch (e: unknown) {
      window.alert((e as Error).message || "Erro ao fechar período.");
    }
  }

  async function preparar(p: Periodo) {
    if (!window.confirm(`Preparar o estoque do período "${p.nome}"? Os estoques iniciais serão preenchidos com o estoque final do período anterior.`)) return;
    try {
      const r = await api.post<{ criados: number }>(`/api/estoque/preparar/${p.id}`);
      window.alert(`${r.criados} lançamentos de estoque preparados.`);
      reload();
    } catch (e: unknown) {
      window.alert((e as Error).message || "Erro ao preparar estoque.");
    }
  }

  const totalVendas = (data || []).reduce((s, p) => s + (p.vendas || 0), 0);

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Períodos</h1>
          <p>Períodos de apuração (semanas) · vendas totais {formatMoney(totalVendas)}</p>
        </div>
        <button className="btn primary" onClick={abrirNovo}>+ Novo período</button>
      </div>

      <div className="table-wrap">
        <table className="tbl">
          <thead>
            <tr>
              <th>Período</th>
              <th>Início</th>
              <th>Fim</th>
              <th className="num">Vendas (R$)</th>
              <th>Status</th>
              <th style={{ width: 340 }}>Ações</th>
            </tr>
          </thead>
          <tbody>
            {(data || []).map((p) => (
              <tr key={p.id}>
                <td><strong>{p.nome}</strong></td>
                <td>{formatDate(p.dataInicio)}</td>
                <td>{formatDate(p.dataFim)}</td>
                <td className="num">{formatMoney(p.vendas)}</td>
                <td><Badge status={p.status} /></td>
                <td>
                  <button
                    className="btn small"
                    onClick={() => window.location.hash = `#/relatorios?periodo=${p.id}`}
                    title="Ver relatório CMV do período"
                  >
                    CMV
                  </button>{" "}
                  <button className="btn small" onClick={() => abrirEdicao(p)}>Editar</button>{" "}
                  {p.status === "ABERTO" && (
                    <>
                      <button className="btn small" onClick={() => preparar(p)}>Preparar estoque</button>{" "}
                      <button className="btn small" onClick={() => fechar(p)}>Fechar</button>
                    </>
                  )}
                </td>
              </tr>
            ))}
            {(data || []).length === 0 && (
              <tr><td colSpan={6} className="empty">Nenhum período criado ainda.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {(novo || editando) && (
        <Modal title={editando ? "Editar período" : "Novo período"} onClose={() => { setNovo(false); setEditando(null); }}>
          <div className="field">
            <label>Nome *</label>
            <input value={form.nome} placeholder="Ex.: Semana 04/12 a 11/12" onChange={(e) => setForm({ ...form, nome: e.target.value })} />
          </div>
          <div className="form-row">
            <div className="field">
              <label>Data início *</label>
              <input type="date" value={form.dataInicio} onChange={(e) => setForm({ ...form, dataInicio: e.target.value })} />
            </div>
            <div className="field">
              <label>Data fim *</label>
              <input type="date" value={form.dataFim} onChange={(e) => setForm({ ...form, dataFim: e.target.value })} />
            </div>
          </div>
          <div className="field">
            <label>Vendas do período (R$)</label>
            <input type="number" min="0" step="0.01" value={form.vendas} placeholder="Ex.: 27211.82" onChange={(e) => setForm({ ...form, vendas: e.target.value })} />
          </div>
          {erro && <div className="form-error">{erro}</div>}
          <div className="form-actions">
            <button className="btn" onClick={() => { setNovo(false); setEditando(null); }}>Cancelar</button>
            <button className="btn primary" disabled={salvando || !form.nome || !form.dataInicio || !form.dataFim} onClick={salvar}>
              {salvando ? "Salvando…" : "Salvar"}
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}