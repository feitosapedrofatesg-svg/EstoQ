import { useMemo, useState } from "react";
import { api } from "../api";
import { Modal } from "../components";
import { parseCupom, type CupomParseResult, type ItemCupom } from "../cupom";
import { casarProduto } from "../match";
import type { CupomLeituraDTO, Produto } from "../types";

type Modo = "texto" | "xml" | "foto";

export interface LinhaConfirmacao {
  key: string;
  original: string;
  produtoId: string;
  nomeProduto: string;
  novoNome?: string;
  quantidade: string;
  valorUnitario: string;
}

interface Props {
  produtos: Produto[];
  onFechar: () => void;
  onConfirmar: (linhas: LinhaConfirmacao[]) => Promise<{ ok: number; erros: string[] }>;
}

function montarLinhas(res: CupomParseResult, produtos: Produto[]): LinhaConfirmacao[] {
  return (res.itens || []).map((it: ItemCupom, i: number) => {
    const p = casarProduto(it.nome, produtos);
    return {
      key: `${i}-${it.nome}`,
      original: it.nome,
      produtoId: p?.id || "",
      nomeProduto: p?.nome || "",
      novoNome: it.nome,
      quantidade: String(it.quantidade ?? 1),
      valorUnitario: it.valorUnitario != null ? String(it.valorUnitario) : "",
    };
  });
}

function montarLinhasDto(dto: CupomLeituraDTO, produtos: Produto[]): LinhaConfirmacao[] {
  return (dto.itens || []).map((it, i) => {
    const p = casarProduto(it.descricao, produtos);
    return {
      key: `${i}-${it.descricao}`,
      original: it.descricao,
      produtoId: p?.id || "",
      nomeProduto: p?.nome || "",
      novoNome: it.descricao,
      quantidade: String(it.quantidade ?? 1),
      valorUnitario: it.precoUnitario != null ? String(it.precoUnitario) : "",
    };
  });
}

