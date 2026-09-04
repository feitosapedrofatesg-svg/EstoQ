package com.estoq.business.relatorio;

import com.estoq.business.alerta.AlertaView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DashboardDTO {

	private int ano;
	private int mes;
	private BigDecimal metaCmv;
	private BigDecimal cmvMes;
	private BigDecimal consumoMes;
	private BigDecimal desperdicioMes;
	private long totalProdutos;
	private long produtosComEstoqueBaixo;
	private long lotesVencendo;
	private long lotesVencidos;
	private boolean balancoPendente;
	private long alertasPendentes;
	private List<AlertaView> principaisAlertas = new ArrayList<>();
}