package com.estoq.business.venda;

import com.estoq.core.domains.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "vendas_mes")
public class VendaMesModel extends BaseModel {

	@Column(name = "ano", nullable = false)
	private int ano;

	@Column(name = "mes", nullable = false)
	private int mes;

	@Column(name = "valor_vendas", precision = 14, scale = 2, nullable = false)
	private BigDecimal valorVendas = BigDecimal.ZERO;
}