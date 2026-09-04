package com.estoq.business.movimentacao;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MovimentacaoView {

	private UUID id;
	private String tipo;
	private LocalDateTime dataHora;
	private UUID produtoId;
	private String produtoNome;
	private String unidadeMedida;
	private BigDecimal quantidade;
	private BigDecimal quantidadeAnterior;
	private BigDecimal quantidadePosterior;
	private String observacao;
	private String usuarioNome;
	private UUID loteId;
	private String loteCodigo;
	private String motivo;
	private BigDecimal valorPrejuizo;
	private BigDecimal custoConsumo;
	private BigDecimal diferencaApurada;
}