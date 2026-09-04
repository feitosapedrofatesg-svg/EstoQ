package com.estoq.business.movimentacao;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AjusteRequest {

	private UUID produtoId;
	private BigDecimal diferenca;
	private String justificativa;
	private String observacao;
}