package com.estoq.business.auditoria;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuditLogView {

	private UUID id;
	private LocalDateTime dataHora;
	private String acao;
	private String entidade;
	private String entidadeId;
	private String descricao;
	private String usuarioNome;

	public static AuditLogView of(AuditLogModel m) {
		return new AuditLogView(m.getId(), m.getDataHora(), m.getAcao(), m.getEntidade(),
				m.getEntidadeId(), m.getDescricao(), m.getUsuarioNome());
	}
}