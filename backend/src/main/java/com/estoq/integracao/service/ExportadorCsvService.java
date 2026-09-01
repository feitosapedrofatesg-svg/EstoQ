package com.estoq.integracao.service;

import com.estoq.api.relatorio.ItemRelatorioCMV;
import com.estoq.api.relatorio.RelatorioCMVMensal;
import com.estoq.api.relatorio.RelatorioCMVPeriodo;
import java.math.RoundingMode;
import java.util.StringJoiner;
import org.springframework.stereotype.Component;

/**
 * Exporta os relatórios para CSV (UTF-8, separador ;) — formato compatível com planilhas.
 */
@Component
public class ExportadorCsvService {

	private static final String SEP = ";";

	public String relatorioCmvPeriodo(RelatorioCMVPeriodo rel) {
		StringJoiner sb = new StringJoiner("\n");
		sb.add("estoQ - Relatório CMV");
		sb.add(rel.getPeriodoNome() + " | " + rel.getDataInicio() + " a " + rel.getDataFim());
		sb.add("");
		sb.add(cabecalho());
		for (ItemRelatorioCMV item : rel.getItens()) {
			sb.add(linha(item));
		}
		sb.add("");
		sb.add("TOTAL" + SEP + SEP
				+ SEP + SEP + fmt(rel.getTotalEstoqueInicial())
				+ SEP + SEP + fmt(rel.getTotalCompras())
				+ SEP + SEP + fmt(rel.getTotalEstoqueFinal())
				+ SEP + SEP + fmt(rel.getTotalConsumo()));
		sb.add("Vendas do período" + SEP + fmt(rel.getVendas()));
		sb.add("CMV (%)" + SEP + fmtPercentual(rel.getCmv()));
		return sb.toString();
	}

	public String relatorioCmvMensal(RelatorioCMVMensal rel) {
		StringJoiner sb = new StringJoiner("\n");
		sb.add("estoQ - Relatório CMV Mensal - " + rel.getMes() + "/" + rel.getAno());
		sb.add(cabecalho());
		for (RelatorioCMVPeriodo periodo : rel.getPeriodos()) {
			for (ItemRelatorioCMV item : periodo.getItens()) {
				sb.add(linha(item));
			}
		}
		sb.add("");
		sb.add("TOTAL" + SEP + SEP
				+ SEP + SEP + fmt(rel.getTotalEstoqueInicial())
				+ SEP + SEP + fmt(rel.getTotalCompras())
				+ SEP + SEP + fmt(rel.getTotalEstoqueFinal())
				+ SEP + SEP + fmt(rel.getTotalConsumo()));
		sb.add("Vendas do mês" + SEP + fmt(rel.getVendas()));
		sb.add("CMV (%)" + SEP + fmtPercentual(rel.getCmv()));
		return sb.toString();
	}

	private String cabecalho() {
		return String.join(SEP, "Produto", "Unidade", "Categoria",
				"Est.Inicial", "R$ Est.Inicial",
				"Compras", "R$ Unid.Médio", "R$ Compras",
				"Est.Final", "R$ Unid.Final", "R$ Est.Final",
				"Consumo Qtd", "R$ Consumo");
	}

	private String linha(ItemRelatorioCMV item) {
		return String.join(SEP,
				item.getProdutoNome(), item.getUnidade(), nz(item.getCategoria()),
				fmt(item.getEstoqueInicialQtd()), fmt(item.getEstoqueInicialValor()),
				fmt(item.getComprasQtd()), fmt(item.getComprasValorUnitarioMedio()), fmt(item.getComprasValor()),
				fmt(item.getEstoqueFinalQtd()), fmt(item.getEstoqueFinalValorUnitario()), fmt(item.getEstoqueFinalValor()),
				fmt(item.getConsumoQtd()), fmt(item.getConsumoValor()));
	}

	private String fmt(java.math.BigDecimal v) {
		return v == null ? "" : v.setScale(2, RoundingMode.HALF_UP).toPlainString().replace(".", ",");
	}

	private String fmtPercentual(java.math.BigDecimal v) {
		return v == null ? "" : v.setScale(2, RoundingMode.HALF_UP).multiply(java.math.BigDecimal.valueOf(100))
				.toPlainString().replace(".", ",") + "%";
	}

	private String nz(String s) {
		return s == null ? "" : s;
	}
}