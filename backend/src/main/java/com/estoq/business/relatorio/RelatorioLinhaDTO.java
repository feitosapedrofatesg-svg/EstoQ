package com.estoq.business.relatorio;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RelatorioLinhaDTO {

	private String chave;
	private String detalhe;
	private String unidadeMedida;
	private BigDecimal quantidade;
	private BigDecimal valor;
	private String data;
	private String status;
}