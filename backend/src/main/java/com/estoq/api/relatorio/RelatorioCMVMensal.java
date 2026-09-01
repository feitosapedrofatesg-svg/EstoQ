package com.estoq.api.relatorio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class RelatorioCMVMensal {

	private int ano;
	private int mes;

	private BigDecimal vendas;
	private BigDecimal totalEstoqueInicial;
	private BigDecimal totalCompras;
	private BigDecimal totalEstoqueFinal;
	private BigDecimal totalConsumo;
	private BigDecimal cmv;

	private List<RelatorioCMVPeriodo> periodos = new ArrayList<>();
}