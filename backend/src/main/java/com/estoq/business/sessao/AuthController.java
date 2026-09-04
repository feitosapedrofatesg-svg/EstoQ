package com.estoq.business.sessao;

import com.estoq.business.sessao.LoginRequest;
import com.estoq.business.sessao.LoginResponse;
import com.estoq.business.usuario.UsuarioView;
import com.estoq.business.sessao.SessaoModel;
import com.estoq.business.sessao.SessaoService;
import com.estoq.business.usuario.IUsuarioRepository;
import com.estoq.business.usuario.UsuarioModel;
import com.estoq.core.exceptions.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final IUsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final SessaoService sessaoService;

	@PostMapping("/login")
	public LoginResponse login(@RequestBody LoginRequest request) {
		if (request == null || request.getPin() == null || request.getPin().isBlank()) {
			throw new BusinessException("Informe o PIN de 6 dígitos.", HttpStatus.BAD_REQUEST);
		}
		UsuarioModel usuario = usuarioRepository.findAllByAtivoTrue().stream()
				.filter(u -> passwordEncoder.matches(request.getPin(), u.getPin()))
				.findFirst()
				.orElseThrow(() -> new BusinessException("PIN inválido.", HttpStatus.UNAUTHORIZED));
		SessaoModel sessao = sessaoService.criar(usuario.getId());
		return new LoginResponse(sessao.getToken(), usuario.getNome(), usuario.getPerfil());
	}

	@PostMapping("/logout")
	public Map<String, String> logout(HttpServletRequest request) {
		sessaoService.encerrar(bearerToken(request.getHeader("Authorization")));
		return Map.of("message", "Sessão encerrada.");
	}

	@GetMapping("/me")
	public UsuarioView me(@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		return UsuarioView.of(usuario);
	}

	private String bearerToken(String authorization) {
		if (authorization != null && authorization.startsWith("Bearer ")) {
			return authorization.substring(7).trim();
		}
		return null;
	}
}