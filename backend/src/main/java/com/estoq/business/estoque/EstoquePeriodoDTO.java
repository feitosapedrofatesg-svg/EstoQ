package com.estoq.business.estoque;

import com.estoq.core.dtos.BaseDTO;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EstoquePeriodoDTO extends BaseDTO {

	private UUID periodoId;
	private UUID produtoId;
	private BigDecimal quantidadeInicial;
	private BigDecimal valorUnitarioInicial;
	private BigDecimal quantidadeFinal;
	private BigDecimal valorUnitarioFinal;
}