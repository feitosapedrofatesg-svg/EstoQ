export interface ItemCupom {
  nome: string;
  quantidade: number;
  valorUnitario: number | null;
  valorTotal: number | null;
  unidade: string | null;
}

export interface CupomParseResult {
  itens: ItemCupom[];
  total: number | null;
  numero: string | null;
  data: string | null;
  raw: string | null;
}

// ---------------------------------------------------------------------------
// Números
// ---------------------------------------------------------------------------

function extrairDecimal(texto: string): number | null {
  // casa decimal com vírgula (padrão BR): 1,99 | 0,300 | 1.234,56
  const m = texto.replace(/\s/g, "").match(/(\d{1,3}(?:\.\d{3})*(?:,\d{1,3})|\d+,\d+|\d+)/);
  if (!m) return null;
  const limpo = m[1].replace(/\./g, "").replace(",", ".");
  const n = parseFloat(limpo);
  return Number.isFinite(n) ? n : null;
}

// ---------------------------------------------------------------------------
// Parsing de texto de cupom (linhas de itens)
// ---------------------------------------------------------------------------

/**
 * Tenta identificar uma linha de item: nome + quantidade + valor unitário.
 * Formatos comuns:
 *   ARROZ 5KG      2,000        39,80
 *   2,000 X ARROZ  39,80
 *   COD 123 ARROZ ...  2      39,80
 */
function parseLinhaItem(linha: string): ItemCupom | null {
  const t = linha.trim();
  if (!t) return null;
  // Ignora linhas que claramente não são itens
  if (/^(total|subtotal|troco|dinheiro|cart[aã]o|pix|cupom|nota|venda|item|qtd|desc|vltotal|cnpj|ie|ccf|coo|data|hora|obrigado|volte|valor|forma|pagto|extrato|sistema)/i.test(t)) {
    return null;
  }

  // Números com vírgula (casas decimais) — qtd/peso/preço BR
  const decimais = t.match(/(\d{1,3}(?:\.\d{3})*,\d+|\d+,\d+)/g) || [];
  if (decimais.length < 2) return null;

  let quantidade: number;
  let valorUnitario: number;

  if (decimais.length >= 3) {
    // "2,000 21,90 43,80" (qtd, preço, total-linha)
    quantidade = paraNumero(decimais[0]!);
    valorUnitario = paraNumero(decimais[1]!);
  } else if (decimais.length === 2) {
    quantidade = paraNumero(decimais[0]!);
    valorUnitario = paraNumero(decimais[1]!);
    // "ARROZ X 39,80 5,00" não é item válido
  } else {
    return null;
  }

  if (!Number.isFinite(quantidade) || quantidade <= 0 || !Number.isFinite(valorUnitario) || valorUnitario <= 0) {
    return null;
  }

  // Limpeza do nome: remove TODOS os números com vírgula da linha (qtd, preço, total)
  let nome = t.replace(/(\d{1,3}(?:\.\d{3})*,\d+|\d+,\d+)/g, " ");

  // Trata qtd inteira com "X" (ex.: "1 X DETERGENTE ..."), mantém o inteiro do X descartado no nome
  const mX = t.match(/^(\d+)\s*x\s+/i) || t.match(/(?:^|\s)(\d+)\s*x\s+/i);
  if (mX) {
    const nX = parseInt(mX[1], 10);
    if (nX > 0) {
      quantidade = nX;
      nome = nome.replace(new RegExp(`\\b${mX[1]}\\s*x\\s*`, "gi"), " ");
    }
  }

  nome = nome
    .replace(/\b\d{6,}\b/g, " ")
    .replace(/^[A-Za-z]{1,5}\s*\d{1,6}\s+/, " ")
    .replace(/\s{2,}/g, " ")
    .trim();

  nome = nome
    .replace(/^\s*x\s+/i, "")
    .replace(/\s+x\s*$/i, "")
    .replace(/^[-|•*\d.\s]+/, "")
    .replace(/\s+[-|•*]+\s*$/, "")
    .trim();
  if (!nome || nome.length < 2 || /^[0-9,.\s]+$/.test(nome)) return null;

  return {
    nome,
    quantidade,
    valorUnitario,
    valorTotal: null,
    unidade: null,
  };
}

/** Converte "2,000" ou "1.234,56" em número. */
function paraNumero(n: string): number {
  const limpo = n.replace(/\./g, "").replace(",", ".");
  const v = parseFloat(limpo);
  return Number.isFinite(v) ? v : 0;
}

