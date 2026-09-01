package com.estoq.business.consumodiario;

import com.estoq.core.dtos.BaseDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConsumoDiarioDTO extends BaseDTO {

	private LocalDate data;
	private UUID produtoId;
	private TipoUso tipo;
	private BigDecimal quantidade;
}