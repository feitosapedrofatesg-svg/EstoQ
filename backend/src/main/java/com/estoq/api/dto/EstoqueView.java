package com.estoq.api.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

@Data
public class EstoqueView {

	private UUID id;
	private UUID periodoId;
	private String periodoNome;
	private UUID produtoId;
	private String produtoNome;
	private String unidade;

	private BigDecimal quantidadeInicial;
	private BigDecimal valorUnitarioInicial;
	private BigDecimal valorInicial;

	private BigDecimal quantidadeFinal;
	private BigDecimal valorUnitarioFinal;
	private BigDecimal valorFinal;
}