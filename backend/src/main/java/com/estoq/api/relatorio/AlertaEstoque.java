package com.estoq.api.relatorio;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class AlertaEstoque {

	private java.util.UUID produtoId;
	private String produtoNome;
	private String unidade;
	private String categoria;
	/** Quantidade em estoque (estoque final da última semana lançada). */
	private BigDecimal estoqueAtual;
	private BigDecimal estoqueMinimo;
	/** Consumo médio semanal calculado nas últimas semanas. */
	private BigDecimal consumoMedioSemanal;
	private String status;

	public static final String REPOR = "REPOR";
	public static final String ATENCAO = "ATENCAO";
	public static final String OK = "OK";
	public static final String SEM_DADOS = "SEM_DADOS";
}