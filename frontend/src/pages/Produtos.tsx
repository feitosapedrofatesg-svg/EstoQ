import { useMemo, useState } from "react";
import { api } from "../api";
import { Modal, useAsyncData } from "../components";
import type { Produto } from "../types";

const vazio = { nome: "", unidade: "und", categoria: "", estoqueMinimo: "7" };

export default function Produtos() {
  const { data, error, loading, reload } = useAsyncData<{ content: Produto[]; totalElements: number }>(
    () => api.get("/api/produtos?size=500"),
    []
  );
  const [busca, setBusca] = useState("");
  const [filtroCat, setFiltroCat] = useState("");
  const [editando, setEditando] = useState<Produto | null>(null);
  const [form, setForm] = useState<Record<string, string>>({ ...vazio });
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState("");

  const categorias = useMemo(
    () => Array.from(new Set((data?.content || []).map((p) => p.categoria).filter(Boolean))).sort(),
    [data]
  );

  const filtrados = useMemo(() => {
    let lista = data?.content || [];
    if (busca) lista = lista.filter((p) => p.nome.toLowerCase().includes(busca.toLowerCase()));
    if (filtroCat) lista = lista.filter((p) => p.categoria === filtroCat);
    return lista.sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR"));
  }, [data, busca, filtroCat]);

  if (loading) return <div className="muted">Carregando…</div>;
  if (error) return <div className="empty">Erro: {error}</div>;

  function abrirNovo() {
    setForm({ ...vazio });
    setEditando({} as Produto);
    setErro("");
  }

  function abrirEdicao(p: Produto) {
    setForm({
      nome: p.nome,
      unidade: p.unidade,
      categoria: p.categoria || "",
      estoqueMinimo: String(p.estoqueMinimo),
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
        unidade: form.unidade,
        categoria: form.categoria,
        estoqueMinimo: parseFloat(form.estoqueMinimo.replace(",", ".")),
      };
      if (editando?.id) await api.put(`/api/produtos/${editando.id}`, body);
      else await api.post("/api/produtos", body);
      setEditando(null);
      reload();
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao salvar.");
    } finally {
      setSalvando(false);
    }
  }

  async function excluir(p: Produto) {
    if (!window.confirm(`Excluir "${p.nome}"?`)) return;
    try {
      await api.del(`/api/produtos/${p.id}`);
      reload();
    } catch (e: unknown) {
      window.alert((e as Error).message || "Erro ao excluir.");
    }
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Produtos</h1>
          <p>{data?.totalElements || 0} itens do catálogo</p>
        </div>
        <div className="page-actions">
          <input
            className="search"
            placeholder="Buscar produto…"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
          />
          <select value={filtroCat} onChange={(e) => setFiltroCat(e.target.value)}>
            <option value="">Todas categorias</option>
            {categorias.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </select>
          <button className="btn primary" onClick={abrirNovo}>+ Novo produto</button>
        </div>
      </div>

      <div className="table-wrap">
        <table className="tbl">
          <thead>
            <tr>
              <th>Produto</th>
              <th>Unidade</th>
              <th>Categoria</th>
              <th className="num">Estoque mínimo</th>
              <th style={{ width: 120 }}>Ações</th>
            </tr>
          </thead>
          <tbody>
            {filtrados.map((p) => (
              <tr key={p.id}>
                <td><strong>{p.nome}</strong></td>
                <td>{p.unidade}</td>
                <td>{p.categoria || "—"}</td>
                <td className="num">{p.estoqueMinimo}</td>
                <td>
                  <button className="btn small" onClick={() => abrirEdicao(p)}>Editar</button>{" "}
                  <button className="btn small danger" onClick={() => excluir(p)}>Excluir</button>
                </td>
              </tr>
            ))}
            {filtrados.length === 0 && (
              <tr><td colSpan={5} className="empty">Nenhum produto encontrado.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {editando && (
        <Modal title={editando.id ? "Editar produto" : "Novo produto"} onClose={() => setEditando(null)}>
          <div className="form-row three">
            <div className="field" style={{ gridColumn: "span 2" }}>
              <label>Nome *</label>
              <input value={form.nome} onChange={(e) => setForm({ ...form, nome: e.target.value })} />
            </div>
            <div className="field">
              <label>Unidade *</label>
              <input value={form.unidade} onChange={(e) => setForm({ ...form, unidade: e.target.value })} />
            </div>
            <div className="field">
              <label>Categoria</label>
              <input value={form.categoria} list="categorias-list" onChange={(e) => setForm({ ...form, categoria: e.target.value })} />
              <datalist id="categorias-list">
                {categorias.map((c) => <option key={c} value={c} />)}
              </datalist>
            </div>
            <div className="field">
              <label>Estoque mínimo</label>
              <input type="number" min="0" step="0.001" value={form.estoqueMinimo} onChange={(e) => setForm({ ...form, estoqueMinimo: e.target.value })} />
            </div>
          </div>
          {erro && <div className="form-error">{erro}</div>}
          <div className="form-actions">
            <button className="btn" onClick={() => setEditando(null)}>Cancelar</button>
            <button className="btn primary" disabled={salvando || !form.nome} onClick={salvar}>
              {salvando ? "Salvando…" : "Salvar"}
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}