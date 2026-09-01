package com.estoq.business.estoque;

import com.estoq.business.periodo.PeriodoModel;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.core.domains.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "estoque_periodo", uniqueConstraints = @UniqueConstraint(name = "uk_estoque_periodo_produto",
		columnNames = { "periodo_id", "produto_id" }))
public class EstoquePeriodoModel extends BaseModel {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "periodo_id", nullable = false)
	private PeriodoModel periodo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "produto_id", nullable = false)
	private ProdutoModel produto;

	@Column(name = "quantidade_inicial", precision = 12, scale = 3, nullable = false)
	private BigDecimal quantidadeInicial;

	@Column(name = "valor_unitario_inicial", precision = 14, scale = 2)
	private BigDecimal valorUnitarioInicial;

	@Column(name = "quantidade_final", precision = 12, scale = 3, nullable = false)
	private BigDecimal quantidadeFinal;

	@Column(name = "valor_unitario_final", precision = 14, scale = 2)
	private BigDecimal valorUnitarioFinal;
}