package com.estoq.business.produto;

import com.estoq.core.domains.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
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

	@Column(name = "unidade", length = 20, nullable = false)
	private String unidade;

	@Column(name = "categoria", length = 60)
	private String categoria;

	@Column(name = "estoque_minimo", precision = 12, scale = 3, nullable = false)
	private BigDecimal estoqueMinimo;
}