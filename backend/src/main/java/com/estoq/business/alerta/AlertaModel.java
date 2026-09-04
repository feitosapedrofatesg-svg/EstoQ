package com.estoq.business.alerta;

import com.estoq.business.balanco.BalancoModel;
import com.estoq.business.lote.LoteModel;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.business.usuario.Perfil;
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

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "alertas")
public class AlertaModel extends BaseModel {

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo", length = 30, nullable = false)
	private TipoAlerta tipo;

	@Column(name = "mensagem", length = 255, nullable = false)
	private String mensagem;

	@Column(name = "data_geracao", nullable = false)
	private LocalDateTime dataGeracao;

	@Enumerated(EnumType.STRING)
	@Column(name = "perfil_destino", length = 20, nullable = false)
	private Perfil perfilDestino;

	@Column(name = "visualizado", nullable = false)
	private boolean visualizado;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "produto_id")
	private ProdutoModel produto;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "lote_id")
	private LoteModel lote;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "balanco_id")
	private BalancoModel balanco;

	public void marcarComoVisualizado() {
		this.visualizado = true;
	}
}