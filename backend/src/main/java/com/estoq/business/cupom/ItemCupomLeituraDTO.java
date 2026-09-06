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

	private BigDecimal quantidade;

	private BigDecimal precoUnitario;

	private BigDecimal precoTotal;

	public ItemCupomLeituraDTO(String descricao, BigDecimal quantidade, BigDecimal precoUnitario,
			BigDecimal precoTotal) {
		this.descricao = descricao;
		this.quantidade = quantidade;
		this.precoUnitario = precoUnitario;
		this.precoTotal = precoTotal;
	}
}
