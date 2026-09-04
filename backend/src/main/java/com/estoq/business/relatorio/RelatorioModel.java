package com.estoq.business.relatorio;

import com.estoq.core.domains.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "relatorios")
public class RelatorioModel extends BaseModel {

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo", length = 30, nullable = false)
	private TipoRelatorio tipo;

	@Column(name = "data_inicio")
	private LocalDate dataInicio;

	@Column(name = "data_fim")
	private LocalDate dataFim;

	@Column(name = "data_geracao", nullable = false)
	private LocalDateTime dataGeracao;

	@Column(name = "linhas_geradas")
	private Integer linhasGeradas;
}