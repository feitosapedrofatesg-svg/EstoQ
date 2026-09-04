package com.estoq.business.movimentacao;

import com.estoq.business.produtoaberto.ProdutoAbertoModel;
import com.estoq.core.helpers.NumeroUtil;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@DiscriminatorValue("CONSUMO")
public class ConsumoModel extends MovimentacaoEstoqueModel {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "produto_aberto_id")
	private ProdutoAbertoModel produtoAberto;

	/** Custo do consumo = quantidade × preço unitário do lote (valor derivado). */
	@Column(name = "custo_consumo", precision = 14, scale = 2)
	private BigDecimal custoConsumo;

	@Override
	public TipoMovimentacao getTipo() {
		return TipoMovimentacao.CONSUMO;
	}

	public BigDecimal calcularCusto() {
		if (custoConsumo == null) {
			BigDecimal preco = NumeroUtil.s(getLote() != null ? getLote().getPrecoUnitario() : null);
			this.custoConsumo = NumeroUtil.multiplica(getQuantidade(), preco);
		}
		return custoConsumo;
	}
}