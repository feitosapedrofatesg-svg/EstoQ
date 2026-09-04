import { useMemo, useState } from "react";
import { api, formatQtd } from "../api";
import { useAuth } from "../auth";
import { Modal, useAsyncData } from "../components";
import type { Categoria, Page, Produto } from "../types";

interface FormProduto {
  nome: string;
  categoriaId: string;
  unidadeMedida: string;
  estoqueMinimo: string;
  saldoNovo: string;
}

const formVazio: FormProduto = { nome: "", categoriaId: "", unidadeMedida: "UN", estoqueMinimo: "7", saldoNovo: "" };

export default function Produtos() {
  const { auth } = useAuth();
  const admin = auth?.perfil === "ADMIN";

  const { data, error, loading, reload } = useAsyncData<Page<Produto>>(
    () => api.get("/api/produtos?page=0&size=1000"),
    []
  );
  const { data: categorias } = useAsyncData<Page<Categoria>>(
    () => api.get("/api/categorias?page=0&size=1000"),
    []
  );
  const { data: unidades } = useAsyncData<string[]>(() => api.get("/api/produtos/opcoes-unidade"), []);

  const [busca, setBusca] = useState("");
  const [filtroCat, setFiltroCat] = useState("");
  const [editando, setEditando] = useState<Produto | null>(null);
  const [form, setForm] = useState<FormProduto>({ ...formVazio });
  const [salvando, setSalvando] = useState(false);
  const [recalcando, setRecalcando] = useState(false);
  const [erro, setErro] = useState("");

  const listCategorias = useMemo(
    () =>
      [...(categorias?.content || [])].sort((a, b) =>
        (a.nome || "").localeCompare(b.nome || "", "pt-BR")
      ),
    [categorias]
  );
  const opcoesUnidade = unidades || [];

  const filtrados = useMemo(() => {
    let lista = data?.content || [];
    if (busca) lista = lista.filter((p) => p.nome.toLowerCase().includes(busca.toLowerCase()));
    if (filtroCat) lista = lista.filter((p) => p.categoriaNome === filtroCat);
    return lista.sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR"));
  }, [data, busca, filtroCat]);

  if (loading) return <div className="muted">Carregando…</div>;
  if (error) return <div className="empty">Erro: {error}</div>;

  function abrirNovo() {
    setForm({ ...formVazio });
    setEditando({} as Produto);
    setErro("");
  }

  function abrirEdicao(p: Produto) {
    setForm({
      nome: p.nome,
      categoriaId: p.categoriaId || "",
      unidadeMedida: p.unidadeMedida,
      estoqueMinimo: String(p.estoqueMinimo),
      saldoNovo: String(p.saldoAtual),
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
        categoriaId: form.categoriaId || null,
        unidadeMedida: form.unidadeMedida,
        estoqueMinimo: parseFloat(form.estoqueMinimo.replace(",", ".")),
      };
      if (editando?.id) {
        await api.put(`/api/produtos/${editando.id}`, body);
        const saldoNovo = parseFloat((form.saldoNovo || "").replace(",", "."));
        if (!Number.isNaN(saldoNovo) && saldoNovo !== editando.saldoAtual) {
          const diferenca = NumeroReal(saldoNovo - editando.saldoAtual);
          if (diferenca !== 0) {
            await api.post("/api/movimentacoes/ajuste", {
              produtoId: editando.id,
              diferenca,
              justificativa: "Ajuste manual pela edição de produto.",
            });
          }
        }
      } else {
        await api.post("/api/produtos", body);
      }
      setEditando(null);
      reload();
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao salvar.");
    } finally {
      setSalvando(false);
    }
  }

  function NumeroReal(v: number): number {
    return Number(v.toFixed(3));
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

  async function recalcularParametros() {
    setRecalcando(true);
    try {
      await api.post("/api/parametros-estoque/recalcular-todos");
      reload();
    } catch (e: unknown) {
      window.alert((e as Error).message || "Erro ao recalcular parâmetros.");
    } finally {
      setRecalcando(false);
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
            {listCategorias.map((c) => (
              <option key={c.id} value={c.nome || ""}>
                {c.nome}
              </option>
            ))}
          </select>
          {admin && (
            <>
              <button className="btn" disabled={recalcando} onClick={recalcularParametros}>
                {recalcando ? "Calculando…" : "Recalcular parâmetros"}
              </button>
              <button className="btn primary" onClick={abrirNovo}>+ Novo produto</button>
            </>
          )}
        </div>
      </div>

      <div className="table-wrap">
        <table className="tbl">
          <thead>
            <tr>
              <th>Produto</th>
              <th>Categoria</th>
              <th>Unidade</th>
              <th className="num">Saldo atual</th>
              <th className="num">Estoque mínimo</th>
              {admin && <th style={{ width: 120 }}>Ações</th>}
            </tr>
          </thead>
          <tbody>
            {filtrados.map((p) => {
              const abaixo = p.saldoAtual < p.estoqueMinimo;
              return (
                <tr key={p.id}>
                  <td><strong>{p.nome}</strong></td>
                  <td>{p.categoriaNome || "—"}</td>
                  <td>{p.unidadeMedida}</td>
                  <td className="num">
                    <span style={abaixo ? { color: "var(--danger)", fontWeight: 700 } : undefined}>
                      {formatQtd(p.saldoAtual)}
                    </span>{" "}
                    {abaixo && <span className="badge danger">Repor</span>}
                  </td>
                  <td className="num">{formatQtd(p.estoqueMinimo)}</td>
                  {admin && (
                    <td>
                      <button className="btn small" onClick={() => abrirEdicao(p)}>Editar</button>{" "}
                      <button className="btn small danger" onClick={() => excluir(p)}>Excluir</button>
                    </td>
                  )}
                </tr>
              );
            })}
            {filtrados.length === 0 && (
              <tr><td colSpan={admin ? 6 : 5} className="empty">Nenhum produto encontrado.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {editando && (
        <Modal title={editando.id ? "Editar produto" : "Novo produto"} onClose={() => setEditando(null)}>
          <div className="form-row">
            <div className="field" style={{ gridColumn: "span 2" }}>
              <label>Nome *</label>
              <input value={form.nome} onChange={(e) => setForm({ ...form, nome: e.target.value })} />
            </div>
            <div className="field">
              <label>Categoria</label>
              <select value={form.categoriaId} onChange={(e) => setForm({ ...form, categoriaId: e.target.value })}>
                <option value="">Sem categoria</option>
                {listCategorias.map((c) => (
                  <option key={c.id} value={c.id}>{c.nome}</option>
                ))}
              </select>
            </div>
            <div className="field">
              <label>Unidade *</label>
              <select value={form.unidadeMedida} onChange={(e) => setForm({ ...form, unidadeMedida: e.target.value })}>
                {opcoesUnidade.map((u) => (
                  <option key={u} value={u}>{u}</option>
                ))}
              </select>
            </div>
            <div className="field">
              <label>Estoque mínimo</label>
              <input
                type="number"
                min="0"
                step="1"
                value={form.estoqueMinimo}
                onChange={(e) => setForm({ ...form, estoqueMinimo: e.target.value })}
              />
            </div>
            {editando.id && (
              <div className="field">
                <label>Saldo atual (editar)</label>
                <input
                  type="number"
                  min="0"
                  step="any"
                  value={form.saldoNovo}
                  onChange={(e) => setForm({ ...form, saldoNovo: e.target.value })}
                />
                <span className="muted small">Altera o estoque disponível do produto.</span>
              </div>
            )}
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