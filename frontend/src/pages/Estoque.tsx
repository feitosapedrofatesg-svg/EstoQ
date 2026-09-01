import { useMemo, useState } from "react";
import { api, formatQtd } from "../api";
import { useAsyncData } from "../components";
import type { EstoqueView, Periodo } from "../types";

export default function Estoque() {
  const { data: periodos } = useAsyncData<Periodo[]>(() => api.get("/api/periodos/listar"), []);
  const [selPeriodo, setSelPeriodo] = useState("");
  const periodoAtivo = selPeriodo || periodos?.[0]?.id || "";

  const { data: estoque, error, loading, reload } = useAsyncData<EstoqueView[]>(
    () => (periodoAtivo ? api.get(`/api/estoque/periodo/${periodoAtivo}`) : Promise.resolve([])),
    [periodoAtivo]
  );

  const [edicao, setEdicao] = useState<Record<string, { ini: string; fin: string }>>({});
  const [salvos, setSalvos] = useState<Record<string, boolean>>({});
  const [busca, setBusca] = useState("");
  const [salvando, setSalvando] = useState(false);

  const filtrados = useMemo(() => {
    let lista = estoque || [];
    if (busca) lista = lista.filter((e) => e.produtoNome.toLowerCase().includes(busca.toLowerCase()));
    return lista.sort((a, b) => a.produtoNome.localeCompare(b.produtoNome, "pt-BR"));
  }, [estoque, busca]);

  const periodoFechado = periodos?.find((p) => p.id === periodoAtivo)?.status === "FECHADO";

  async function preparar() {
    if (!periodoAtivo) return;
    if (!window.confirm("Preparar estoque inicial a partir do período anterior para todos os produtos?")) return;
    const r = await api.post<{ criados: number }>(`/api/estoque/preparar/${periodoAtivo}`);
    window.alert(`${r.criados} lançamentos preparados.`);
    reload();
  }

  function setValor(id: string, chave: "ini" | "fin", valor: string) {
    setEdicao((prev) => ({ ...prev, [id]: { ...prev[id], [chave]: valor } }));
    setSalvos((prev) => ({ ...prev, [id]: false }));
  }

  async function salvarLinha(e: EstoqueView) {
    const dados = edicao[e.id];
    if (!dados) return;
    setSalvando(true);
    try {
      await api.put(`/api/estoque/${e.id}`, {
        periodoId: e.periodoId,
        produtoId: e.produtoId,
        quantidadeInicial: parseFloat(dados.ini.replace(",", ".")),
        quantidadeFinal: parseFloat(dados.fin.replace(",", ".")),
      });
      setSalvos((prev) => ({ ...prev, [e.id]: true }));
      reload();
    } catch (er: unknown) {
      window.alert((er as Error).message || "Erro ao salvar.");
    } finally {
      setSalvando(false);
    }
  }

  async function salvarTudo() {
    const pendentes = (estoque || []).filter((e) => edicao[e.id]);
    if (pendentes.length === 0) return;
    setSalvando(true);
    try {
      for (const e of pendentes) {
        const dados = edicao[e.id];
        await api.put(`/api/estoque/${e.id}`, {
          periodoId: e.periodoId,
          produtoId: e.produtoId,
          quantidadeInicial: parseFloat(dados.ini.replace(",", ".")),
          quantidadeFinal: parseFloat(dados.fin.replace(",", ".")),
        });
      }
      setEdicao({});
      window.alert("Estoque salvo com sucesso.");
      reload();
    } catch (er: unknown) {
      window.alert((er as Error).message || "Erro ao salvar.");
    } finally {
      setSalvando(false);
    }
  }

  const pendentes = (estoque || []).filter((e) => edicao[e.id]).length;

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Estoque</h1>
          <p>Estoque inicial e final por produto no período</p>
        </div>
        <div className="page-actions">
          <input className="search" placeholder="Buscar produto…" value={busca} onChange={(e) => setBusca(e.target.value)} />
          <select value={periodoAtivo} onChange={(e) => setSelPeriodo(e.target.value)}>
            {(periodos || []).map((p) => (
              <option key={p.id} value={p.id}>{p.nome}</option>
            ))}
          </select>
          {!periodoFechado && (
            <>
              <button className="btn" onClick={preparar}>Preparar estoque inicial</button>
              <button className="btn primary" disabled={salvando || pendentes === 0} onClick={salvarTudo}>
                Salvar alterações ({pendentes})
              </button>
            </>
          )}
        </div>
      </div>

      {loading && <div className="muted">Carregando…</div>}
      {error && <div className="empty">Erro: {error} — <button className="btn small" onClick={reload}>recarregar</button></div>}
      {!loading && !error && (
        <div className="table-wrap">
          <table className="tbl">
            <thead>
              <tr>
                <th>Produto</th>
                <th className="num">Estoque inicial</th>
                <th className="num">Estoque final</th>
                <th className="num">Consumo sugerido</th>
                <th style={{ width: 160 }}></th>
              </tr>
            </thead>
            <tbody>
              {filtrados.map((e) => {
                const dados = edicao[e.id];
                const ini = dados ? parseFloat(dados.ini.replace(",", ".")) : e.quantidadeInicial;
                const fin = dados ? parseFloat(dados.fin.replace(",", ".")) : e.quantidadeFinal;
                const consumo = (isNaN(ini) ? 0 : ini) - (isNaN(fin) ? 0 : fin);
                return (
                  <tr key={e.id}>
                    <td><strong>{e.produtoNome}</strong> <span className="muted small">({e.unidade})</span></td>
                    <td className="num">
                      <input
                        disabled={periodoFechado}
                        type="number"
                        min="0"
                        step="0.001"
                        style={{ width: 100, textAlign: "right" }}
                        value={dados ? dados.ini : e.quantidadeInicial}
                        onChange={(ev) => setValor(e.id, "ini", ev.target.value)}
                      />
                    </td>
                    <td className="num">
                      <input
                        disabled={periodoFechado}
                        type="number"
                        min="0"
                        step="0.001"
                        style={{ width: 100, textAlign: "right" }}
                        value={dados ? dados.fin : e.quantidadeFinal}
                        onChange={(ev) => setValor(e.id, "fin", ev.target.value)}
                      />
                    </td>
                    <td className="num">{formatQtd(consumo)}</td>
                    <td>
                      {salvos[e.id] && <span className="badge ok">salvo</span>}{" "}
                      {!periodoFechado && dados && (
                        <button className="btn small" disabled={salvando} onClick={() => salvarLinha(e)}>Salvar</button>
                      )}
                    </td>
                  </tr>
                );
              })}
              {filtrados.length === 0 && (
                <tr>
                  <td colSpan={5} className="empty">
                    Nenhum lançamento de estoque.{" "}
                    <button className="btn small" onClick={preparar}>Preparar automaticamente</button>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}