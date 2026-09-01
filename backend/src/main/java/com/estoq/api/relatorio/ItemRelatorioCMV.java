package com.estoq.api.relatorio;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

@Data
public class ItemRelatorioCMV {

	private UUID produtoId;
	private String produtoNome;
	private String unidade;
	private String categoria;

	private BigDecimal estoqueInicialQtd;
	private BigDecimal estoqueInicialValor;

	private BigDecimal comprasQtd;
	private BigDecimal comprasValorUnitarioMedio;
	private BigDecimal comprasValor;

	private BigDecimal estoqueFinalQtd;
	private BigDecimal estoqueFinalValorUnitario;
	private BigDecimal estoqueFinalValor;

	private BigDecimal consumoQtd;
	private BigDecimal consumoValor;
}