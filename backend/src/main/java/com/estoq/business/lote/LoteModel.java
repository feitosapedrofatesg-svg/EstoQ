package com.estoq.business.lote;

import com.estoq.business.produto.ProdutoModel;
import com.estoq.core.domains.BaseModel;
import com.estoq.core.exceptions.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "lotes")
public class LoteModel extends BaseModel {

	@Column(name = "codigo", length = 40, nullable = false)
	private String codigo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "produto_id", nullable = false)
	private ProdutoModel produto;

	@Column(name = "quantidade_inicial", precision = 12, scale = 3, nullable = false)
	private BigDecimal quantidadeInicial;

	@Column(name = "quantidade_atual", precision = 12, scale = 3, nullable = false)
	private BigDecimal quantidadeAtual;

	@Column(name = "data_entrada", nullable = false)
	private LocalDate dataEntrada;

	@Column(name = "data_validade")
	private LocalDate dataValidade;

	@Column(name = "preco_unitario", precision = 14, scale = 2, nullable = false)
	private BigDecimal precoUnitario;

	public boolean estaDisponivel() {
		return !estaVencido() && quantidadeAtual != null && quantidadeAtual.signum() > 0;
	}

	public boolean estaVencido() {
		return estaVencido(LocalDate.now());
	}

	public boolean estaVencido(LocalDate referencia) {
		return dataValidade != null && dataValidade.isBefore(referencia);
	}

	/** Dias até o vencimento; negativo quando já vencido e zero quando sem validade. */
	public int diasParaVencimento() {
		return diasParaVencimento(LocalDate.now());
	}

	public int diasParaVencimento(LocalDate referencia) {
		if (dataValidade == null) {
			return 0;
		}
		return (int) java.time.Duration.between(
				referencia.atStartOfDay(), dataValidade.atStartOfDay()).toDays();
	}

	/** Dá baixa em quantidade do lote, recusando quando insuficiente. */
	public void baixar(BigDecimal quantidade) {
		if (quantidade == null || quantidade.signum() <= 0) {
			throw new BusinessException("Quantidade deve ser maior que zero.", HttpStatus.BAD_REQUEST);
		}
		if (quantidadeAtual == null || quantidadeAtual.compareTo(quantidade) < 0) {
			throw new BusinessException(
					"Saldo insuficiente no lote " + codigo + " (disponível: "
							+ (quantidadeAtual == null ? 0 : quantidadeAtual) + ").",
					HttpStatus.BAD_REQUEST);
		}
		this.quantidadeAtual = quantidadeAtual.subtract(quantidade);
	}

	/** Credita quantidade de volta ao lote (ex.: sobra). */
	public void creditar(BigDecimal quantidade) {
		if (quantidade == null || quantidade.signum() <= 0) {
			throw new BusinessException("Quantidade deve ser maior que zero.", HttpStatus.BAD_REQUEST);
		}
		this.quantidadeAtual = quantidadeAtual == null ? quantidade : quantidadeAtual.add(quantidade);
	}
}