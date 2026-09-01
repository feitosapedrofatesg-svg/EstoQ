package com.estoq.api.relatorio;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

@Data
public class ConsumoMatrizItem {

	private UUID produtoId;
	private String produtoNome;
	private String unidade;
	private String categoria;
	private BigDecimal[] consumoPorPeriodo;
	private BigDecimal consumoTotal;
	private BigDecimal estoqueMinimo;
}