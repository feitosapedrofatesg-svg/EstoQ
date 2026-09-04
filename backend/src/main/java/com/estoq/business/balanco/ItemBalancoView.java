package com.estoq.business.balanco;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ItemBalancoView {

	private UUID id;
	private UUID balancoId;
	private UUID produtoId;
	private String produtoNome;
	private String unidadeMedida;
	private BigDecimal quantidadeSistema;
	private BigDecimal quantidadeFisica;
	private BigDecimal diferenca;
}