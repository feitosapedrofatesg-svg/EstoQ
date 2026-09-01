import { useMemo, useState } from "react";
import { api, formatQtd } from "../api";
import { useAuth } from "../auth";
import { useAsyncData } from "../components";
import type { ConsumoDiario, Produto, TipoUso } from "../types";

function hoje() {
  return new Date().toISOString().slice(0, 10);
}

export default function UsoDiario() {
  const { auth } = useAuth();
  const [data, setData] = useState(hoje());
  const [busca, setBusca] = useState("");
  const [selecionado, setSelecionado] = useState<Produto | null>(null);
  const [qtd, setQtd] = useState(1);
  const [erro, setErro] = useState("");
  const [salvandoTipo, setSalvandoTipo] = useState<TipoUso | null>(null);
  const [refresh, setRefresh] = useState(0);

  const { data: registros, loading } = useAsyncData<ConsumoDiario[]>(
    () => api.get(`/api/consumo-diario?data=${data}`),
    [data, refresh]
  );

  const { data: produtos } = useAsyncData<{ content: Produto[] }>(
    () => api.get("/api/produtos?size=500"),
    []
  );

  const catalogo = useMemo(() => {
    let lista = produtos?.content || [];
    if (busca) {
      const q = busca.toLowerCase();
      lista = lista.filter((p) => p.nome.toLowerCase().includes(q) || (p.categoria || "").toLowerCase().includes(q));
    }
    return lista.sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR")).slice(0, 40);
  }, [produtos, busca]);

  const soma = useMemo(() => {
    const mapa = new Map<string, { produtoId: string; produtoNome: string; unidade: string; tipo: TipoUso; quantidade: number; qtdItens: number }>();
    (registros || []).forEach((r) => {
      const chave = r.produtoId + "::" + r.tipo;
      const item = mapa.get(chave) || {
        produtoId: r.produtoId,
        produtoNome: r.produtoNome,
        unidade: r.unidade,
        tipo: r.tipo,
        quantidade: 0,
        qtdItens: 0,
      };
      item.quantidade += r.quantidade;
      item.qtdItens += 1;
      mapa.set(chave, item);
    });
    return Array.from(mapa.values()).sort((a, b) => a.produtoNome.localeCompare(b.produtoNome, "pt-BR"));
  }, [registros]);

  const podeExcluir = (r: ConsumoDiario) =>
    auth?.perfil === "ADMIN" || (r.usuarioNome === auth?.nome && r.data === hoje());

  async function registrar(tipo: TipoUso) {
    if (!selecionado) {
      setErro("Escolha um produto primeiro.");
      return;
    }
    if (!qtd || qtd <= 0) {
      setErro("Informe uma quantidade maior que zero.");
      return;
    }
    setErro("");
    setSalvandoTipo(tipo);
    try {
      await api.post("/api/consumo-diario", {
        data,
        produtoId: selecionado.id,
        tipo,
        quantidade: qtd,
      });
      setRefresh((k) => k + 1);
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao registrar.");
    } finally {
      setSalvandoTipo(null);
    }
  }

  async function excluir(id: string) {
    try {
      await api.del(`/api/consumo-diario/${id}`);
      setRefresh((k) => k + 1);
    } catch (e: unknown) {
      window.alert((e as Error).message || "Erro ao excluir.");
    }
  }

  return (
    <div style={{ maxWidth: 1200 }}>
      <div className="page-head">
        <div>
          <h1>Uso diário</h1>
          <p>Registre os itens usados e os itens abertos no dia</p>
        </div>
        <div className="page-actions">
          <label className="muted small" htmlFor="data-uso">Dia:</label>
          <input id="data-uso" type="date" value={data} max={hoje()} onChange={(e) => setData(e.target.value)} />
        </div>
      </div>

      <div className="grid two">
        {/* Painel de registro */}
        <div className="card card-pad" style={{ minHeight: 420 }}>
          <h3 style={{ marginTop: 0 }}>Registrar</h3>

          <div className="field">
            <input
              className="search"
              style={{ width: "100%" }}
              placeholder="Buscar produto…"
              value={busca}
              onChange={(e) => { setBusca(e.target.value); setSelecionado(null); }}
            />
          </div>

          {busca && (
            <div className="prod-list">
              {catalogo.length === 0 && <div className="empty">Nenhum produto encontrado.</div>}
              {catalogo.map((p) => (
                <button
                  key={p.id}
                  className={`prod-item${selecionado?.id === p.id ? " sel" : ""}`}
                  onClick={() => { setSelecionado(p); setBusca(p.nome); }}
                >
                  <span className="prod-nome">{p.nome}</span>
                  <span className="muted small">{p.categoria || "—"}</span>
                </button>
              ))}
            </div>
          )}

          {selecionado && (
            <div className="qtd-row">
              <div className="qtd-prod">
                <strong>{selecionado.nome}</strong>
                <span className="muted small">({selecionado.unidade})</span>
              </div>
              <button className="btn qtd-btn" onClick={() => setQtd((q) => Math.max(0.25, +(q - 0.5).toFixed(3)))}>−0,5</button>
              <button className="btn qtd-btn" onClick={() => setQtd((q) => Math.max(0, +(q - 1).toFixed(3)))}>−1</button>
              <input
                type="number"
                min="0"
                step="0.5"
                value={qtd}
                style={{ width: 80, textAlign: "center", fontSize: 20, fontWeight: 700 }}
                onChange={(e) => setQtd(parseFloat(e.target.value) || 0)}
              />
              <button className="btn qtd-btn" onClick={() => setQtd((q) => +(q + 1).toFixed(3))}>+1</button>
              <button className="btn qtd-btn" onClick={() => setQtd((q) => +(q + 2).toFixed(3))}>+2</button>
            </div>
          )}

          <div className="action-grid">
            <button className="btn big usado" disabled={salvandoTipo !== null} onClick={() => registrar("USADO")}>
              {salvandoTipo === "USADO" ? "Salvando…" : "Usado hoje"}
            </button>
            <button className="btn big aberto" disabled={salvandoTipo !== null} onClick={() => registrar("ABERTO")}>
              {salvandoTipo === "ABERTO" ? "Salvando…" : "Item aberto"}
            </button>
          </div>

          {erro && <div className="form-error">{erro}</div>}
        </div>

        {/* Registros do dia */}
        <div className="card card-pad" style={{ minHeight: 420 }}>
          <h3 style={{ marginTop: 0 }}>Registros do dia</h3>
          {loading && <div className="muted">Carregando…</div>}
          {!loading && soma.length === 0 && <div className="empty">Nenhum registro nesta data.</div>}

          {!loading && soma.length > 0 && (
            <ul className="alert-list">
              {soma.map((s) => (
                <li key={s.produtoId + s.tipo}>
                  <span>
                    <strong>{s.produtoNome}</strong>{" "}
                    <span className={`badge ${s.tipo === "USADO" ? "ok" : "warn"}`}>
                      {s.tipo === "USADO" ? "Usado" : "Aberto"}
                    </span>{" "}
                    <span className="muted small">×{s.qtdItens}</span>
                    <span className="small" style={{ marginLeft: 8 }}>
                      Total: <strong>{formatQtd(s.quantidade)} {s.unidade}</strong>
                    </span>
                  </span>
                </li>
              ))}
            </ul>
          )}

          {!loading && registros && registros.length > 0 && (
            <div className="tbl-detalhe">
              <div className="slow muted small" style={{ margin: "12px 0 6px" }}>Últimos registros</div>
              {(registros || []).slice().reverse().slice(0, 15).map((r) => (
                <div key={r.id} className="reg-linha">
                  <span className="reg-info">
                    <strong>{r.produtoNome}</strong>
                    <span className={`badge ${r.tipo === "USADO" ? "ok" : "warn"}`}>{r.tipo}</span>
                    <span className="small">{formatQtd(r.quantidade)} {r.unidade}</span>
                    <span className="muted small">{r.usuarioNome} · {r.dataHoraRegistro?.slice(11, 19)}</span>
                  </span>
                  {podeExcluir(r) && (
                    <button className="btn small danger" onClick={() => excluir(r.id)}>Excluir</button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}