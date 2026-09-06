import { useRef, useState } from "react";
import { api, carregarProdutos, download, formatDateTime, formatQtd, hojeIso, inicioMesIso } from "../api";
import { useAuth } from "../auth";
import { Badge, Modal, Notice, useAsyncData } from "../components";
import { ProductPicker, QuantityInput, useConfirm, useToast, TableSkeleton } from "../ux";
import { CupomScanner, type LinhaConfirmacao } from "../components/CupomScanner";
import { derivarUnidade } from "../cupom";
import type { MovimentacaoView, Produto } from "../types";

const tipoLabel: Record<string, string> = {
  ENTRADA: "Entrada",
  AJUSTE: "Ajuste",
};

export default function Entradas() {
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

  // entrada
  const [produtoEntrada, setProdutoEntrada] = useState("");
  const [qtdEntrada, setQtdEntrada] = useState("1");
  const [valorTotalPago, setValorTotalPago] = useState("");
  const [unidadeCompra, setUnidadeCompra] = useState("");
  const [fatorConversao, setFatorConversao] = useState("");
  const [dataValidade, setDataValidade] = useState("");
  const [obsEntrada, setObsEntrada] = useState("");
  const { data: unidades } = useAsyncData<string[]>(() => api.get("/api/produtos/opcoes-unidade"), [refresh]);

  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState("");
  const [aviso, setAviso] = useState<{ tipo: "ok" | "danger"; msg: string } | null>(null);
  const [qrAberto, setQrAberto] = useState(false);

  async function registrarEntrada() {
    setSalvando(true);
    setErro("");
    setAviso(null);
    const semValor = !valorTotalPago.trim();
    if (semValor) {
      const ok = await confirmar({
        titulo: "Entrada sem valor",
        texto: "Esta entrada ficará sem valor total pago, o que subestima o custo no CMV. Concluir mesmo assim?",
        confirmarLabel: "Lançar sem valor",
        perigo: true,
      });
      if (!ok) {
        setSalvando(false);
        return;
      }
    }
    try {
      const nome = (produtos || []).find((p) => p.id === produtoEntrada)?.nome || "";
      await api.post("/api/movimentacoes/entrada", {
        produtoId: produtoEntrada,
        quantidade: parseFloat(qtdEntrada.replace(",", ".")),
        valorTotalPago: semValor ? null : parseFloat(valorTotalPago.replace(",", ".")),
        unidadeCompra: unidadeCompra || null,
        fatorConversao: fatorConversao ? parseFloat(fatorConversao.replace(",", ".")) : null,
        dataValidade: dataValidade || null,
        observacao: obsEntrada || null,
      });
      setProdutoEntrada("");
      setQtdEntrada("1");
      setValorTotalPago("");
      setUnidadeCompra("");
      setFatorConversao("");
      setDataValidade("");
      setObsEntrada("");
      setRefresh((k) => k + 1);
      toast(`Entrada registrada: ${nome || "produto"} (${formatQtd(parseFloat(qtdEntrada.replace(",", ".")))} un)`);
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao registrar entrada.");
    } finally {
      setSalvando(false);
    }
  }

  async function reverter(m: MovimentacaoView) {
    const ok = await confirmar({
      titulo: `Reverter ${tipoLabel[m.tipo]}`,
      texto: <>Reverter <strong>{m.produtoNome}</strong> ({formatQtd(m.quantidade)})? O lote voltará ao estoque.</>,
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

  async function confirmarCupom(linhas: LinhaConfirmacao[]) {
    const ok: LinhaConfirmacao[] = [];
    const erros: string[] = [];
    for (const l of linhas) {
      try {
        const qtd = parseFloat(l.quantidade.replace(",", "."));
        const unit = l.valorUnitario.trim() ? parseFloat(l.valorUnitario.replace(",", ".")) : null;
        let produtoId = l.produtoId;
        if (!produtoId) {
          const nomeNovo = (l.novoNome || l.original).trim();
          const novo = await api.post<{ id: string }>("/api/integracao/criar-produto-cupom", {
            nome: nomeNovo,
            unidadeMedida: derivarUnidade(nomeNovo || l.original),
          });
          produtoId = novo.id;
        }
        await api.post("/api/movimentacoes/entrada", {
          produtoId,
          quantidade: qtd,
          valorTotalPago: unit != null && unit > 0 ? +(unit * qtd).toFixed(2) : null,
          observacao: `Entrada via cupom/nota — ${l.original}`,
        });
        if (unit != null && unit > 0) {
          try {
            await api.patch(`/api/produtos/${produtoId}/preco`, { precoUnitario: unit });
          } catch {
            // não bloqueia a entrada; o valor pode ser ajustado depois na tela de Produtos
          }
        }
        ok.push(l);
      } catch (e: unknown) {
        erros.push(`"${l.nomeProduto || l.original}": ${(e as Error).message}`);
      }
    }
    setRefresh((k) => k + 1);
    return { ok: ok.length, erros };
  }

  const lista = (movs || []).filter((m) => m.tipo === "ENTRADA" || m.tipo === "AJUSTE");
  const periodoInvalido = inicio > fim;

  const [importAberto, setImportAberto] = useState(false);
  const [importando, setImportando] = useState(false);
  const [resultado, setResultado] = useState<{ importadas: number; ignoradas: number; erros: string[] } | null>(null);
  const [arquivoImport, setArquivoImport] = useState<File | null>(null);
  const fileImportRef = useRef<HTMLInputElement>(null);

  async function importarPlanilha() {
    if (!arquivoImport) {
      setErro("Escolha o arquivo .csv da planilha primeiro.");
      return;
    }
    setImportando(true);
    setErro("");
    setAviso(null);
    try {
      const r = await api.upload<{ importadas: number; ignoradas: number; erros: string[] }>(
        "/api/integracao/importar-produtos",
        arquivoImport
      );
      setResultado(r);
      setArquivoImport(null);
      if (fileImportRef.current) fileImportRef.current.value = "";
      setRefresh((k) => k + 1);
      toast(`Planilha importada: ${r.importadas} entrada(s) registrada(s).`);
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao importar a planilha.");
    } finally {
      setImportando(false);
    }
  }

  async function baixarModeloPlanilha() {
    try {
      await download("/api/integracao/modelo-entrada", "modelo-entrada-estoq.xlsx");
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao baixar o modelo.");
    }
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Entrada de itens</h1>
          <p>Registre entradas e ajustes de estoque</p>
        </div>
      </div>

      {aviso && <Notice tipo={aviso.tipo}>{aviso.msg}</Notice>}

      <div className="card card-pad" style={{ marginBottom: 20 }}>
        <h3 style={{ marginTop: 0 }}>Registrar entrada</h3>
        <div className="form-row three">
          <div className="field" style={{ gridColumn: "span 1" }}>
            <label>Produto *</label>
            <ProductPicker
              produtos={produtos || []}
              value={produtoEntrada}
              placeholder="Buscar produto…"
              onChange={setProdutoEntrada}
            />
          </div>
          <div className="field">
            <label>Quantidade *</label>
            <QuantityInput
              value={qtdEntrada}
              onChange={setQtdEntrada}
              min={0}
              step={1}
              unidade={(produtos || []).find((p) => p.id === produtoEntrada)?.unidadeMedida}
            />
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
            <label>Fator de conversão</label>
            <input
              type="number"
              min="0"
              step="0.001"
              value={fatorConversao}
              placeholder="ex.: 12"
              onChange={(e) => setFatorConversao(e.target.value)}
            />
          </div>
          <div className="field">
            <label>Validade</label>
            <input type="date" value={dataValidade} onChange={(e) => setDataValidade(e.target.value)} />
          </div>
        </div>
        <div className="form-row three" style={{ marginTop: 0 }}>
          <div className="field" style={{ gridColumn: "span 3" }}>
            <label>Observação</label>
            <input value={obsEntrada} onChange={(e) => setObsEntrada(e.target.value)} />
          </div>
        </div>
        {unidadeCompra && unidadeCompra !== (produtos || []).find((p) => p.id === produtoEntrada)?.unidadeMedida && (
          <div className="aviso" style={{ marginTop: 8 }}>
            A unidade de compra <strong>{unidadeCompra}</strong> é diferente da unidade do produto. Informe o fator de
            conversão — ex.: 1 caixa = 12 unidades — para a quantidade entrar certa no estoque.
          </div>
        )}
        {fatorConversao && parseFloat(fatorConversao) > 0 && (
          <p className="small muted" style={{ marginTop: 8 }}>
            Estoque do produto: <strong>{formatQtd(+parseFloat(qtdEntrada.replace(",", ".")) * parseFloat(fatorConversao.replace(",", ".")))}</strong>{" "}
            {(produtos || []).find((p) => p.id === produtoEntrada)?.unidadeMedida || "un"}
          </p>
        )}
        {erro && <div className="form-error">{erro}</div>}
        <div className="form-actions" style={{ marginTop: 0 }}>
          <button className="btn" onClick={() => setQrAberto(true)}>
            Entrada por cupom / nota / foto
          </button>
          <button className="btn" onClick={() => setImportAberto(true)}>
            Importar planilha
          </button>
          <button
            className="btn primary"
            disabled={salvando || !produtoEntrada || !qtdEntrada || parseFloat(qtdEntrada) <= 0}
            onClick={registrarEntrada}
          >
            {salvando ? "Salvando…" : "Registrar entrada"}
          </button>
        </div>
      </div>

      <div className="card card-pad">
        <div className="filtro-line">
          <label className="muted small" htmlFor="ent-inicio">Período:</label>
          <input id="ent-inicio" type="date" value={inicio} onChange={(e) => setInicio(e.target.value)} />
          <input type="date" value={fim} onChange={(e) => setFim(e.target.value)} />
        </div>

        {periodoInvalido ? (
          <div className="aviso">
            Período inválido: a data inicial é posterior à data final. Ajuste o intervalo para listar as movimentações.
          </div>
        ) : loadingMovs ? (
          <TableSkeleton linhas={5} colunas={8} />
        ) : lista.length === 0 ? (
          <div className="empty">Nenhuma entrada ou ajuste no período.</div>
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
                  <th>Alteração</th>
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
                      {m.diferencaApurada != null ? formatQtd(m.diferencaApurada) : "—"}
                    </td>
                    <td className="small muted">{m.observacao || ""}</td>
                    {admin && m.tipo === "ENTRADA" && (
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

      {qrAberto && (
        <CupomScanner produtos={produtos || []} onFechar={() => setQrAberto(false)} onConfirmar={confirmarCupom} />
      )}

      {importAberto && (
        <Modal title="Importar entradas de planilha" onClose={() => setImportAberto(false)}>
          <p className="muted small" style={{ marginTop: 0 }}>
            Envie um arquivo <strong>.xlsx</strong> (planilha do Excel) ou <strong>.csv</strong> (separado por ponto e
            vírgula). Baixe o modelo abaixo para começar no formato certo. Produtos que não existem no catálogo são
            criados automaticamente. Colunas aceitas (primeira linha = cabeçalho):
          </p>
          <p style={{ fontFamily: "monospace", fontSize: 12, margin: "4px 0 10px", background: "var(--bg)", padding: 8, borderRadius: 6 }}>
            Produto; Categoria; Unidade; Quantidade; Valor; DataValidade
          </p>
          <p className="small muted">
            Obrigatórias: <strong>Produto</strong>, <strong>Unidade</strong>, <strong>Quantidade</strong>. · Valor em R${" "}
            (com ou sem vírgula) · Data no formato <code>dd/MM/aaaa</code> (opcional). Linhas com quantidade zerada ou
            vazia são ignoradas.
          </p>
          <div className="form-row" style={{ marginTop: 12 }}>
            <div className="field" style={{ gridColumn: "span 2" }}>
              <input
                ref={fileImportRef}
                type="file"
                accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                onChange={(e) => {
                  setArquivoImport(e.target.files?.[0] || null);
                  setResultado(null);
                  setErro("");
                }}
              />
            </div>
            <div className="field">
              <button className="btn" onClick={baixarModeloPlanilha}>Baixar modelo (xlsx)</button>
            </div>
          </div>
          {resultado && (
            <div className="aviso" style={{ marginTop: 10 }}>
              <strong>{resultado.importadas} entradas registradas.</strong>
              {resultado.ignoradas > 0 && <> {resultado.ignoradas} linha(s) ignorada(s).</>}
              {resultado.erros.length > 0 && (
                <ul style={{ margin: "6px 0 0", paddingLeft: 18 }}>
                  {resultado.erros.map((e, i) => (
                    <li key={i}>{e}</li>
                  ))}
                </ul>
              )}
            </div>
          )}
          {erro && <div className="form-error">{erro}</div>}
          <div className="form-actions">
            <button className="btn" onClick={() => setImportAberto(false)}>Fechar</button>
            <button className="btn primary" disabled={importando || !arquivoImport} onClick={importarPlanilha}>
              {importando ? "Importando…" : "Importar planilha"}
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}