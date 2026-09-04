package com.estoq.business.balanco;

import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfiguracaoBalancoView {

	private UUID id;
	private String periodicidade;
	private Integer diaExecucao;
	private LocalDate proximaExecucao;
	private boolean pendente;
	private long balancosEmAndamento;
}