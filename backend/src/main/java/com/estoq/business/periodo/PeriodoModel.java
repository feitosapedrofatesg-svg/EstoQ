package com.estoq.business.periodo;

import com.estoq.core.domains.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "periodos")
public class PeriodoModel extends BaseModel {

	@Column(name = "nome", length = 80, nullable = false)
	private String nome;

	@Column(name = "data_inicio", nullable = false)
	private LocalDate dataInicio;

	@Column(name = "data_fim", nullable = false)
	private LocalDate dataFim;

	@Column(name = "vendas", precision = 14, scale = 2)
	private BigDecimal vendas;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", length = 20, nullable = false)
	private PeriodoStatus status;
}