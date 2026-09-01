package com.estoq.api.relatorio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class RelatorioCMVPeriodo {

	private UUID periodoId;
	private String periodoNome;
	private LocalDate dataInicio;
	private LocalDate dataFim;
	private BigDecimal vendas;
	private BigDecimal metaCmv = BigDecimal.valueOf(0.40);

	private BigDecimal totalEstoqueInicial;
	private BigDecimal totalCompras;
	private BigDecimal totalEstoqueFinal;
	private BigDecimal totalConsumo;

	/** Custo de Mercadoria Vendida = consumo / vendas. */
	private BigDecimal cmv;

	private List<ItemRelatorioCMV> itens = new ArrayList<>();
}