import type { Produto } from "./types";

function normalizar(s: string): string {
  return s
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function tokeniza(s: string): string[] {
  return normalizar(s).split(" ").filter((w) => w.length > 1);
}

/** Similaridade simples baseada em tokens (0..1). */
function similaridade(a: string, b: string): number {
  const ta = tokeniza(a);
  const tb = tokeniza(b);
  if (ta.length === 0 || tb.length === 0) return 0;
  const setMenor = ta.length <= tb.length ? ta : tb;
  const setMaior = ta.length <= tb.length ? tb : ta;
  let iguais = 0;
  for (const tok of setMenor) {
    if (setMaior.includes(tok)) iguais++;
  }
  return iguais / Math.max(setMaior.length, 1);
}

/**
 * Encontra o produto do catálogo que melhor casa com o nome do item do cupom.
 * Retorna null se não houver um bom suficiente.
 */
export function casarProduto(nomeItem: string, produtos: Produto[]): Produto | null {
  const item = normalizar(nomeItem).replace(/\s*(kg|ml|l|un|g|g\b|lt)\s*$/g, "").trim();
  if (!item) return null;
  let melhor: Produto | null = null;
  let melhorPontos = 0;
  for (const p of produtos) {
    const pontos = similaridade(item, p.nome);
    if (pontos > melhorPontos) {
      melhorPontos = pontos;
      melhor = p;
    }
  }
  // exige pelo menos metade dos tokens do item no nome do produto
  const tokensItem = tokeniza(item);
  if (melhor && melhorPontos >= Math.min(0.6, 2 / Math.max(tokensItem.length, 1))) {
    return melhor;
  }
  return melhor && melhorPontos >= 0.5 ? melhor : null;
}

export function contemTokenUnico(texto: string, produtos: Produto[]): Produto | null {
  const t = normalizar(texto);
  for (const p of produtos) {
    const pn = normalizar(p.nome);
    if (pn === t) return p;
  }
  return null;
}
