import { useRef, useState } from "react";
import { api, formatDate, formatMoney, formatQtd } from "../api";
import type { LeitorReciboResultado, ResultadoImportacao } from "../types";

export default function Importar() {
  const [arquivo, setArquivo] = useState<File | null>(null);
  const [importando, setImportando] = useState(false);
  const [resultado, setResultado] = useState<ResultadoImportacao | null>(null);
  const [erro, setErro] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  const [reciboArquivo, setReciboArquivo] = useState<File | null>(null);
  const [reciboLendo, setReciboLendo] = useState(false);
  const [reciboResultado, setReciboResultado] = useState<LeitorReciboResultado | null>(null);
  const [reciboErro, setReciboErro] = useState("");
  const reciboInputRef = useRef<HTMLInputElement>(null);

  async function importar() {
    if (!arquivo) return;
    setImportando(true);
    setErro("");
    setResultado(null);
    try {
      const r = await api.upload<ResultadoImportacao>("/api/integracao/importar-planilha", arquivo);
      setResultado(r);
    } catch (e: unknown) {
      setErro((e as Error).message || "Falha na importação.");
    } finally {
      setImportando(false);
    }
  }

  async function lerRecibo() {
    if (!reciboArquivo) return;
    setReciboLendo(true);
    setReciboErro("");
    setReciboResultado(null);
    try {
      const r = await api.upload<LeitorReciboResultado>("/api/integracao/importar-recibo", reciboArquivo);
      setReciboResultado(r);
    } catch (e: unknown) {
      setReciboErro((e as Error).message || "Falha ao ler o recibo.");
    } finally {
      setReciboLendo(false);
    }
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Importar dados</h1>
          <p>Importar planilha legada de CMV ou foto de recibo de mercado (cupom fiscal)</p>
        </div>
      </div>

      <div className="card card-pad" style={{ maxWidth: 640 }}>
        <p>
          Envie a planilha no formato do dezembro/2022 (abas <em>CMV SEMANA 01..04</em> e{" "}
          <em>CONSUMO</em>). O sistema importará:
        </p>
        <ul className="small" style={{ lineHeight: 1.9 }}>
          <li>Períodos (semanas) com datas e vendas;</li>
          <li>Compras por período (produto, unidade, quantidade, preço);</li>
          <li>Estoques iniciais e finais por período;</li>
          <li>Produtos novos no catálogo (com unidade e categoria) — os já existentes são atualizados;</li>
          <li>Estoque mínimo semanal da aba <em>CONSUMO</em>.</li>
        </ul>

        <div className="field" style={{ marginTop: 16 }}>
          <label>Arquivo da planilha</label>
          <input
            ref={inputRef}
            type="file"
            accept=".xls,.xlsx"
            onChange={(e) => setArquivo(e.target.files?.[0] || null)}
          />
        </div>

        {erro && <div className="form-error">{erro}</div>}

        <div className="form-actions">
          <button
            className="btn primary"
            disabled={!arquivo || importando}
            onClick={importar}
          >
            {importando ? "Importando…" : "Importar planilha"}
          </button>
        </div>

        {resultado && (
          <div className="card" style={{ marginTop: 16, background: "var(--ok-soft)", borderColor: "#a7f3d0" }}>
            <div style={{ padding: 14 }}>
              <strong>Importação concluída!</strong> {resultado.mensagem}
              <ul style={{ margin: "10px 0 0", paddingLeft: 20 }}>
                <li>Períodos: {resultado.periodos}</li>
                <li>Compras: {resultado.compras}</li>
                <li>Estoques: {resultado.estoques}</li>
                <li>Produtos criados: {resultado.produtosCriados}</li>
                <li>Produtos atualizados: {resultado.produtosAtualizados}</li>
              </ul>
            </div>
          </div>
        )}
      </div>

      <div className="card card-pad" style={{ maxWidth: 640, marginTop: 18 }}>
        <h3 style={{ marginTop: 0 }}>Importar recibo (foto do cupom)</h3>
        <p>
          Tire uma foto do cupom fiscal / NFC-e / SAT de mercado. O sistema lê a imagem,
          reconhece os itens e grava as compras no período corrente (produtos novos são criados
          automaticamente com categoria <em>Recibo</em>).
        </p>

        <div className="field" style={{ marginTop: 16 }}>
          <label>Foto do recibo</label>
          <input
            ref={reciboInputRef}
            type="file"
            accept="image/*"
            capture="environment"
            onChange={(e) => setReciboArquivo(e.target.files?.[0] || null)}
          />
          <span className="muted small">JPG ou PNG. Em celular, abre a câmera ao selecionar.</span>
        </div>

        {reciboErro && <div className="form-error">{reciboErro}</div>}

        <div className="form-actions">
          <button
            className="btn primary"
            disabled={!reciboArquivo || reciboLendo}
            onClick={lerRecibo}
          >
            {reciboLendo ? "Lendo recibo…" : "Importar recibo"}
          </button>
        </div>

        {reciboResultado && (
          <div style={{ marginTop: 16 }}>
            <div
              className="card"
              style={{
                background: reciboResultado.compras > 0 ? "var(--ok-soft)" : "var(--warn-soft)",
                borderColor: reciboResultado.compras > 0 ? "#a7f3d0" : "#fcd34d",
              }}
            >
              <div style={{ padding: 14 }}>
                <strong>{reciboResultado.compras > 0 ? "Recibo importado!" : "Recibo processado"}</strong>{" "}
                {reciboResultado.mensagem}
                <ul style={{ margin: "10px 0 0", paddingLeft: 20 }}>
                  {reciboResultado.dataCompra && <li>Data da compra: {formatDate(reciboResultado.dataCompra)}</li>}
                  <li>Itens reconhecidos: {reciboResultado.totalItens}</li>
                  <li>Compras gravadas: {reciboResultado.compras}</li>
                  <li>Produtos criados: {reciboResultado.produtosCriados}</li>
                </ul>
              </div>
            </div>

            {reciboResultado.itens && reciboResultado.itens.length > 0 && (
              <div className="table-wrap" style={{ marginTop: 12 }}>
                <table className="tbl">
                  <thead>
                    <tr>
                      <th>Produto</th>
                      <th className="num">Qtd.</th>
                      <th className="num">Preço unit.</th>
                    </tr>
                  </thead>
                  <tbody>
                    {reciboResultado.itens.map((i, idx) => (
                      <tr key={idx}>
                        <td>{i.descricao}</td>
                        <td className="num">{formatQtd(i.quantidade)}</td>
                        <td className="num">{formatMoney(i.precoUnitario)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}