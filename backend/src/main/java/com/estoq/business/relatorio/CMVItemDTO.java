package com.estoq.business.relatorio;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CMVItemDTO {

	private UUID produtoId;
	private String produtoNome;
	private String categoriaNome;
	private String unidadeMedida;
	private BigDecimal estoqueInicialQtd;
	private BigDecimal estoqueInicialValor;
	private BigDecimal entradasQtd;
	private BigDecimal entradasValor;
	private BigDecimal estoqueFinalQtd;
	private BigDecimal estoqueFinalValor;
	private BigDecimal consumoQtd;
	private BigDecimal consumoValor;
	private BigDecimal desperdicioQtd;
	private BigDecimal desperdicioValor;
	private BigDecimal totalValor;
}