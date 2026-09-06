package com.estoq.business.sessao;

import com.estoq.business.auditoria.AuditService;
import com.estoq.business.usuario.IUsuarioRepository;
import com.estoq.business.usuario.UsuarioModel;
import com.estoq.business.usuario.UsuarioView;
import com.estoq.core.exceptions.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private static final int TENTATIVAS_MAXIMAS = 5;
	private static final long BLOQUEIO_MINUTOS = 15;

	private final IUsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final SessaoService sessaoService;
	private final AuditService auditService;

	public AuthController(IUsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
			SessaoService sessaoService, AuditService auditService) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.sessaoService = sessaoService;
		this.auditService = auditService;
	}

	@PostMapping("/login")
	public LoginResponse login(@RequestBody LoginRequest request, HttpServletRequest http) {
		if (request == null || request.getPin() == null || request.getPin().isBlank()) {
			throw new BusinessException("Informe o PIN de 6 dígitos.", HttpStatus.BAD_REQUEST);
		}
		UsuarioModel usuario = usuarioRepository.findAllByAtivoTrue().stream()
				.filter(u -> passwordEncoder.matches(request.getPin(), u.getPin()))
				.findFirst()
				.orElse(null);
		if (usuario == null) {
			registrarFalha();
			throw new BusinessException("PIN inválido.", HttpStatus.UNAUTHORIZED);
		}
		if (bloqueado(usuario)) {
			long minutos = java.time.Duration.between(LocalDateTime.now(), usuario.getBloqueadoAte()).toMinutes();
			throw new BusinessException(
					"Muitas tentativas de login. Aguarde " + Math.max(1, minutos)
							+ " min e tente novamente.",
					HttpStatus.UNAUTHORIZED);
		}
		desbloquear(usuario);
		SessaoModel sessao = sessaoService.criar(usuario.getId(), http.getRemoteAddr());
		auditService.registrar("LOGIN", "SESSAO", sessao.getId().toString(),
				"Login realizado por " + usuario.getNome(), usuario);
		return new LoginResponse(sessao.getToken(), usuario.getNome(), usuario.getPerfil());
	}

	@PostMapping("/logout")
	public Map<String, String> logout(HttpServletRequest request,
			@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		sessaoService.encerrar(bearerToken(request.getHeader("Authorization")));
		auditService.registrar("LOGOUT", "SESSAO", null, "Logout de " + usuario.getNome(), usuario);
		return Map.of("message", "Sessão encerrada.");
	}

	@GetMapping("/me")
	public UsuarioView me(@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		return UsuarioView.of(usuario);
	}

	@GetMapping("/sessoes")
	public List<SessaoView> sessoesAtivas() {
		return sessaoService.listarAtivas();
	}

	@DeleteMapping("/sessoes/{id}")
	public Map<String, String> revogar(@PathVariable UUID id,
			@RequestAttribute("usuario_logado") UsuarioModel admin) {
		sessaoService.revogar(id, admin);
		return Map.of("message", "Sessão revogada.");
	}

	private boolean bloqueado(UsuarioModel u) {
		return u.getBloqueadoAte() != null && u.getBloqueadoAte().isAfter(LocalDateTime.now());
	}

	private void desbloquear(UsuarioModel u) {
		u.setTentativasFalhas(0);
		u.setBloqueadoAte(null);
		usuarioRepository.save(u);
	}

	/**
	 * A tentativa com PIN que não corresponde a nenhum usuário não pode ser
	 * atribuída com certeza, então a falha é contabilizada em todos os usuários
	 * ativos e desbloqueados.
	 */
	private void registrarFalha() {
		for (UsuarioModel u : usuarioRepository.findAllByAtivoTrue()) {
			if (bloqueado(u)) {
				continue;
			}
			int tentativas = (u.getTentativasFalhas() == null ? 0 : u.getTentativasFalhas()) + 1;
			if (tentativas >= TENTATIVAS_MAXIMAS) {
				u.setBloqueadoAte(LocalDateTime.now().plusMinutes(BLOQUEIO_MINUTOS));
				u.setTentativasFalhas(0);
			} else {
				u.setTentativasFalhas(tentativas);
			}
			usuarioRepository.save(u);
		}
	}

	private String bearerToken(String authorization) {
		if (authorization != null && authorization.startsWith("Bearer ")) {
			return authorization.substring(7).trim();
		}
		return null;
	}
}