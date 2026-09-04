import { useEffect, useState } from "react";
import { api, download, formatDate, formatDateTime, formatQtd } from "../api";
import { Badge, Modal, useAsyncData } from "../components";
import type { BalancoView, ConfiguracaoBalancoView, ItemBalancoView } from "../types";

const periodicidades = [
  { valor: "DIARIA", label: "Diária" },
  { valor: "SEMANAL", label: "Semanal" },
  { valor: "MENSAL", label: "Mensal" },
];

function BalancoModal({
  balancoId,
  onClose,
  onConcluido,
}: {
  balancoId: string;
  onClose: () => void;
  onConcluido: () => void;
}) {
  const [balanco, setBalanco] = useState<BalancoView | null>(null);
  const [salvos, setSalvos] = useState<Record<string, number>>({});
  const [erro, setErro] = useState("");
  const [carregando, setCarregando] = useState(true);
  const [salvando, setSalvando] = useState(false);

  const carregar = async () => {
    setCarregando(true);
    try {
      const b = await api.get<BalancoView>(`/api/conferencia/balancos/${balancoId}`);
      setBalanco(b);
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao carregar balanço.");
    } finally {
      setCarregando(false);
    }
  };

  useEffect(() => {
    carregar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [balancoId]);

  const confirmado = balanco?.status === "CONCLUIDO";
  const cancelado = balanco?.status === "CANCELADO";

  function setItemValor(itemId: string, valor: string) {
    setBalanco((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        itens: prev.itens.map((i) =>
          i.id === itemId ? { ...i, quantidadeFisica: parseFloat(valor.replace(",", ".")) || 0 } : i
        ),
      };
    });
  }

  async function salvarItem(item: ItemBalancoView) {
    setErro("");
    try {
      const atualizado = await api.put<ItemBalancoView>(
        `/api/conferencia/balancos/${balancoId}/itens/${item.id}`,
        { quantidadeFisica: item.quantidadeFisica }
      );
      setSalvos((s) => ({ ...s, [item.id]: atualizado.quantidadeFisica }));
      setBalanco((prev) =>
        prev
          ? { ...prev, itens: prev.itens.map((i) => (i.id === atualizado.id ? atualizado : i)) }
          : prev
      );
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao salvar item.");
    }
  }

  async function apurar() {
    setErro("");
    try {
      const b = await api.post<BalancoView>(`/api/conferencia/balancos/${balancoId}/apurar`);
      setBalanco(b);
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao apurar balanço.");
    }
  }

  async function confirmar() {
    if (!window.confirm("Confirmar este balanço? Ajustes de estoque serão gerados para as diferenças.")) return;
    setErro("");
    setSalvando(true);
    try {
      const b = await api.post<BalancoView>(`/api/conferencia/balancos/${balancoId}/confirmar`);
      setBalanco(b);
      onConcluido();
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao confirmar balanço.");
    } finally {
      setSalvando(false);
    }
  }

  async function cancelar() {
    if (!window.confirm("Cancelar este balanço? Ele ficará marcado como cancelado.")) return;
    setErro("");
    setSalvando(true);
    try {
      const b = await api.post<BalancoView>(`/api/conferencia/balancos/${balancoId}/cancelar`);
      setBalanco(b);
      onConcluido();
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao cancelar balanço.");
    } finally {
      setSalvando(false);
    }
  }

  async function reabrir() {
    if (!window.confirm("Reabrir este balanço cancelado? Ele voltará para em andamento.")) return;
    setErro("");
    setSalvando(true);
    try {
      const b = await api.post<BalancoView>(`/api/conferencia/balancos/${balancoId}/reabrir`);
      setBalanco(b);
      onConcluido();
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao reabrir balanço.");
    } finally {
      setSalvando(false);
    }
  }

  const bloqueado = confirmado || cancelado;

  function imprimir() {
    if (!balanco) return;
    const linhas = balanco.itens
      .map(
        (i) =>
          `<tr><td>${escHtml(i.produtoNome)} <span class="um">(${escHtml(i.unidadeMedida || "")})</span></td>` +
          `<td class="num">${formatQtd(i.quantidadeSistema)}</td>` +
          `<td class="num">${formatQtd(i.quantidadeFisica)}</td>` +
          `<td class="num">${formatQtd(i.diferenca)}</td></tr>`
      )
      .join("");
    const win = window.open("", "_blank", "width=900,height=700");
    if (!win) return;
    win.document.write(
      `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><title>Balanço de estoque</title>` +
        `<style>
          * { box-sizing: border-box; }
          body { font: 13px/1.45 Arial, sans-serif; color: #111; padding: 28px; }
          h1 { font-size: 18px; margin: 0 0 4px; }
          .meta { color: #555; margin: 0 0 16px; }
          table { width: 100%%; border-collapse: collapse; page-break-inside: auto; }
          th, td { border: 1px solid #999; padding: 6px 8px; text-align: left; }
          th { background: #eee; }
          .num { text-align: right; white-space: nowrap; }
          tfoot td { font-weight: bold; background: #f6f6f6; }
          .um { color: #777; font-size: 11px; }
          @media print { body { padding: 0; } }
        </style></head><body>
        <h1>Balanço de estoque</h1>
        <p class="meta">${escHtml(formatDateTime(balanco.dataHora))} · ${escHtml(balanco.tipo || "")} · responsável: ${escHtml(balanco.responsavelNome || "—")} · ${escHtml(balanco.status || "")}</p>
        <table><thead><tr><th>Produto</th><th class="num">Qtd. sistema</th><th class="num">Qtd. física</th><th class="num">Diferença</th></tr></thead>
        <tbody>${linhas}</tbody>
        <tfoot><tr><td>Total de diferenças</td><td></td><td></td><td class="num">${formatQtd(balanco.totalDiferenca)}</td></tr></tfoot></table>
        <script>window.onload = function(){ setTimeout(function(){ window.print(); }, 150); };<\/script>
      </body></html>`
    );
    win.document.close();
  }

  function escHtml(v: string): string {
    return v.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }

  return (
    <Modal title="Balanço" onClose={onClose} wide>
      {carregando ? (
        <div className="muted">Carregando…</div>
      ) : !balanco ? (
        <div className="empty">{erro || "Balanço não encontrado."}</div>
      ) : (
        <>
          <p className="small muted">
            {formatDateTime(balanco.dataHora)} · {balanco.tipo} · responsável: {balanco.responsavelNome || "—"}{" "}
            · <Badge status={balanco.status} />
          </p>
          <div className="table-wrap" style={{ marginBottom: 12 }}>
            <table className="tbl">
              <thead>
                <tr>
                  <th>Produto</th>
                  <th className="num">Qtd. sistema</th>
                  <th className="num">Qtd. física</th>
                  <th className="num">Diferença</th>
                  <th style={{ width: 70 }}></th>
                </tr>
              </thead>
              <tbody>
                {balanco.itens.map((i) => (
                  <tr key={i.id}>
                    <td><strong>{i.produtoNome}</strong> <span className="muted small">({i.unidadeMedida})</span></td>
                    <td className="num">{formatQtd(i.quantidadeSistema)}</td>
                    <td className="num">
                      <input
                        type="number"
                        min="0"
                        step="1"
                        value={i.quantidadeFisica || 0}
                        disabled={bloqueado}
                        style={{ width: 90, textAlign: "right" }}
                        onChange={(e) => setItemValor(i.id, e.target.value)}
                      />
                    </td>
                    <td className="num">
                      {formatQtd(i.diferenca)}
                    </td>
                    <td>
                      {(() => {
                        const salvo = salvos[i.id] === i.quantidadeFisica;
                        return (
                          <button
                            type="button"
                            className={`btn check-btn${salvo ? " saved" : ""}`}
disabled={bloqueado || salvo}
                        onClick={() => salvarItem(i)}
                            aria-label={salvo ? "Diferença salva" : "Salvar diferença"}
                            title={salvo ? "Salvo" : "Salvar"}
                          >
                            <span className="check-ico" aria-hidden="true">✓</span>
                          </button>
                        );
                      })()}
                    </td>
                  </tr>
                ))}
                {balanco.itens.length === 0 && (
                  <tr><td colSpan={5} className="empty">Balanço sem itens (nenhum produto com saldo).</td></tr>
                )}
              </tbody>
              <tfoot>
                <tr>
                  <td>Total de diferenças</td>
                  <td colSpan={2}></td>
                  <td className="num"><strong>{formatQtd(balanco.totalDiferenca)}</strong></td>
                  <td></td>
                </tr>
              </tfoot>
            </table>
          </div>
          {erro && <div className="form-error">{erro}</div>}
          <div className="form-actions">
            <button className="btn" onClick={() => download(`/api/conferencia/balancos/${balancoId}/exportar`, "balanco.csv")}>
              Exportar CSV
            </button>
            <button className="btn" onClick={imprimir}>Imprimir</button>
            <span style={{ flex: 1 }} />
            <button className="btn" onClick={onClose}>Fechar</button>
            {cancelado && (
              <button className="btn primary" disabled={salvando} onClick={reabrir}>Reabrir</button>
            )}
            {!bloqueado && (
              <>
                <button className="btn danger" disabled={salvando} onClick={cancelar}>Cancelar balanço</button>
                <button className="btn" disabled={salvando} onClick={apurar}>Apurar</button>
                <button className="btn primary" disabled={salvando} onClick={confirmar}>
                  {salvando ? "Confirmando…" : "Confirmar"}
                </button>
              </>
            )}
          </div>
        </>
      )}
    </Modal>
  );
}

