import { useMemo, useState } from "react";
import { api, carregarProdutos, formatDateTime, formatMoney, formatQtd, hojeIso, inicioMesIso } from "../api";
import { useAuth } from "../auth";
import { Badge, Modal, useAsyncData } from "../components";
import { ProductPicker, QuantityInput, useConfirm, useToast, TableSkeleton } from "../ux";
import type { LoteView, MovimentacaoView, Produto, ProdutoAbertoView } from "../types";

const tipoLabel: Record<string, string> = {
  CONSUMO: "Consumo",
  DESPERDICIO: "Desperdício",
};

export default function Consumo() {
  const { auth } = useAuth();
  const admin = auth?.perfil === "ADMIN";
  const { confirmar } = useConfirm();
  const { toast } = useToast();

  const [refresh, setRefresh] = useState(0);

  // listagem
  const [inicio, setInicio] = useState(inicioMesIso());
  const [fim, setFim] = useState(hojeIso());
  const { data: movs, loading: loadingMovs } = useAsyncData<MovimentacaoView[]>(
    () => api.get(`/api/movimentacoes?inicio=${inicio}&fim=${fim}`),
    [inicio, fim, refresh]
  );

  // catálogo de produtos
  const { data: produtos } = useAsyncData<Produto[]>(() => carregarProdutos(), [refresh]);

  // consumo
  const [produtoConsumo, setProdutoConsumo] = useState("");
  const [qtdConsumo, setQtdConsumo] = useState("1");
  const [produtoAbertoId, setProdutoAbertoId] = useState("");
  const [obsConsumo, setObsConsumo] = useState("");
  const { data: abertos } = useAsyncData<ProdutoAbertoView[]>(
    () => (produtoConsumo ? api.get(`/api/produtos-abertos?produtoId=${produtoConsumo}`) : Promise.resolve([])),
    [produtoConsumo, refresh]
  );

  // abrir embalagem
  const [abrindo, setAbrindo] = useState(false);
  const [abrirProduto, setAbrirProduto] = useState("");
  const [abrirQtd, setAbrirQtd] = useState("1");
  const [abrirLote, setAbrirLote] = useState("");
  const { data: lotesDisponiveis } = useAsyncData<LoteView[]>(
    () => (abrirProduto ? api.get(`/api/lotes/disponiveis?produtoId=${abrirProduto}`) : Promise.resolve([])),
    [abrirProduto]
  );
  const { data: produtosAbrir } = useAsyncData<Produto[]>(() => carregarProdutos(), []);

  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState("");
  const [aviso, setAviso] = useState<{ tipo: "ok" | "danger"; msg: string } | null>(null);

  const unidadeConsumo = useMemo(
    () => (produtos || []).find((p) => p.id === produtoConsumo)?.unidadeMedida || "",
    [produtos, produtoConsumo]
  );

  // produtos usados hoje — acesso rápido (recall, não memória)
  const usadosHoje = useMemo(() => {
    const hoje = hojeIso();
    const vistos = new Set<string>();
    const saidas = [...(movs || [])].reverse();
    const lista: { id: string; nome: string; un: string }[] = [];
    for (const m of saidas) {
      if (m.tipo !== "CONSUMO" && m.tipo !== "DESPERDICIO") continue;
      if ((m.dataHora || "").slice(0, 10) !== hoje) continue;
      if (!m.produtoId || vistos.has(m.produtoId)) continue;
      vistos.add(m.produtoId);
      lista.push({ id: m.produtoId, nome: m.produtoNome, un: m.unidadeMedida });
      if (lista.length >= 8) break;
    }
    return lista;
  }, [movs]);

  async function abrirEmbalagem() {
    setSalvando(true);
    setErro("");
    setAviso(null);
    try {
      const nome = (produtosAbrir || []).find((p) => p.id === abrirProduto)?.nome || "";
      await api.post("/api/produtos-abertos/abrir", {
        produtoId: abrirProduto,
        quantidade: parseFloat(abrirQtd.replace(",", ".")),
        loteId: abrirLote || null,
      });
      setAbrindo(false);
      setAbrirProduto("");
      setAbrirQtd("1");
      setAbrirLote("");
      setRefresh((k) => k + 1);
      toast(`Embalagem aberta: ${nome}`);
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao abrir embalagem.");
    } finally {
      setSalvando(false);
    }
  }

  async function registrarConsumo() {
    setSalvando(true);
    setErro("");
    setAviso(null);
    try {
      const nome = (produtos || []).find((p) => p.id === produtoConsumo)?.nome || "";
      await api.post("/api/movimentacoes/consumo", {
        produtoId: produtoConsumo,
        quantidade: parseFloat(qtdConsumo.replace(",", ".")),
        produtoAbertoId: produtoAbertoId || null,
        observacao: obsConsumo || null,
      });
      setProdutoConsumo("");
      setQtdConsumo("1");
      setProdutoAbertoId("");
      setObsConsumo("");
      setRefresh((k) => k + 1);
      toast(`${nome} — consumo registrado (${formatQtd(parseFloat(qtdConsumo.replace(",", ".")))} ${unidadeConsumo})`);
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao registrar consumo.");
    } finally {
      setSalvando(false);
    }
  }

  async function reverter(m: MovimentacaoView) {
    const ok = await confirmar({
      titulo: `Reverter ${tipoLabel[m.tipo]}`,
      texto: <>Reverter <strong>{m.produtoNome}</strong> ({formatQtd(m.quantidade)}) e voltar para o estoque?</>,
      confirmarLabel: "Reverter",
      perigo: true,
    });
    if (!ok) return;
    setAviso(null);
    try {
      await api.post(`/api/movimentacoes/${m.id}/reverter`);
      setAviso({ tipo: "ok", msg: `${tipoLabel[m.tipo]} de "${m.produtoNome}" revertido com sucesso.` });
      toast(`${tipoLabel[m.tipo]} revertido: ${m.produtoNome}`);
      setRefresh((k) => k + 1);
    } catch (e: unknown) {
      setAviso({ tipo: "danger", msg: (e as Error).message || "Erro ao reverter." });
    }
  }

  const lista = (movs || []).filter((m) => m.tipo === "CONSUMO" || m.tipo === "DESPERDICIO");
  const abertosDoProduto = (abertos || []).filter((a) => !a.finalizado && a.quantidadeRestante > 0);
  const periodoInvalido = inicio > fim;

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Consumo do dia</h1>
          <p>O que a cozinha vai usar hoje</p>
        </div>
      </div>

      {aviso && <div className={`aviso ${aviso.tipo}`}>{aviso.msg}</div>}

      <div className="card card-pad" style={{ marginBottom: 20 }}>
        <h3 style={{ marginTop: 0 }}>Registrar consumo</h3>

        {usadosHoje.length > 0 && (
          <>
            <p className="small muted" style={{ margin: "0 0 6px" }}>
              Usados hoje — toque para preencher:
            </p>
            <div className="chips">
              {usadosHoje.map((u) => (
                <button
                  key={u.id}
                  type="button"
                  className={`chip${produtoConsumo === u.id ? " active" : ""}`}
                  onClick={() => {
                    setProdutoConsumo(u.id);
                    setProdutoAbertoId("");
                  }}
                >
                  {u.nome} <span className="chip-un">{u.un}</span>
                </button>
              ))}
            </div>
          </>
        )}

        <div className="form-row three">
          <div className="field" style={{ gridColumn: "span 1" }}>
            <label>Produto *</label>
            <ProductPicker
              produtos={produtos || []}
              value={produtoConsumo}
              placeholder="Buscar produto…"
              onChange={(id) => {
                setProdutoConsumo(id);
                setProdutoAbertoId("");
              }}
            />
          </div>
          <div className="field">
            <label>Quantidade *</label>
            <QuantityInput
              value={qtdConsumo}
              onChange={setQtdConsumo}
              min={0}
              step={1}
              unidade={unidadeConsumo}
            />
          </div>
          <div className="field">
            <label>Usar embalagem aberta</label>
            {!produtoConsumo ? (
              <div className="muted small">Selecione um produto primeiro</div>
            ) : abertosDoProduto.length === 0 ? (
              <div className="muted small" style={{ padding: "8px 0" }}>
                Sem embalagem aberta deste produto — use "Abrir embalagem" abaixo
              </div>
            ) : (
              <>
                <label className="radio-line">
                  <input
                    type="radio"
                    name="usar-aberto"
                    checked={produtoAbertoId === ""}
                    onChange={() => setProdutoAbertoId("")}
                  />
                  <span>Automático (FIFO)</span>
                </label>
                {abertosDoProduto.map((a) => (
                  <label key={a.id} className="radio-line">
                    <input
                      type="radio"
                      name="usar-aberto"
                      checked={produtoAbertoId === a.id}
                      onChange={() => setProdutoAbertoId(a.id)}
                    />
                    <span>
                      Aberto · resto <strong>{formatQtd(a.quantidadeRestante)} {a.unidadeMedida}</strong>
                    </span>
                  </label>
                ))}
              </>
            )}
          </div>
        </div>
        <div className="form-row three">
          <div className="field" style={{ gridColumn: "span 2" }}>
            <label>Observação</label>
            <input value={obsConsumo} onChange={(e) => setObsConsumo(e.target.value)} />
          </div>
          <div className="field" style={{ justifyContent: "flex-end" }}>
            <button
              className="btn"
              style={{ marginRight: 8 }}
              onClick={() => { setAbrindo(true); setErro(""); }}
            >
              Abrir embalagem
            </button>
            <button
              className="btn primary"
              disabled={salvando || !produtoConsumo || !qtdConsumo || parseFloat(qtdConsumo) <= 0}
              onClick={registrarConsumo}
            >
              {salvando ? "Salvando…" : "Registrar consumo"}
            </button>
          </div>
        </div>
        {erro && <div className="form-error">{erro}</div>}
      </div>

      <div className="card card-pad">
        <div className="filtro-line">
          <label className="muted small" htmlFor="cons-inicio">Período:</label>
          <input id="cons-inicio" type="date" value={inicio} onChange={(e) => setInicio(e.target.value)} />
          <input type="date" value={fim} onChange={(e) => setFim(e.target.value)} />
        </div>

        {periodoInvalido ? (
          <div className="aviso">
            Período inválido: a data inicial é posterior à data final. Ajuste o intervalo para listar as movimentações.
          </div>
        ) : loadingMovs ? (
          <TableSkeleton linhas={5} colunas={8} />
        ) : lista.length === 0 ? (
          <div className="empty">Nenhuma saída de estoque no período.</div>
        ) : (
          <div className="table-wrap">
            <table className="tbl">
              <thead>
                <tr>
                  <th>Data/hora</th>
                  <th>Tipo</th>
                  <th>Produto</th>
                  <th className="num">Quantidade</th>
                  <th>Usuário</th>
                  <th>Lote</th>
                  <th>Valor</th>
                  <th>Observação</th>
                  {admin && <th style={{ width: 100 }}>Ações</th>}
                </tr>
              </thead>
              <tbody>
                {lista.slice().reverse().map((m) => (
                  <tr key={m.id}>
                    <td>{formatDateTime(m.dataHora)}</td>
                    <td><Badge status={m.tipo} /></td>
                    <td><strong>{m.produtoNome}</strong></td>
                    <td className="num">{formatQtd(m.quantidade)} {m.unidadeMedida}</td>
                    <td>{m.usuarioNome || "—"}</td>
                    <td>{m.loteCodigo || "—"}</td>
                    <td className="num">
                      {m.valorPrejuizo != null ? formatMoney(m.valorPrejuizo)
                        : m.custoConsumo != null ? formatMoney(m.custoConsumo)
                        : "—"}
                    </td>
                    <td className="small muted">{m.observacao || ""}</td>
                    {admin &&
                      (m.tipo === "CONSUMO" || m.tipo === "DESPERDICIO") && (
                        <td>
                          <button className="btn small danger" onClick={() => reverter(m)}>Reverter</button>
                        </td>
                      )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {abrindo && (
        <Modal title="Abrir embalagem" onClose={() => { setAbrindo(false); setErro(""); }}>
          <div className="form-row">
            <div className="field">
              <label>Produto *</label>
              <ProductPicker
                produtos={produtosAbrir || []}
                value={abrirProduto}
                placeholder="Buscar produto…"
                onChange={(id) => { setAbrirProduto(id); setAbrirLote(""); }}
              />
            </div>
            <div className="field">
              <label>Quantidade</label>
              <QuantityInput value={abrirQtd} onChange={setAbrirQtd} min={0} step={1} />
            </div>
            <div className="field" style={{ gridColumn: "span 2" }}>
              <label>Lote (opcional)</label>
              <select
                value={abrirLote}
                disabled={!abrirProduto || (lotesDisponiveis || []).length === 0}
                onChange={(e) => setAbrirLote(e.target.value)}
              >
                <option value="">Automático (FIFO)</option>
                {(lotesDisponiveis || []).map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.codigo} · {formatQtd(l.quantidadeAtual)} {l.unidadeMedida}
                  </option>
                ))}
              </select>
            </div>
          </div>
          {erro && <div className="form-error">{erro}</div>}
          <div className="form-actions">
            <button className="btn" onClick={() => setAbrindo(false)}>Cancelar</button>
            <button
              className="btn primary"
              disabled={salvando || !abrirProduto || !abrirQtd || parseFloat(abrirQtd) <= 0}
              onClick={abrirEmbalagem}
            >
              {salvando ? "Salvando…" : "Abrir embalagem"}
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}