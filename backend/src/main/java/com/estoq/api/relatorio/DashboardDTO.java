package com.estoq.api.relatorio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class DashboardDTO {

	private BigDecimal cmvDoMes;
	private BigDecimal cmvMeta;
	private BigDecimal consumoDoMes;
	private BigDecimal vendasDoMes;
	private long totalProdutos;
	private long produtosComEstoqueBaixo;
	private long periodosAbertos;
	private long comprasNoMes;
	private List<AlertaEstoque> principaisAlertas = new ArrayList<>();
}