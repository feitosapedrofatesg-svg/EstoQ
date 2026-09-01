package com.estoq.business.sessao;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessaoService {

	@Autowired
	private ISessaoRepository sessaoRepository;

	@Value("${estoq.sessao.expiracao-horas:24}")
	private long expiracaoHoras;

	@Transactional
	public SessaoModel criar(UUID usuarioId) {
		sessaoRepository.deleteByUsuarioId(usuarioId);
		SessaoModel sessao = new SessaoModel();
		sessao.setToken(UUID.randomUUID().toString());
		sessao.setUsuarioId(usuarioId);
		sessao.setCriadoEm(LocalDateTime.now());
		sessao.setExpiraEm(LocalDateTime.now().plusHours(expiracaoHoras));
		return sessaoRepository.save(sessao);
	}

	@Transactional
	public Optional<SessaoModel> validar(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		Optional<SessaoModel> sessao = sessaoRepository.findByToken(token);
		if (sessao.isPresent() && sessao.get().getExpiraEm().isBefore(LocalDateTime.now())) {
			sessaoRepository.delete(sessao.get());
			return Optional.empty();
		}
		return sessao;
	}

	@Transactional
	public void encerrar(String token) {
		if (token != null && !token.isBlank()) {
			sessaoRepository.findByToken(token).ifPresent(sessaoRepository::delete);
		}
	}
}