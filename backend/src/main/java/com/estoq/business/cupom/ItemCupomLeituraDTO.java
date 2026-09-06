package com.estoq.business.cupom;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Item de cupom identificado na leitura (QR/XML ou OCR).
 * Usado apenas como prévia — NÃO grava nada no estoque.
 */
@Getter
@Setter
public class ItemCupomLeituraDTO {

	private String descricao;

	/**
	 * Código do produto detectado (EAN/GTIN ou código interno do cupom), quando
	 * o OCR espacial consegue ler a coluna de código. Pode ser {@code null}.
	 */
	private String codigo;

	private BigDecimal quantidade;

	private BigDecimal precoUnitario;

	private BigDecimal precoTotal;

	/**
	 * Confiança do item (0..1). Apenas informativa nesta versão; o backend já
	 * filtra itens abaixo do limiar, então raramente chega com valor baixo.
	 */
	private Double confianca;

	public ItemCupomLeituraDTO(String descricao, BigDecimal quantidade, BigDecimal precoUnitario,
			BigDecimal precoTotal) {
		this.descricao = descricao;
		this.quantidade = quantidade;
		this.precoUnitario = precoUnitario;
		this.precoTotal = precoTotal;
	}
}
