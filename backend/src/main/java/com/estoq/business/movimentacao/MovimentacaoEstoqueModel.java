package com.estoq.business.movimentacao;

import com.estoq.business.lote.LoteModel;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.business.usuario.UsuarioModel;
import com.estoq.core.domains.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo_movimentacao", discriminatorType = DiscriminatorType.STRING, length = 20)
@DiscriminatorValue("MOVIMENTACAO")
@Table(name = "movimentacoes_estoque")
public abstract class MovimentacaoEstoqueModel extends BaseModel {

	@Column(name = "data_hora", nullable = false)
	private LocalDateTime dataHora;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "produto_id", nullable = false)
	private ProdutoModel produto;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private UsuarioModel usuario;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "lote_id")
	private LoteModel lote;

	@Column(name = "quantidade", precision = 12, scale = 3, nullable = false)
	private BigDecimal quantidade;

	@Column(name = "quantidade_anterior", precision = 12, scale = 3)
	private BigDecimal quantidadeAnterior;

	@Column(name = "quantidade_posterior", precision = 12, scale = 3)
	private BigDecimal quantidadePosterior;

	@Column(name = "observacao", length = 255)
	private String observacao;

	public abstract TipoMovimentacao getTipo();
}