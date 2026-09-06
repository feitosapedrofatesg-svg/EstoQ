package com.estoq.business.sessao;

import com.estoq.business.auditoria.AuditService;
import com.estoq.business.usuario.IUsuarioRepository;
import com.estoq.business.usuario.UsuarioModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessaoService {

	@Autowired
	private ISessaoRepository sessaoRepository;

	@Autowired
	private IUsuarioRepository usuarioRepository;

	@Autowired
	private AuditService auditService;

	@Value("${estoq.sessao.expiracao-horas:24}")
	private long expiracaoHoras;

	@Transactional
	public SessaoModel criar(UUID usuarioId, String origem) {
		SessaoModel sessao = new SessaoModel();
		sessao.setToken(UUID.randomUUID().toString());
		sessao.setUsuarioId(usuarioId);
		sessao.setCriadoEm(LocalDateTime.now());
		sessao.setExpiraEm(LocalDateTime.now().plusHours(expiracaoHoras));
		sessao.setOrigem(origem != null && origem.length() > 60 ? origem.substring(0, 60) : origem);
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

	@Transactional(readOnly = true)
	public List<SessaoView> listarAtivas() {
		LocalDateTime agora = LocalDateTime.now();
		return sessaoRepository.findAll().stream()
				.filter(s -> !s.getExpiraEm().isBefore(agora))
				.sorted(Comparator.comparing(SessaoModel::getCriadoEm).reversed())
				.map(s -> {
					String nome = usuarioRepository.findByIdAndAtivoTrue(s.getUsuarioId())
							.map(UsuarioModel::getNome).orElse("Usuário removido");
					return new SessaoView(s.getId(), s.getUsuarioId(), nome, s.getCriadoEm(),
							s.getExpiraEm(), s.getOrigem());
				})
				.toList();
	}

	@Transactional(readOnly = true)
	public long contarAtivas() {
		LocalDateTime agora = LocalDateTime.now();
		return sessaoRepository.findAll().stream()
				.filter(s -> !s.getExpiraEm().isBefore(agora)).count();
	}

	@Transactional
	public void revogar(UUID sessaoId, UsuarioModel admin) {
		sessaoRepository.findById(sessaoId).ifPresent(s -> {
			sessaoRepository.delete(s);
			auditService.registrar("REVOGACAO_SESSAO", "SESSAO", s.getId().toString(),
					"Sessão revogada", admin);
		});
	}
}