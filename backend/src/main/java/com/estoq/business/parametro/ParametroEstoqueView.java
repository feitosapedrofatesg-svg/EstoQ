package com.estoq.business.parametro;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParametroEstoqueView {

	private UUID id;
	private UUID produtoId;
	private String produtoNome;
	private BigDecimal estoqueMinimo;
	private BigDecimal estoqueMedio;
	private BigDecimal estoqueMaximo;
	private BigDecimal consumoMedioDiario;
	private Integer tempoReposicaoDias;
	private Integer periodoAnaliseDias;
	private LocalDateTime dataAtualizacao;
	private BigDecimal saldoAtual;
	private String unidadeMedida;
}