package com.estoq.business.balanco;

import com.estoq.business.produto.ProdutoModel;
import com.estoq.core.domains.BaseModel;
import com.estoq.core.helpers.NumeroUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "itens_balanco")
public class ItemBalancoModel extends BaseModel {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "balanco_id", nullable = false)
	private BalancoModel balanco;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "produto_id", nullable = false)
	private ProdutoModel produto;

	@Column(name = "quantidade_sistema", precision = 12, scale = 3, nullable = false)
	private BigDecimal quantidadeSistema;

	@Column(name = "quantidade_fisica", precision = 12, scale = 3, nullable = false)
	private BigDecimal quantidadeFisica;

	/** Diferença = quantidade física − quantidade do sistema (valor derivado). */
	public BigDecimal getDiferenca() {
		return NumeroUtil.s(quantidadeFisica).subtract(NumeroUtil.s(quantidadeSistema));
	}

	public BigDecimal calcularDiferenca() {
		return getDiferenca();
	}
}