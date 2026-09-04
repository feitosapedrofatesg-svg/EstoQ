package com.estoq.business.produto;

import java.util.Locale;

public enum UnidadeMedida {
	KG, G, L, ML, UN, PCT, CARTELA, CX;

	/** Converte o texto livre do catálogo legado (ex.: "kg", "und", "pacote") para a enum. */
	public static UnidadeMedida fromLegado(String valor) {
		if (valor == null) {
			return UN;
		}
		String v = valor.trim().toLowerCase(Locale.ROOT);
		return switch (v) {
			case "kg" -> KG;
			case "g", "grama", "gramas" -> G;
			case "l", "litro", "litros" -> L;
			case "ml", "mililitro", "mililitros" -> ML;
			case "und", "um", "unidade", "unidades", "un" -> UN;
			case "pacote", "pct", "pct." -> PCT;
			case "cartela", "carta" -> CARTELA;
			case "cx", "caixa", "caixas" -> CX;
			default -> UN;
		};
	}
}