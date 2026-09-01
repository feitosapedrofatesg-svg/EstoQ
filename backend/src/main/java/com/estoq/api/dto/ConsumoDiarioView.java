package com.estoq.api.dto;

import com.estoq.business.consumodiario.ConsumoDiarioModel;
import com.estoq.business.consumodiario.TipoUso;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ConsumoDiarioView {

	private UUID id;
	private LocalDate data;
	private UUID produtoId;
	private String produtoNome;
	private String unidade;
	private TipoUso tipo;
	private BigDecimal quantidade;
	private UUID usuarioId;
	private String usuarioNome;
	private LocalDateTime dataHoraRegistro;

	public static ConsumoDiarioView of(ConsumoDiarioModel m) {
		return new ConsumoDiarioView(
				m.getId(), m.getData(),
				m.getProduto().getId(), m.getProduto().getNome(), m.getProduto().getUnidade(),
				m.getTipo(), m.getQuantidade(),
				m.getUsuario().getId(), m.getUsuario().getNome(),
				m.getDataHoraRegistro());
	}
}