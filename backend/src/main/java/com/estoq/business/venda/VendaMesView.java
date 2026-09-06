package com.estoq.business.venda;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VendaMesView {

	private int ano;
	private int mes;
	private BigDecimal valorVendas;

	public static VendaMesView of(VendaMesModel m) {
		return new VendaMesView(m.getAno(), m.getMes(), m.getValorVendas());
	}
}