export default function Conferencia() {
  const { data: configData, reload: reloadConfig } = useAsyncData<ConfiguracaoBalancoView | null>(
    () => api.get("/api/conferencia/configuracao"),
    []
  );
  const { data: balancos, loading: loadingBalancos, reload: reloadBalancos } = useAsyncData<BalancoView[]>(
    () => api.get("/api/conferencia/balancos"),
    []
  );

  const [periodicidade, setPeriodicidade] = useState("SEMANAL");
  const [diaExecucao, setDiaExecucao] = useState("1");
  const [salvandoCfg, setSalvandoCfg] = useState(false);
  const [erroCfg, setErroCfg] = useState("");
  const [iniciando, setIniciando] = useState(false);
  const [balancoModal, setBalancoModal] = useState<string | null>(null);

  useEffect(() => {
    if (configData) {
      setPeriodicidade(configData.periodicidade || "SEMANAL");
      setDiaExecucao(String(configData.diaExecucao ?? 1));
    }
  }, [configData]);

  async function salvarConfiguracao() {
    setSalvandoCfg(true);
    setErroCfg("");
    try {
      await api.put("/api/conferencia/configuracao", {
        periodicidade,
        diaExecucao: parseInt(diaExecucao, 10),
      });
      reloadConfig();
    } catch (e: unknown) {
      setErroCfg((e as Error).message || "Erro ao salvar configuração.");
    } finally {
      setSalvandoCfg(false);
    }
  }

  async function reabrirBalancos(b: BalancoView) {
    if (!window.confirm(`Reabrir o balanço cancelado de ${formatDateTime(b.dataHora)}?`)) return;
    try {
      await api.post(`/api/conferencia/balancos/${b.id}/reabrir`);
      reloadBalancos();
      reloadConfig();
    } catch (e: unknown) {
      setErroCfg((e as Error).message || "Erro ao reabrir balanço.");
    }
  }

  async function excluirBalancos(b: BalancoView) {
    if (!window.confirm(`Excluir definitivamente o balanço cancelado de ${formatDateTime(b.dataHora)}? Esta ação não pode ser desfeita.`)) return;
    try {
      await api.del(`/api/conferencia/balancos/${b.id}`);
      reloadBalancos();
      reloadConfig();
    } catch (e: unknown) {
      setErroCfg((e as Error).message || "Erro ao excluir balanço.");
    }
  }

  async function cancelarBalancos(b: BalancoView) {
    if (!window.confirm(`Cancelar o balanço de ${formatDateTime(b.dataHora)}?`)) return;
    setErroCfg("");
    try {
      await api.post(`/api/conferencia/balancos/${b.id}/cancelar`);
      reloadBalancos();
      reloadConfig();
    } catch (e: unknown) {
      setErroCfg((e as Error).message || "Erro ao cancelar balanço.");
    }
  }

  async function iniciarBalanco() {
    setIniciando(true);
    setErroCfg("");
    try {
      const b = await api.post<BalancoView>("/api/conferencia/balancos/iniciar");
      reloadBalancos();
      reloadConfig();
      setBalancoModal(b.id);
    } catch (e: unknown) {
      setErroCfg((e as Error).message || "Erro ao iniciar balanço.");
    } finally {
      setIniciando(false);
    }
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Conferência de estoque</h1>
          <p>Agende e execute balanços físicos</p>
        </div>
        <div className="page-actions">
          <button className="btn primary" disabled={iniciando} onClick={iniciarBalanco}>
            {iniciando ? "Iniciando…" : "Iniciar novo balanço"}
          </button>
        </div>
      </div>

      <div className="card card-pad" style={{ marginBottom: 20 }}>
        <h3 style={{ marginTop: 0 }}>Configuração</h3>
        <div className="form-row three">
          <div className="field">
            <label>Periodicidade</label>
            <select value={periodicidade} onChange={(e) => setPeriodicidade(e.target.value)}>
              {periodicidades.map((p) => (
                <option key={p.valor} value={p.valor}>{p.label}</option>
              ))}
            </select>
          </div>
          <div className="field">
            <label>Dia de execução</label>
            {periodicidade === "MENSAL" && (
              <div className="day-grid">
                {Array.from({ length: 31 }, (_, i) => i + 1).map((d) => (
                  <button
                    key={d}
                    type="button"
                    className={`day-cell${Number(diaExecucao) === d ? " sel" : ""}`}
                    onClick={() => setDiaExecucao(String(d))}
                  >
                    {d}
                  </button>
                ))}
              </div>
            )}
            {periodicidade === "SEMANAL" && (
              <div className="weekday-row">
                {[
                  { val: "1", lbl: "Seg" },
                  { val: "2", lbl: "Ter" },
                  { val: "3", lbl: "Qua" },
                  { val: "4", lbl: "Qui" },
                  { val: "5", lbl: "Sex" },
                  { val: "6", lbl: "Sáb" },
                  { val: "7", lbl: "Dom" },
                ].map(({ val, lbl }) => (
                  <button
                    key={val}
                    type="button"
                    className={`day-cell${diaExecucao === val ? " sel" : ""}`}
                    onClick={() => setDiaExecucao(val)}
                  >
                    {lbl}
                  </button>
                ))}
              </div>
            )}
            {periodicidade === "DIARIA" && (
              <p className="small muted" style={{ margin: "6px 0 0" }}>
                Execução diária — todos os dias
              </p>
            )}
          </div>
          <div className="field" style={{ justifyContent: "flex-end" }}>
            <button className="btn primary" disabled={salvandoCfg} onClick={salvarConfiguracao}>
              {salvandoCfg ? "Salvando…" : "Salvar configuração"}
            </button>
          </div>
        </div>
        <p className="small muted" style={{ margin: 0 }}>
          Próxima execução: {formatDate(configData?.proximaExecucao)}{" "}
          {configData?.pendente && <Badge status="PENDENTE" />}
        </p>
        {erroCfg && <div className="form-error">{erroCfg}</div>}
      </div>

      <h3 style={{ margin: "0 0 10px" }}>Balanços realizados</h3>
      {loadingBalancos ? (
        <div className="muted">Carregando…</div>
      ) : !balancos || balancos.length === 0 ? (
        <div className="empty">Nenhum balanço ainda.</div>
      ) : (
        <div className="table-wrap">
          <table className="tbl">
            <thead>
              <tr>
                <th>Data/hora</th>
                <th>Tipo</th>
                <th>Status</th>
                <th>Responsável</th>
                <th className="num">Itens</th>
                <th className="num">Diferença total</th>
                <th style={{ width: 260 }}>Ações</th>
              </tr>
            </thead>
            <tbody>
              {balancos.map((b) => (
                <tr key={b.id}>
                  <td>{formatDateTime(b.dataHora)}</td>
                  <td>{b.tipo}</td>
                  <td><Badge status={b.status} /></td>
                  <td>{b.responsavelNome || "—"}</td>
                  <td className="num">{b.qtdItens}</td>
                  <td className="num">{formatQtd(b.totalDiferenca)}</td>
                  <td>
                    <span className="row-actions">
                      <button className="btn small" onClick={() => setBalancoModal(b.id)}>
                        {b.status === "CONCLUIDO" || b.status === "CANCELADO" ? "Ver" : "Continuar"}
                      </button>
                      {b.status === "CANCELADO" && (
                        <>
                          <button className="btn small primary" onClick={() => reabrirBalancos(b)}>
                            Reabrir
                          </button>
                          <button className="btn small danger" onClick={() => excluirBalancos(b)}>
                            Excluir
                          </button>
                        </>
                      )}
                      {b.status !== "CONCLUIDO" && b.status !== "CANCELADO" && (
                        <button className="btn small danger" onClick={() => cancelarBalancos(b)}>
                          Cancelar
                        </button>
                      )}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {balancoModal && (
        <BalancoModal
          balancoId={balancoModal}
          onClose={() => setBalancoModal(null)}
          onConcluido={() => reloadBalancos()}
        />
      )}
    </div>
  );
}