package com.estoq.business.movimentacao;

import com.estoq.core.helpers.NumeroUtil;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@DiscriminatorValue("DESPERDICIO")
public class DesperdicioModel extends MovimentacaoEstoqueModel {

	@Enumerated(EnumType.STRING)
	@Column(name = "motivo", length = 30, nullable = true)
	private MotivoDesperdicio motivo;

	@Column(name = "descricao_motivo", length = 255)
	private String descricaoMotivo;

	/** Valor do prejuízo = quantidade × preço unitário do lote (valor derivado). */
	public BigDecimal getValorPrejuizo() {
		BigDecimal preco = NumeroUtil.s(getLote() != null ? getLote().getPrecoUnitario() : null);
		return NumeroUtil.money(NumeroUtil.multiplica(getQuantidade(), preco));
	}

	@Override
	public TipoMovimentacao getTipo() {
		return TipoMovimentacao.DESPERDICIO;
	}
}