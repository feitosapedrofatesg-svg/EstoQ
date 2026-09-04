package com.estoq.business.produtoaberto;

import com.estoq.business.lote.LoteModel;
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
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "produtos_abertos")
public class ProdutoAbertoModel extends BaseModel {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "produto_id", nullable = false)
	private ProdutoModel produto;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "lote_id")
	private LoteModel lote;

	@Column(name = "data_abertura", nullable = false)
	private LocalDateTime dataAbertura;

	@Column(name = "quantidade_aberta", precision = 12, scale = 3, nullable = false)
	private BigDecimal quantidadeAberta;

	@Column(name = "quantidade_utilizada", precision = 12, scale = 3, nullable = false)
	private BigDecimal quantidadeUtilizada;

	@Column(name = "finalizado")
	private boolean finalizado;

	/** Quantidade restante = quantidade aberta − quantidade utilizada (valor derivado). */
	public BigDecimal getQuantidadeRestante() {
		return quantidadeAberta == null ? BigDecimal.ZERO
				: quantidadeAberta.subtract(quantidadeUtilizada == null ? BigDecimal.ZERO : quantidadeUtilizada);
	}

	public BigDecimal calcularQuantidadeRestante() {
		return getQuantidadeRestante();
	}

	/** Registra quebra de sobra: quantidade devolvida ao estoque. */
	public void registrarSobra(BigDecimal quantidade) {
		if (quantidade == null || quantidade.signum() <= 0) {
			throw new BusinessException("Quantidade de sobra deve ser maior que zero.", HttpStatus.BAD_REQUEST);
		}
		if (getQuantidadeRestante().compareTo(quantidade) < 0) {
			throw new BusinessException("Sobra maior que a quantidade restante da embalagem.",
					HttpStatus.BAD_REQUEST);
		}
		this.quantidadeUtilizada = (quantidadeUtilizada == null ? BigDecimal.ZERO : quantidadeUtilizada)
				.add(quantidade);
	}

	public void marcarFinalizado() {
		this.finalizado = true;
	}
}