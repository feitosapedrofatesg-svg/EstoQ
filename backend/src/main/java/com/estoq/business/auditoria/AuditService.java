package com.estoq.business.auditoria;

import com.estoq.business.usuario.UsuarioModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuditService {

	@Autowired
	private IAuditLogRepository repository;

	public static final String USUARIO_SISTEMA = "Sistema";

	/**
	 * Registra um evento de auditoria. Falhas de auditoria nunca derrubam a
	 * operação de negócio principal.
	 */
	@Transactional
	public void registrar(String acao, String entidade, String entidadeId, String descricao,
			UsuarioModel usuario) {
		registrar(acao, entidade, entidadeId, descricao,
				usuario != null ? usuario.getNome() : USUARIO_SISTEMA);
	}

	@Transactional
	public void registrar(String acao, String entidade, String entidadeId, String descricao) {
		registrar(acao, entidade, entidadeId, descricao, USUARIO_SISTEMA);
	}

	private void registrar(String acao, String entidade, String entidadeId, String descricao,
			String usuarioNome) {
		try {
			AuditLogModel log = new AuditLogModel();
			log.setDataHora(LocalDateTime.now());
			log.setAcao(truncate(acao, 40));
			log.setEntidade(truncate(entidade, 60));
			log.setEntidadeId(truncate(entidadeId, 60));
			log.setDescricao(descricao == null ? "" : truncate(descricao, 2000));
			log.setUsuarioNome(usuarioNome == null ? USUARIO_SISTEMA : truncate(usuarioNome, 120));
			repository.save(log);
		} catch (Exception ignored) {
			// a auditoria não pode interromper a operação de negócio
		}
	}

	@Transactional(readOnly = true)
	public List<AuditLogView> ultimosEventos(int limite) {
		return repository.findTop20ByAtivoTrueOrderByDataHoraDesc().stream()
				.limit(Math.max(1, Math.min(limite, 20)))
				.map(AuditLogView::of)
				.collect(Collectors.toList());
	}

	private String truncate(String s, int max) {
		if (s == null) {
			return null;
		}
		return s.length() <= max ? s : s.substring(0, max);
	}
}