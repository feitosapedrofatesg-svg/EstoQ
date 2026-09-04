package com.estoq.business.produto;

import com.estoq.business.categoria.CategoriaModel;
import com.estoq.core.domains.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "produtos")
public class ProdutoModel extends BaseModel {

	@Column(name = "nome", length = 120, unique = true, nullable = false)
	private String nome;

	@Enumerated(EnumType.STRING)
	@Column(name = "unidade_medida", length = 20, nullable = false)
	private UnidadeMedida unidadeMedida;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "categoria_id")
	private CategoriaModel categoria;

	/** Saldo derivado: soma das quantidades disponíveis nos lotes e embalagens abertas. */
	@Transient
	private BigDecimal saldoAtual;

	/** Estoque mínimo do produto (persistido em ParametroEstoque); transportado no DTO. */
	@Transient
	private BigDecimal estoqueMinimo;

	public void ativar() {
		setAtivo(true);
	}

	public void desativar() {
		setAtivo(false);
	}

	public boolean estaAbaixoDoMinimo(BigDecimal estoqueMinimo) {
		BigDecimal saldo = saldoAtual == null ? BigDecimal.ZERO : saldoAtual;
		return saldo.compareTo(estoqueMinimo == null ? BigDecimal.ZERO : estoqueMinimo) < 0;
	}
}