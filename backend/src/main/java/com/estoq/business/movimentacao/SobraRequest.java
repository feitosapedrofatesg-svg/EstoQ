package com.estoq.business.movimentacao;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SobraRequest {

	private UUID produtoAbertoId;
	private BigDecimal quantidade;
}