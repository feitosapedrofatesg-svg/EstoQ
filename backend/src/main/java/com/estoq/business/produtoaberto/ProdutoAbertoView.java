package com.estoq.business.produtoaberto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProdutoAbertoView {

	private UUID id;
	private UUID produtoId;
	private String produtoNome;
	private String unidadeMedida;
	private LocalDateTime dataAbertura;
	private BigDecimal quantidadeAberta;
	private BigDecimal quantidadeUtilizada;
	private BigDecimal quantidadeRestante;
	private boolean finalizado;
}