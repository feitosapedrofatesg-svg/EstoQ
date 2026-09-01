package com.estoq.business.periodo;

import com.estoq.core.dtos.BaseDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PeriodoDTO extends BaseDTO {

	private String nome;
	private LocalDate dataInicio;
	private LocalDate dataFim;
	private BigDecimal vendas;
	private PeriodoStatus status;
}