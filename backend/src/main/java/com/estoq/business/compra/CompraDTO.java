package com.estoq.business.compra;

import com.estoq.core.dtos.BaseDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompraDTO extends BaseDTO {

	private UUID produtoId;
	private UUID periodoId;
	private BigDecimal quantidade;
	private BigDecimal precoUnitario;
	private LocalDate dataCompra;
}