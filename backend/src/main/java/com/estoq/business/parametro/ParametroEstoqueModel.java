package com.estoq.business.parametro;

import com.estoq.business.produto.ProdutoModel;
import com.estoq.core.domains.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "parametros_estoque")
public class ParametroEstoqueModel extends BaseModel {

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "produto_id", nullable = false)
	private ProdutoModel produto;

	@Column(name = "tempo_reposicao_dias", nullable = false)
	private Integer tempoReposicaoDias;

	@Column(name = "periodo_analise_dias", nullable = false)
	private Integer periodoAnaliseDias;

	@Column(name = "consumo_medio_diario", precision = 12, scale = 3)
	private BigDecimal consumoMedioDiario;

	@Column(name = "estoque_minimo", precision = 12, scale = 3, nullable = false)
	private BigDecimal estoqueMinimo;

	@Column(name = "estoque_medio", precision = 12, scale = 3)
	private BigDecimal estoqueMedio;

	@Column(name = "estoque_maximo", precision = 12, scale = 3)
	private BigDecimal estoqueMaximo;

	@Column(name = "data_atualizacao")
	private LocalDateTime dataAtualizacao;
}