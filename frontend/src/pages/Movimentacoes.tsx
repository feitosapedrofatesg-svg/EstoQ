import { useState } from "react";
import { api, carregarProdutos, formatDateTime, formatMoney, formatQtd, hojeIso, inicioMesIso } from "../api";
import { useAuth } from "../auth";
import { Badge, Modal, useAsyncData } from "../components";
import type { LoteView, MovimentacaoView, Produto, ProdutoAbertoView } from "../types";

type Aba = "consumo" | "entrada";

const tipoLabel: Record<string, string> = {
  ENTRADA: "Entrada",
  CONSUMO: "Consumo",
  DESPERDICIO: "Desperdício",
  AJUSTE: "Ajuste",
};

export default function Movimentacoes() {
  const { auth } = useAuth();
  const admin = auth?.perfil === "ADMIN";

  const [aba, setAba] = useState<Aba>("consumo");
  const [refresh, setRefresh] = useState(0);

  // listagem
  const [inicio, setInicio] = useState(inicioMesIso());
  const [fim, setFim] = useState(hojeIso());
  const { data: movs, loading: loadingMovs } = useAsyncData<MovimentacaoView[]>(
    () => api.get(`/api/movimentacoes?inicio=${inicio}&fim=${fim}`),
    [inicio, fim, refresh]
  );

  // catálogo de produtos (todas as páginas)
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
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao abrir embalagem.");
    } finally {
      setSalvando(false);
    }
  }

  // entrada
  const [produtoEntrada, setProdutoEntrada] = useState("");
  const [qtdEntrada, setQtdEntrada] = useState("1");
  const [valorTotalPago, setValorTotalPago] = useState("");
  const [unidadeCompra, setUnidadeCompra] = useState("");
  const [dataValidade, setDataValidade] = useState("");
  const [obsEntrada, setObsEntrada] = useState("");
  const { data: unidades } = useAsyncData<string[]>(() => api.get("/api/produtos/opcoes-unidade"), []);

  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState("");

  async function registrarConsumo() {
    setSalvando(true);
    setErro("");
    try {
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
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao registrar consumo.");
    } finally {
      setSalvando(false);
    }
  }

  async function registrarEntrada() {
    setSalvando(true);
    setErro("");
    try {
      await api.post("/api/movimentacoes/entrada", {
        produtoId: produtoEntrada,
        quantidade: parseFloat(qtdEntrada.replace(",", ".")),
        valorTotalPago: valorTotalPago ? parseFloat(valorTotalPago.replace(",", ".")) : null,
        unidadeCompra: unidadeCompra || null,
        dataValidade: dataValidade || null,
        observacao: obsEntrada || null,
      });
      setProdutoEntrada("");
      setQtdEntrada("1");
      setValorTotalPago("");
      setUnidadeCompra("");
      setDataValidade("");
      setObsEntrada("");
      setRefresh((k) => k + 1);
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao registrar entrada.");
    } finally {
      setSalvando(false);
    }
  }

  async function reverter(m: MovimentacaoView) {
    if (!window.confirm(`Reverter ${tipoLabel[m.tipo]} de "${m.produtoNome}" (${formatQtd(m.quantidade)})?`)) return;
    try {
      await api.post(`/api/movimentacoes/${m.id}/reverter`);
      setRefresh((k) => k + 1);
    } catch (e: unknown) {
      window.alert((e as Error).message || "Erro ao reverter.");
    }
  }

  const lista = movs || [];
  const abertosDoProduto = (abertos || []).filter((a) => !a.finalizado && a.quantidadeRestante > 0);

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Movimentações do dia</h1>
          <p>Registre consumos e entradas de estoque</p>
        </div>
      </div>

      <div className="tabs" role="tablist">
        <button className={`btn${aba === "consumo" ? " primary" : ""}`} onClick={() => { setAba("consumo"); setErro(""); }}>
          Consumo
        </button>
        {admin && (
          <button className={`btn${aba === "entrada" ? " primary" : ""}`} onClick={() => { setAba("entrada"); setErro(""); }}>
            Entrada
          </button>
        )}
      </div>

      {aba === "consumo" && (
        <div className="card card-pad" style={{ marginBottom: 20 }}>
          <h3 style={{ marginTop: 0 }}>Registrar consumo</h3>
          <div className="form-row three">
            <div className="field" style={{ gridColumn: "span 1" }}>
              <label>Produto *</label>
              <select value={produtoConsumo} onChange={(e) => { setProdutoConsumo(e.target.value); setProdutoAbertoId(""); }}>
                <option value="">Selecione…</option>
                {(produtos || []).map((p) => (
                  <option key={p.id} value={p.id}>{p.nome}</option>
                ))}
              </select>
            </div>
            <div className="field">
              <label>Quantidade *</label>
              <input type="number" min="0" step="1" value={qtdConsumo} onChange={(e) => setQtdConsumo(e.target.value)} />
            </div>
            <div className="field">
              <label>Usar embalagem aberta</label>
              {!produtoConsumo ? (
                <div className="muted small">Selecione um produto primeiro</div>
              ) : abertosDoProduto.length === 0 ? (
                <div className="muted small" style={{ padding: "8px 0" }}>
                  Sem embalagem aberta deste produto — use "Abrir embalagem" acima
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
      )}

      {aba === "entrada" && admin && (
        <div className="card card-pad" style={{ marginBottom: 20 }}>
          <h3 style={{ marginTop: 0 }}>Registrar entrada</h3>
          <div className="form-row three">
            <div className="field" style={{ gridColumn: "span 1" }}>
              <label>Produto *</label>
              <select value={produtoEntrada} onChange={(e) => setProdutoEntrada(e.target.value)}>
                <option value="">Selecione…</option>
                {(produtos || []).map((p) => (
                  <option key={p.id} value={p.id}>{p.nome}</option>
                ))}
              </select>
            </div>
            <div className="field">
              <label>Quantidade *</label>
              <input type="number" min="0" step="1" value={qtdEntrada} onChange={(e) => setQtdEntrada(e.target.value)} />
            </div>
            <div className="field">
              <label>Valor total pago (R$)</label>
              <input type="number" min="0" step="0.01" value={valorTotalPago} onChange={(e) => setValorTotalPago(e.target.value)} />
            </div>
          </div>
          <div className="form-row three">
            <div className="field">
              <label>Unidade de compra</label>
              <select value={unidadeCompra} onChange={(e) => setUnidadeCompra(e.target.value)}>
                <option value="">Selecione…</option>
                {(unidades || []).map((u) => (
                  <option key={u} value={u}>{u}</option>
                ))}
              </select>
            </div>
            <div className="field">
              <label>Validade</label>
              <input type="date" value={dataValidade} onChange={(e) => setDataValidade(e.target.value)} />
            </div>
            <div className="field">
              <label>Observação</label>
              <input value={obsEntrada} onChange={(e) => setObsEntrada(e.target.value)} />
            </div>
          </div>
          {erro && <div className="form-error">{erro}</div>}
          <div className="form-actions" style={{ marginTop: 0 }}>
            <button
              className="btn primary"
              disabled={salvando || !produtoEntrada || !qtdEntrada || parseFloat(qtdEntrada) <= 0}
              onClick={registrarEntrada}
            >
              {salvando ? "Salvando…" : "Registrar entrada"}
            </button>
          </div>
        </div>
      )}

      <div className="card card-pad">
        <div className="filtro-line">
          <label className="muted small" htmlFor="mov-inicio">Período:</label>
          <input id="mov-inicio" type="date" value={inicio} onChange={(e) => setInicio(e.target.value)} />
          <input type="date" value={fim} onChange={(e) => setFim(e.target.value)} />
        </div>

        {loadingMovs ? (
          <div className="muted">Carregando…</div>
        ) : lista.length === 0 ? (
          <div className="empty">Nenhuma movimentação no período.</div>
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
                        : m.diferencaApurada != null ? formatQtd(m.diferencaApurada)
                        : "—"}
                    </td>
                    <td className="small muted">{m.observacao || ""}</td>
                    {admin && (
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
              <select value={abrirProduto} onChange={(e) => { setAbrirProduto(e.target.value); setAbrirLote(""); }}>
                <option value="">Selecione…</option>
                {(produtosAbrir || []).map((p) => (
                  <option key={p.id} value={p.id}>{p.nome}</option>
                ))}
              </select>
            </div>
            <div className="field">
              <label>Quantidade</label>
              <input type="number" min="0" step="1" value={abrirQtd} onChange={(e) => setAbrirQtd(e.target.value)} />
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