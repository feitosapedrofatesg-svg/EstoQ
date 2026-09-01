package com.estoq.api.relatorio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class ConsumoMatriz {

	@Data
	public static class ColunaPeriodo {
		private UUID periodoId;
		private String periodoNome;
		private LocalDate dataInicio;
		private LocalDate dataFim;
	}

	private List<ColunaPeriodo> periodos = new ArrayList<>();
	private List<ConsumoMatrizItem> itens = new ArrayList<>();

	public BigDecimal getConsumoTotal() {
		BigDecimal total = BigDecimal.ZERO;
		for (ConsumoMatrizItem item : itens) {
			total = total.add(item.getConsumoTotal() == null ? BigDecimal.ZERO : item.getConsumoTotal());
		}
		return total;
	}
}