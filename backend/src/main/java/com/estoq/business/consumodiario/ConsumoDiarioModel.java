package com.estoq.business.consumodiario;

import com.estoq.business.produto.ProdutoModel;
import com.estoq.business.usuario.UsuarioModel;
import com.estoq.core.domains.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "consumo_diario")
public class ConsumoDiarioModel extends BaseModel {

	@Column(name = "data", nullable = false)
	private LocalDate data;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "produto_id", nullable = false)
	private ProdutoModel produto;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo", length = 10, nullable = false)
	private TipoUso tipo;

	@Column(name = "quantidade", precision = 12, scale = 3, nullable = false)
	private BigDecimal quantidade;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private UsuarioModel usuario;

	@Column(name = "data_hora_registro")
	private LocalDateTime dataHoraRegistro;
}