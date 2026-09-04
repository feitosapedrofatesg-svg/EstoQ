package com.estoq.business.balanco;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BalancoView {

	private UUID id;
	private LocalDateTime dataHora;
	private String tipo;
	private String status;
	private String responsavelNome;
	private List<ItemBalancoView> itens;
	private long qtdItens;
	private BigDecimal totalDiferenca;
}