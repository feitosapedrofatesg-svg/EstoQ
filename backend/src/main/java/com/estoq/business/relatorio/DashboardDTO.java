package com.estoq.business.relatorio;

import com.estoq.business.alerta.AlertaView;
import com.estoq.business.auditoria.AuditLogView;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
	private BigDecimal vendasMes;
	private BigDecimal metaDesperdicio;
	private BigDecimal desperdicioPct;
	private long totalProdutos;
	private long produtosComEstoqueBaixo;
	private long lotesVencendo;
	private long lotesVencidos;
	private long entradasSemValor;
	private boolean balancoPendente;
	private long alertasPendentes;
	private long sessoesAtivas;
	private LocalDateTime ultimoBackup;
	private boolean backupEmDia;
	private List<AlertaView> principaisAlertas = new ArrayList<>();
	private List<AuditLogView> ultimosEventos = new ArrayList<>();
}