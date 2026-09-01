package com.estoq.core.domains;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@MappedSuperclass
public abstract class BaseModel {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", insertable = false, updatable = false)
	private UUID id;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "data_hora_criacao")
	private LocalDateTime dataHoraCriacao;

	@Column(name = "ativo")
	private boolean ativo;

	@PrePersist
	public void prePersist() {
		this.dataHoraCriacao = LocalDateTime.now();
		this.ativo = true;
	}
}