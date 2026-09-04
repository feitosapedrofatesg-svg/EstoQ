package com.estoq.business.movimentacao;

import com.estoq.business.balanco.ItemBalancoModel;
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
@DiscriminatorValue("AJUSTE")
public class AjusteModel extends MovimentacaoEstoqueModel {

	@Column(name = "diferenca_apurada", precision = 12, scale = 3)
	private BigDecimal diferencaApurada;

	@Column(name = "justificativa", length = 255)
	private String justificativa;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "item_balanco_id")
	private ItemBalancoModel itemBalanco;

	@Override
	public TipoMovimentacao getTipo() {
		return TipoMovimentacao.AJUSTE;
	}
}