package com.estoq.business.sessao;

import com.estoq.core.domains.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sessoes")
public class SessaoModel extends BaseModel {

	@Column(name = "token", length = 64, unique = true, nullable = false)
	private String token;

	@Column(name = "usuario_id", nullable = false)
	private UUID usuarioId;

	@Column(name = "criado_em")
	private LocalDateTime criadoEm;

	@Column(name = "expira_em")
	private LocalDateTime expiraEm;

	@Column(name = "origem", length = 60)
	private String origem;
}