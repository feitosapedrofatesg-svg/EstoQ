package com.estoq.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Data;

@Data
public class CompraView {

	private UUID id;
	private UUID produtoId;
	private String produtoNome;
	private String unidade;
	private UUID periodoId;
	private String periodoNome;
	private BigDecimal quantidade;
	private BigDecimal precoUnitario;
	private BigDecimal total;
	private LocalDate dataCompra;
}