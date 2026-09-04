package com.estoq.business.relatorio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RelatorioViewDTO {

	private UUID id;
	private String tipo;
	private LocalDate dataInicio;
	private LocalDate dataFim;
	private LocalDateTime dataGeracao;
	private int linhasGeradas;
	private List<RelatorioLinhaDTO> linhas = new ArrayList<>();
}