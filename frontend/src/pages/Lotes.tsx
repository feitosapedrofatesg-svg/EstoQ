import { useMemo, useState } from "react";
import { api, carregarProdutos, formatDate, formatMoney, formatQtd } from "../api";
import { useAuth } from "../auth";
import { Badge, Modal, useAsyncData } from "../components";
import { ProductPicker, QuantityInput, useToast, TableSkeleton } from "../ux";
import type { LoteView, Produto, ProdutoAbertoView } from "../types";

function statusLote(l: LoteView) {
  if (l.vencido) return { status: "VENCIDO", label: "Vencido" };
  if (l.diasParaVencimento <= 7) return { status: "ATENCAO", label: `Vence em ${l.diasParaVencimento} dia(s)` };
  return { status: "OK", label: "OK" };
}

export default function Lotes() {
  const { auth } = useAuth();
  const admin = auth?.perfil === "ADMIN";
  const { toast } = useToast();

  const [refresh, setRefresh] = useState(0);
  const [busca, setBusca] = useState("");
  const [soDisponiveis, setSoDisponiveis] = useState(false);

  const [sobraDe, setSobraDe] = useState<ProdutoAbertoView | null>(null);
  const [qtdSobra, setQtdSobra] = useState("");
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState("");

  const [abrindo, setAbrindo] = useState(false);
  const [abrirProduto, setAbrirProduto] = useState("");
  const [abrirQtd, setAbrirQtd] = useState("1");
  const [abrirLote, setAbrirLote] = useState("");

  const [editandoVal, setEditandoVal] = useState<LoteView | null>(null);
  const [validadeTemp, setValidadeTemp] = useState("");
  const [salvandoVal, setSalvandoVal] = useState(false);

  const { data: lotes, loading: loadingLotes } = useAsyncData<LoteView[]>(() => api.get("/api/lotes"), [refresh]);
  const { data: abertos } = useAsyncData<ProdutoAbertoView[]>(() => api.get("/api/produtos-abertos"), [refresh]);
  const { data: produtos } = useAsyncData<Produto[]>(() => carregarProdutos(), []);
  const { data: lotesDisponiveis } = useAsyncData<LoteView[]>(
    () => (abrirProduto ? api.get(`/api/lotes/disponiveis?produtoId=${abrirProduto}`) : Promise.resolve([])),
    [abrirProduto]
  );

  const filtrados = useMemo(() => {
    let lista = lotes || [];
    if (busca) {
      const q = busca.toLowerCase();
      lista = lista.filter(
        (l) => l.codigo.toLowerCase().includes(q) || l.produtoNome.toLowerCase().includes(q)
      );
    }
    if (soDisponiveis) lista = lista.filter((l) => l.disponivel);
    return [...lista].sort((a, b) => a.dataValidade?.localeCompare(b.dataValidade || "") || 0);
  }, [lotes, busca, soDisponiveis]);

  async function registrarSobra() {
    if (!sobraDe) return;
    setSalvando(true);
    setErro("");
    try {
      await api.post("/api/movimentacoes/sobra", {
        produtoAbertoId: sobraDe.id,
        quantidade: parseFloat(qtdSobra.replace(",", ".")),
      });
      setSobraDe(null);
      setQtdSobra("");
      setRefresh((k) => k + 1);
      toast("Sobra registrada e devolvida ao estoque.");
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao registrar sobra.");
    } finally {
      setSalvando(false);
    }
  }

  async function salvarValidade() {
    if (!editandoVal) return;
    setSalvandoVal(true);
    setErro("");
    try {
      await api.put(`/api/lotes/${editandoVal.id}/validade`, {
        dataValidade: validadeTemp || null,
      });
      setEditandoVal(null);
      setValidadeTemp("");
      setRefresh((k) => k + 1);
      toast("Validade do lote atualizada.");
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao salvar validade.");
    } finally {
      setSalvandoVal(false);
    }
  }

  async function abrirEmbalagem() {
    setSalvando(true);
    setErro("");
    try {
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
      toast("Embalagem aberta e registrada no sistema.");
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao abrir embalagem.");
    } finally {
      setSalvando(false);
    }
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Estoque</h1>
          <p>Vencimentos, saldos por lote e embalagens abertas</p>
        </div>
        {admin && (
          <div className="page-actions">
            <button className="btn primary" onClick={() => { setAbrindo(true); setErro(""); }}>Abrir embalagem</button>
          </div>
        )}
      </div>

      <div className="filtro-line">
        <input
          className="search"
          placeholder="Buscar por código ou produto…"
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
        />
        <label className="toggle">
          <input type="checkbox" checked={soDisponiveis} onChange={(e) => setSoDisponiveis(e.target.checked)} />
          Só disponíveis
        </label>
      </div>

      {loadingLotes ? (
        <TableSkeleton linhas={6} colunas={9} />
      ) : filtrados.length === 0 ? (
        <div className="empty">Nenhum lote encontrado.</div>
      ) : (
        <div className="table-wrap">
          <table className="tbl">
            <thead>
              <tr>
                <th>Código</th>
                <th>Produto</th>
                <th className="num">Qtd. atual</th>
                <th className="num">Qtd. inicial</th>
                <th>Entrada</th>
                <th>Validade</th>
                <th className="num">Preço unit.</th>
                <th className="num">Dias p/ vencer</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {filtrados.map((l) => {
                const st = statusLote(l);
                return (
                  <tr key={l.id}>
                    <td><strong>{l.codigo}</strong></td>
                    <td>{l.produtoNome}</td>
                    <td className="num">{formatQtd(l.quantidadeAtual)} {l.unidadeMedida}</td>
                    <td className="num">{formatQtd(l.quantidadeInicial)}</td>
                    <td>{formatDate(l.dataEntrada)}</td>
                    <td>
                      {formatDate(l.dataValidade)}
                      {admin && (
                        <button
                          className="btn small"
                          style={{ marginLeft: 8 }}
                          onClick={() => {
                            setEditandoVal(l);
                            setValidadeTemp(l.dataValidade || "");
                            setErro("");
                          }}
                        >
                          Editar
                        </button>
                      )}
                    </td>
                    <td className="num">{formatMoney(l.precoUnitario)}</td>
                    <td className="num">{l.diasParaVencimento}</td>
                    <td>
                      <span
                        className={`badge ${st.status === "VENCIDO" ? "danger" : st.status === "ATENCAO" ? "warn" : "ok"}`}
                      >
                        {st.label}
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <h3 style={{ margin: "26px 0 10px" }}>Embalagens abertas</h3>
      {!abertos || abertos.length === 0 ? (
        <div className="empty">Nenhuma embalagem aberta.</div>
      ) : (
        <div className="table-wrap">
          <table className="tbl">
            <thead>
              <tr>
                <th>Produto</th>
                <th>Abertura</th>
                <th className="num">Aberto</th>
                <th className="num">Utilizado</th>
                <th className="num">Restante</th>
                <th>Status</th>
                {admin && <th style={{ width: 140 }}>Ações</th>}
              </tr>
            </thead>
            <tbody>
              {abertos.map((a) => (
                <tr key={a.id}>
                  <td><strong>{a.produtoNome}</strong></td>
                  <td>{formatDate(a.dataAbertura)}</td>
                  <td className="num">{formatQtd(a.quantidadeAberta)} {a.unidadeMedida}</td>
                  <td className="num">{formatQtd(a.quantidadeUtilizada)}</td>
                  <td className="num">{formatQtd(a.quantidadeRestante)}</td>
                  <td>{a.finalizado ? <Badge status="CONCLUIDO" /> : <Badge status="ABERTO" />}</td>
                  {admin && (
                    <td>
                      <button
                        className="btn small"
                        disabled={a.finalizado || a.quantidadeRestante <= 0}
                        onClick={() => { setSobraDe(a); setQtdSobra(String(a.quantidadeRestante)); setErro(""); }}
                      >
                        Registrar sobra
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {sobraDe && (
        <Modal title={`Registrar sobra — ${sobraDe.produtoNome}`} onClose={() => { setSobraDe(null); setErro(""); }}>
          <div className="field">
            <label>Quantidade restante a devolver</label>
            <QuantityInput
              value={qtdSobra}
              onChange={setQtdSobra}
              min={0}
              step={1}
              unidade={sobraDe?.unidadeMedida}
            />
          </div>
          {erro && <div className="form-error">{erro}</div>}
          <div className="form-actions">
            <button className="btn" onClick={() => setSobraDe(null)}>Cancelar</button>
            <button className="btn primary" disabled={salvando || !qtdSobra || parseFloat(qtdSobra) <= 0} onClick={registrarSobra}>
              {salvando ? "Salvando…" : "Registrar sobra"}
            </button>
          </div>
        </Modal>
      )}

      {editandoVal && (
        <Modal
          title={`Editar validade — ${editandoVal.produtoNome} (${editandoVal.codigo})`}
          onClose={() => { setEditandoVal(null); setErro(""); }}
        >
          <div className="field">
            <label>Validade</label>
            <input
              type="date"
              value={validadeTemp}
              onChange={(e) => setValidadeTemp(e.target.value)}
            />
            <span className="muted small">Deixe vazio para remover a validade do lote.</span>
          </div>
          {erro && <div className="form-error">{erro}</div>}
          <div className="form-actions">
            <button className="btn" onClick={() => setEditandoVal(null)}>Cancelar</button>
            <button className="btn primary" disabled={salvandoVal} onClick={salvarValidade}>
              {salvandoVal ? "Salvando…" : "Salvar validade"}
            </button>
          </div>
        </Modal>
      )}

      {abrindo && (
        <Modal title="Abrir embalagem" onClose={() => { setAbrindo(false); setErro(""); }}>
          <div className="form-row">
            <div className="field">
              <label>Produto *</label>
              <ProductPicker
                produtos={produtos || []}
                value={abrirProduto}
                placeholder="Buscar produto…"
                onChange={(id) => { setAbrirProduto(id); setAbrirLote(""); }}
              />
            </div>
            <div className="field">
              <label>Quantidade</label>
              <QuantityInput
                value={abrirQtd}
                onChange={setAbrirQtd}
                min={0}
                step={1}
                unidade={(produtos || []).find((p) => p.id === abrirProduto)?.unidadeMedida}
              />
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