package com.estoq.business.movimentacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EntradaRequest {

	private UUID produtoId;
	private BigDecimal quantidade;
	private BigDecimal valorTotalPago;
	private String unidadeCompra;
	private LocalDate dataValidade;
	private String observacao;
}