package com.estoq.integracao.recibo;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Item extraído de um cupom fiscal pelo OCR + parser.
 */
@Data
public class ItemReciboDTO {

	private String descricao;

	private BigDecimal quantidade;

	private BigDecimal precoUnitario;
}