export function CupomScanner({ produtos, onFechar, onConfirmar }: Props) {
  const [modo, setModo] = useState<Modo>("texto");
  const [texto, setTexto] = useState("");
  const [arquivoXml, setArquivoXml] = useState<string | null>(null);
  const [nomeArquivo, setNomeArquivo] = useState("");

  const [linhas, setLinhas] = useState<LinhaConfirmacao[]>([]);
  const [baseInfo, setBaseInfo] = useState<string>("");

  const [status, setStatus] = useState("");
  const [erro, setErro] = useState("");
  const [ocrAtivo, setOcrAtivo] = useState(false);
  const [salvando, setSalvando] = useState(false);

  const temItens = linhas.length > 0;

  function aplicarResultado(res: CupomParseResult, origem: string) {
    if (!res.itens || res.itens.length === 0) {
      setErro("Nenhum item reconhecido. Confira o conteúdo/enquadramento e tente de novo.");
      return;
    }
    const ls = montarLinhas(res, produtos);
    setLinhas(ls);
    const info = [origem, res.numero ? `nº ${res.numero}` : null, res.total != null ? `Total ${res.total.toFixed(2)}` : null]
      .filter(Boolean)
      .join(" · ");
    setBaseInfo(info);
    setErro("");
    setStatus(`Encontrados ${ls.length} itens. Confira e confirme.`);
  }

  function lerTexto() {
    if (!texto.trim()) {
      setErro("Cole o texto do cupom/nota primeiro.");
      return;
    }
    aplicarResultado(parseCupom(texto), "Texto colado");
  }

  function onArquivoXml(file: File | null) {
    if (!file) return;
    setErro("");
    setNomeArquivo(file.name);
    const reader = new FileReader();
    reader.onload = () => {
      const conteudo = String(reader.result || "");
      setArquivoXml(conteudo);
    };
    reader.readAsText(file, "utf-8");
  }

  function lerXml() {
    if (!arquivoXml) {
      setErro("Envie um arquivo XML primeiro.");
      return;
    }
    aplicarResultado(parseCupom(arquivoXml), "Arquivo XML");
  }

  async function lerFoto(file: File | null) {
    if (!file) return;
    setErro("");
    setOcrAtivo(true);
    setStatus("Enviando e processando a imagem… (pode levar alguns segundos)");
    try {
      const dto = await api.upload<CupomLeituraDTO>("/api/cupons/ler", file);
      const ls = montarLinhasDto(dto, produtos);
      if (ls.length === 0) {
        setErro("Não foi possível identificar produtos neste cupom. Tente uma foto mais nítida e com boa iluminação.");
        setOcrAtivo(false);
        return;
      }
      setLinhas(ls);
      const info = [
        dto.fonte === "OCR" ? "Foto (OCR)" : "Foto (QR/NFC-e)",
        dto.estabelecimento || null,
        dto.data || null,
      ]
        .filter(Boolean)
        .join(" · ");
      setBaseInfo(info);
      setErro("");
      setStatus(`Encontrados ${ls.length} itens. Confira e confirme.`);
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao ler a foto.");
    } finally {
      setOcrAtivo(false);
    }
  }

  function atualizarLinha(key: string, patch: Partial<LinhaConfirmacao>) {
    setLinhas((ls) => ls.map((l) => (l.key === key ? { ...l, ...patch } : l)));
  }

  function removerLinha(key: string) {
    setLinhas((ls) => ls.filter((l) => l.key !== key));
  }

  async function confirmar() {
    const validas = linhas.filter((l) => parseFloat(l.quantidade) > 0);
    if (validas.length === 0) {
      setErro("Nenhuma linha válida para registrar (informe uma quantidade > 0).");
      return;
    }
    setSalvando(true);
    setErro("");
    try {
      const r = await onConfirmar(validas);
      setLinhas([]);
      setTexto("");
      setArquivoXml(null);
      setNomeArquivo("");
      setStatus(`Registradas ${r.ok} entradas.` + (r.erros.length ? ` Faltas: ${r.erros.join("; ")}` : ""));
    } catch (e: unknown) {
      setErro((e as Error).message || "Erro ao registrar as entradas.");
    } finally {
      setSalvando(false);
    }
  }

  const produtosOrdenados = useMemo(
    () => [...produtos].sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR")),
    [produtos]
  );

  return (
    <Modal title="Entrada por cupom / nota / foto" onClose={onFechar} wide>
      <div className="tabs" role="tablist">
        <button className={`btn${modo === "texto" ? " primary" : ""}`} onClick={() => setModo("texto")}>Colar texto</button>
        <button className={`btn${modo === "xml" ? " primary" : ""}`} onClick={() => setModo("xml")}>Arquivo XML</button>
        <button className={`btn${modo === "foto" ? " primary" : ""}`} onClick={() => setModo("foto")}>Imagem</button>
      </div>

      {modo === "texto" && (
        <div>
          <p className="muted small">Cole aqui o texto do cupom (as linhas com os itens). O app reconhece <strong>nome + quantidade + preço</strong> automaticamente.</p>
          <textarea
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            rows={8}
            placeholder={"Ex.:\nARROZ TIO JOÃO 5KG   2,000   39,80\nLEITE INTEGRAL        4,000    5,90\nOUTROS ..."}
            style={{ width: "100%", boxSizing: "border-box", padding: 10, fontFamily: "monospace" }}
          />
          <div className="form-actions" style={{ marginTop: 10 }}>
            <button className="btn primary" onClick={lerTexto}>Ler itens do texto</button>
          </div>
        </div>
      )}

      {modo === "xml" && (
        <div>
          <p className="muted small">Envie o arquivo <strong>.xml</strong> da nota fiscal (NFC-e/NF-e) ou do cupom SAT (CF-e).</p>
          <input type="file" accept=".xml,text/xml" onChange={(e) => onArquivoXml(e.target.files?.[0] || null)} />
          {nomeArquivo && <div className="muted small" style={{ marginTop: 6 }}>Arquivo: {nomeArquivo}</div>}
          <div className="form-actions" style={{ marginTop: 10 }}>
            <button className="btn primary" onClick={lerXml}>Ler itens do XML</button>
          </div>
        </div>
      )}

      {modo === "foto" && (
        <div>
          <p className="muted small">Envie uma imagem do cupom/nota (papel ou tela) — o app tenta ler o QR Code da NFC-e e, se não houver, usa reconhecimento de texto (OCR).</p>
          <input
            type="file"
            accept="image/*"
            capture="environment"
            onChange={(e) => lerFoto(e.target.files?.[0] || null)}
          />
          {ocrAtivo && <div className="muted small" style={{ marginTop: 8 }}>{status}</div>}
        </div>
      )}

      {erro && <div className="form-error" style={{ marginTop: 8 }}>{erro}</div>}

      {temItens && (
        <div style={{ marginTop: 16 }}>
          <div className="small muted" style={{ marginBottom: 6 }}>{baseInfo}</div>
          <p className="small muted" style={{ marginTop: 0, marginBottom: 8 }}>
            Ao confirmar, o <strong>valor unitário</strong> de cada item é gravado como <em>custo unitário</em> do
            produto cadastrado (pode ser ajustado depois na tela Produtos).
          </p>
          {linhas.some((l) => l.produtoId === "") && (
            <p className="small" style={{ marginBottom: 8, color: "var(--primary-dark)" }}>
              Linhas com <strong>＋ criar novo produto</strong> serão cadastradas como um novo item no catálogo antes da entrada.
            </p>
          )}
          <div className="table-wrap">
            <table className="tbl">
              <thead>
                <tr>
                  <th style={{ width: 30 }}></th>
                  <th>Item do cupom</th>
                  <th>Produto no catálogo</th>
                  <th className="num" style={{ width: 90 }}>Qtd.</th>
                  <th className="num" style={{ width: 110 }}>Valor unit.</th>
                </tr>
              </thead>
              <tbody>
                {linhas.map((l) => (
                  <tr key={l.key}>
                    <td>
                      <button className="btn small danger" title="Remover" onClick={() => removerLinha(l.key)}>✕</button>
                    </td>
                    <td className="small muted">
                      {l.original}
                      {l.produtoId === "" && (
                        <span className="badge info" style={{ marginLeft: 8 }}>Novo</span>
                      )}
                    </td>
                    <td>
                      <select
                        value={l.produtoId}
                        onChange={(e) => {
                          const p = produtos.find((x) => x.id === e.target.value);
                          atualizarLinha(l.key, { produtoId: e.target.value, nomeProduto: p?.nome || "" });
                        }}
                        style={{ width: 240 }}
                      >
                        <option value="">＋ criar novo produto</option>
                        {produtosOrdenados.map((p) => (
                          <option key={p.id} value={p.id}>{p.nome}</option>
                        ))}
                      </select>
                      {l.produtoId === "" && (
                        <input
                          value={l.novoNome ?? l.original}
                          onChange={(e) => atualizarLinha(l.key, { novoNome: e.target.value })}
                          placeholder="Nome padronizado do novo produto"
                          title="Defina o nome padronizado que será cadastrado no catálogo"
                          style={{ width: 240, marginTop: 6, display: "block", boxSizing: "border-box" }}
                        />
                      )}
                    </td>
                    <td className="num">
                      <input
                        type="number" min="0" step="0.001"
                        value={l.quantidade}
                        onChange={(e) => atualizarLinha(l.key, { quantidade: e.target.value })}
                        style={{ width: 80 }}
                      />
                    </td>
                    <td className="num">
                      <input
                        type="number" min="0" step="0.01"
                        value={l.valorUnitario}
                        onChange={(e) => atualizarLinha(l.key, { valorUnitario: e.target.value })}
                        style={{ width: 100 }}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {status && <div className="qr-resumo" style={{ marginTop: 10 }}>{status}</div>}

          <div className="form-actions">
            <button className="btn" onClick={() => { setLinhas([]); setStatus(""); setErro(""); }}>Limpar lista</button>
            <button
              className="btn primary"
              disabled={salvando || linhas.filter((l) => parseFloat(l.quantidade) > 0).length === 0}
              onClick={confirmar}
            >
              {salvando ? "Registrando…" : `Confirmar e registrar ${linhas.filter((l) => parseFloat(l.quantidade) > 0).length} ${linhas.filter((l) => parseFloat(l.quantidade) > 0).length === 1 ? "entrada" : "entradas"}`}
            </button>
          </div>
        </div>
      )}
    </Modal>
  );
}
