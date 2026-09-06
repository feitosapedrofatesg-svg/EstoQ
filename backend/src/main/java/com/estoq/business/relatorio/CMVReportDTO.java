package com.estoq.business.relatorio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CMVReportDTO {

	private LocalDate dataInicio;
	private LocalDate dataFim;
	private BigDecimal vendas;
	private BigDecimal metaCmv;
	private BigDecimal totalConsumo;
	private BigDecimal totalDesperdicio;
	private BigDecimal totalGeral;
	private BigDecimal cmv;
	private String avaliacao;
	private String mensagem;
	private List<CMVItemDTO> itens = new ArrayList<>();
}