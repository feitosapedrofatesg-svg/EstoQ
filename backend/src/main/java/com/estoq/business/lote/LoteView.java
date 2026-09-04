package com.estoq.business.lote;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoteView {

	private UUID id;
	private String codigo;
	private UUID produtoId;
	private String produtoNome;
	private String unidadeMedida;
	private BigDecimal quantidadeInicial;
	private BigDecimal quantidadeAtual;
	private LocalDate dataEntrada;
	private LocalDate dataValidade;
	private BigDecimal precoUnitario;
	private boolean vencido;
	private boolean disponivel;
	private int diasParaVencimento;
}