package com.estoq.business.auditoria;

import com.estoq.core.domains.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "auditoria_log")
public class AuditLogModel extends BaseModel {

	@Column(name = "data_hora", nullable = false)
	private LocalDateTime dataHora;

	@Column(name = "acao", length = 40, nullable = false)
	private String acao;

	@Column(name = "entidade", length = 60, nullable = false)
	private String entidade;

	@Column(name = "entidade_id", length = 60)
	private String entidadeId;

	@Lob
	@Column(name = "descricao")
	private String descricao;

	@Column(name = "usuario_nome", length = 120, nullable = false)
	private String usuarioNome;
}