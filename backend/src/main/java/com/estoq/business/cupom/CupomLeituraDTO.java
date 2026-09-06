package com.estoq.business.cupom;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado da leitura de um cupom fiscal (prévia).
 * Não registra nada no estoque — serve apenas de sugestão para o usuário
 * revisar antes de confirmar a entrada.
 */
@Getter
@Setter
public class CupomLeituraDTO {

	private String estabelecimento;

	private String data;

	/** Fonte origem da leitura: "QR_CODE", "XML_NFC_E" ou "OCR". */
	private String fonte;

	/** Indica se a leitura é de baixa confiança (ex.: OCR com texto ambíguo). */
	private boolean baixaConfianca;

	private List<ItemCupomLeituraDTO> itens = new ArrayList<>();

	public void addItem(ItemCupomLeituraDTO item) {
		this.itens.add(item);
	}
}
