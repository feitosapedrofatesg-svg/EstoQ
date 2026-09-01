package com.estoq.conf;

import com.estoq.business.sessao.SessaoModel;
import com.estoq.business.sessao.SessaoService;
import com.estoq.business.usuario.IUsuarioRepository;
import com.estoq.business.usuario.Perfil;
import com.estoq.business.usuario.UsuarioModel;
import com.estoq.core.exceptions.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

	private static final List<String> PUBLICOS = List.of("/api/auth/login");

	@Autowired
	private SessaoService sessaoService;

	@Autowired
	private IUsuarioRepository usuarioRepository;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String path = request.getRequestURI();
		if (PUBLICOS.contains(path)) {
			return true;
		}

		String token = bearerToken(request.getHeader("Authorization"));
		if (token == null) {
			throw new BusinessException(
					"Autenticação necessária. Informe o token no header Authorization.", HttpStatus.UNAUTHORIZED);
		}

		SessaoModel sessao = sessaoService.validar(token)
				.orElseThrow(() -> new BusinessException(
						"Sessão inválida ou expirada. Faça login novamente.", HttpStatus.UNAUTHORIZED));

		UsuarioModel usuario = usuarioRepository.findByIdAndAtivoTrue(sessao.getUsuarioId())
				.orElseThrow(() -> new BusinessException("Usuário não encontrado.", HttpStatus.UNAUTHORIZED));

		if (usuario.getPerfil() == Perfil.COZINHA && !cozinhaPode(request.getMethod(), path)) {
			throw new BusinessException("Acesso restrito ao administrador.", HttpStatus.FORBIDDEN);
		}

		request.setAttribute("usuario_logado", usuario);
		return true;
	}

	private boolean cozinhaPode(String method, String path) {
		if (path.startsWith("/api/consumo-diario")) {
			return true;
		}
		if (path.startsWith("/api/produtos")) {
			return "GET".equals(method);
		}
		return path.equals("/api/auth/me") || path.equals("/api/auth/logout");
	}

	private String bearerToken(String authorization) {
		if (authorization != null && authorization.startsWith("Bearer ")) {
			return authorization.substring(7).trim();
		}
		return null;
	}
}