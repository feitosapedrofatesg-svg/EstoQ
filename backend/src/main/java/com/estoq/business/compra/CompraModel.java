package com.estoq.business.compra;

import com.estoq.business.periodo.PeriodoModel;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.core.domains.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "compras")
public class CompraModel extends BaseModel {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "periodo_id", nullable = false)
	private PeriodoModel periodo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "produto_id", nullable = false)
	private ProdutoModel produto;

	@Column(name = "quantidade", precision = 12, scale = 3, nullable = false)
	private BigDecimal quantidade;

	@Column(name = "preco_unitario", precision = 14, scale = 2, nullable = false)
	private BigDecimal precoUnitario;

	@Column(name = "data_compra", nullable = false)
	private LocalDate dataCompra;
}