/** Junta linhas do cupom (continuações de nome entre linhas). */
export function parseCupomTexto(texto: string): CupomParseResult {
  const linhas = texto
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter(Boolean);

  let itens: ItemCupom[] = [];
  let pendente: string | null = null;
  let total: number | null = null;

  for (const linha of linhas) {
    const item = parseLinhaItem(linha);
    if (item) {
      if (pendente && item.nome.length > pendente.length * 0.5) {
        // possivel continuação: prefere a linha seguinte como novo item
      }
      itens.push(item);
      pendente = null;
    } else if (linha.match(/^total/i)) {
      const tn = extrairDecimal(linha.replace(/^total/i, ""));
      if (tn !== null) total = tn;
      pendente = null;
    } else {
      // linha sem números: pode ser continuação do nome do item anterior
      if (!/^[-–—|•*]+$/.test(linha) && !linha.match(/\d/)) {
        pendente = linha;
      }
    }
  }

  // Se só tem 1 linha com numeros e nada mais, não parseia itens
  if (itens.length === 0) return { itens: [], total, numero: null, data: null, raw: texto };
  return { itens, total, numero: null, data: null, raw: texto };
}

// ---------------------------------------------------------------------------
// Parsing de XML (SAT CF-e e NF-e/NFC-e)
// ---------------------------------------------------------------------------

export function parseCupomXml(xml: string): CupomParseResult {
  const dets: ItemCupom[] = [];

  const extrair = (bloco: string) => {
    const g = (tag: string) => {
      const m = new RegExp(`<${tag}>([\\s\\S]*?)<\\/${tag}>`).exec(bloco);
      return m ? m[1].trim() : null;
    };
    const nome = g("xProd");
    if (!nome) return;
    const quantidade = parseFloat((g("qCom") || g("qTrib") || "").replace(",", "."));
    const vUnCom = parseFloat((g("vUnCom") || g("vUnTrib") || "").replace(",", "."));
    const vProd = parseFloat((g("vProd") || "").replace(",", "."));
    dets.push({
      nome,
      quantidade: Number.isFinite(quantidade) && quantidade > 0 ? quantidade : 1,
      valorUnitario: Number.isFinite(vUnCom) ? vUnCom : null,
      valorTotal: Number.isFinite(vProd) ? vProd : null,
      unidade: g("cEAN") === "SEM GTIN" || !g("cEAN") ? (g("uCom") || g("uTrib") || null) : (g("uCom") || g("uTrib") || null),
    });
  };

  const blocos = xml.split(/<det[^>]*>/).slice(1);
  for (const b of blocos) {
    const fim = b.indexOf("</det>");
    if (fim >= 0) extrair(b.slice(0, fim));
  }

  let total: number | null = null;
  const tM = xml.match(/<vNF>([\s\S]*?)<\/vNF>|<vCFe>([\s\S]*?)<\/vCFe>/);
  if (tM) {
    const tv = (tM[1] || tM[2] || "").replace(",", ".");
    const parsed = parseFloat(tv);
    if (Number.isFinite(parsed)) total = parsed;
  }

  let numero: string | null = null;
  const nCFe = xml.match(/<nCFe>([\s\S]*?)<\/nCFe>/);
  const nNF = xml.match(/<nNF>([\s\S]*?)<\/nNF>/);
  numero = (nCFe?.[1] || nNF?.[1] || null)?.trim() || null;

  let data: string | null = null;
  const dEmi = xml.match(/<dEmi>([\s\S]*?)<\/dEmi>/);
  let hEmi = xml.match(/<hEmi>([\s\S]*?)<\/hEmi>/);
  const dEmissao = xml.match(/<dhEmi>([\s\S]*?)<\/dhEmi>/);
  if (dEmi) {
    data = dEmi[1].trim();
    if (hEmi && hEmi[1]) data = `${data} ${hEmi[1].trim()}`;
  } else if (dEmissao) {
    data = dEmissao[1].trim().replace("T", " ");
  }

  return { itens: dets, total, numero, data, raw: xml };
}

// ---------------------------------------------------------------------------
// Detecção automática do tipo de conteúdo
// ---------------------------------------------------------------------------

export function detectarFonte(entrada: string): "xml" | "texto" | "desconhecido" {
  const t = entrada.trim();
  if (t.startsWith("<") && t.includes("<det")) return "xml";
  if (t.startsWith("<") && /<CFe|<NFe/i.test(t)) return "xml";
  if (t.split(/\r?\n/).length >= 2) return "texto";
  return "desconhecido";
}

export function parseCupom(entrada: string): CupomParseResult {
  const tipo = detectarFonte(entrada);
  if (tipo === "xml") return parseCupomXml(entrada);
  return parseCupomTexto(entrada);
}

/** Infere a unidade de medida a partir do nome do item do cupom. */
export function derivarUnidade(nome: string): string {
  const t = (nome || "").toUpperCase();
  if (/\bML\b/.test(t) || /MILILITRO/.test(t)) return "ML";
  if (/\bKG\b/.test(t) || /QUILO/.test(t)) return "KG";
  if (/\bL\b|\bLITRO/.test(t)) return "L";
  if (/\bG\b|\bGRAMA/.test(t)) return "G";
  if (/\bPCT\b|PACOTE/.test(t)) return "PCT";
  if (/\bCX\b|CAIXA/.test(t)) return "CX";
  if (/\bCARTA\b|CARTELA/.test(t)) return "CARTELA";
  if (/\bUNID\b/.test(t)) return "UN";
  return "UN";
}
