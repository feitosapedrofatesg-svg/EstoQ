package com.estoq.business.movimentacao;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DesperdicioRequest {

	private UUID produtoId;
	private BigDecimal quantidade;
	private String motivo;
	private String descricaoMotivo;
	private UUID loteId;
	private String observacao;
}