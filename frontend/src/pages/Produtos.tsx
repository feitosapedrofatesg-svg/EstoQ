import { useMemo, useState } from "react";
import { api, formatMoney, formatQtd } from "../api";
import { useAuth } from "../auth";
import { ErroCarregar, Modal, Notice, useAsyncData } from "../components";
import { useConfirm, useToast, TableSkeleton } from "../ux";
import type { Categoria, Page, Produto } from "../types";

interface FormProduto {
  nome: string;
  categoriaId: string;
  unidadeMedida: string;
  estoqueMinimo: string;
  saldoNovo: string;
  preco: string;
}

const formVazio: FormProduto = { nome: "", categoriaId: "", unidadeMedida: "UN", estoqueMinimo: "7", saldoNovo: "", preco: "" };

export default function Produtos() {
  const { auth } = useAuth();
  const admin = auth?.perfil === "ADMIN";
  const { confirmar } = useConfirm();
  const { toast } = useToast();

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
  const [aviso, setAviso] = useState<{ tipo: "ok" | "danger"; msg: string } | null>(null);

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

  if (loading) return (
    <div>
      <div className="page-head">
        <div><h1>Produtos</h1><p>Carregando catálogo…</p></div>
      </div>
      <TableSkeleton linhas={8} colunas={6} />
    </div>
  );
  if (error) return <ErroCarregar message={error} onTentar={reload} />;

  function abrirNovo() {
    setForm({ ...formVazio });
    setEditando({} as Produto);
    setErro("");
    setAviso(null);
  }

  function abrirEdicao(p: Produto) {
    setForm({
      nome: p.nome,
      categoriaId: p.categoriaId || "",
      unidadeMedida: p.unidadeMedida,
      estoqueMinimo: String(p.estoqueMinimo),
      saldoNovo: String(p.saldoAtual),
      preco: p.precoUnitario != null ? String(p.precoUnitario) : "",
    });
    setEditando(p);
    setErro("");
    setAviso(null);
  }

  async function salvar() {
    setSalvando(true);
    setErro("");
    setAviso(null);
    try {
      const body = {
        nome: form.nome,
        categoriaId: form.categoriaId || null,
        unidadeMedida: form.unidadeMedida,
        estoqueMinimo: parseFloat(form.estoqueMinimo.replace(",", ".")),
        precoUnitario: (() => {
          const v = parseFloat((form.preco || "").replace(",", "."));
          return Number.isNaN(v) ? null : v;
        })(),
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
      toast(editando?.id ? `Produto "${form.nome}" atualizado.` : `Produto "${form.nome}" criado.`);
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
    const ok = await confirmar({
      titulo: "Excluir produto",
      texto: <>Excluir <strong>{p.nome}</strong> ({formatQtd(p.saldoAtual)} {p.unidadeMedida})? Esta ação não pode ser desfeita.</>,
      confirmarLabel: "Excluir",
      perigo: true,
    });
    if (!ok) return;
    setAviso(null);
    try {
      await api.del(`/api/produtos/${p.id}`);
      setAviso({ tipo: "ok", msg: `Produto "${p.nome}" excluído.` });
      reload();
      toast(`Produto excluído: ${p.nome}`, "danger");
    } catch (e: unknown) {
      setAviso({ tipo: "danger", msg: (e as Error).message || "Erro ao excluir." });
    }
  }

  async function resetarParametros() {
    const ok = await confirmar({
      titulo: "Resetar parâmetros de estoque",
      texto: "O saldo atual e o estoque mínimo de todos os produtos serão zerados. Continuar?",
      confirmarLabel: "Resetar tudo",
      perigo: true,
    });
    if (!ok) return;
    setRecalcando(true);
    setAviso(null);
    try {
      await api.post("/api/parametros-estoque/resetar-todos");
      setAviso({ tipo: "ok", msg: "Parâmetros de estoque de todos os produtos resetados." });
      reload();
      toast("Parâmetros de estoque resetados.");
    } catch (e: unknown) {
      setAviso({ tipo: "danger", msg: (e as Error).message || "Erro ao resetar parâmetros." });
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
              <button className="btn" disabled={recalcando} onClick={resetarParametros}>
                {recalcando ? "Resetando…" : "Resetar parâmetros"}
              </button>
              <button className="btn primary" onClick={abrirNovo}>+ Novo produto</button>
            </>
          )}
        </div>
      </div>

      {aviso && <Notice tipo={aviso.tipo}>{aviso.msg}</Notice>}

      <div className="table-wrap">
        <table className="tbl">
          <thead>
            <tr>
              <th>Produto</th>
              <th>Categoria</th>
              <th>Unidade</th>
              <th className="num">Saldo atual</th>
              <th className="num">Estoque mínimo</th>
              <th className="num">Custo unit.</th>
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
                  <td className="num">{p.precoUnitario != null ? formatMoney(p.precoUnitario) : "—"}</td>
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
              <tr><td colSpan={admin ? 7 : 6} className="empty">Nenhum produto encontrado.</td></tr>
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
            <div className="field">
              <label>Custo unitário (R$)</label>
              <input
                type="number"
                min="0"
                step="0.01"
                placeholder="0,00"
                value={form.preco}
                onChange={(e) => setForm({ ...form, preco: e.target.value })}
              />
              <span className="muted small">Opcional. Deixe em branco se não souber.</span>
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