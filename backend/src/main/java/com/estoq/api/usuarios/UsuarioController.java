package com.estoq.api.usuarios;

import com.estoq.api.dto.PinRequest;
import com.estoq.api.dto.UsuarioView;
import com.estoq.business.usuario.IUsuarioRepository;
import com.estoq.business.usuario.UsuarioModel;
import com.estoq.business.usuario.UsuarioService;
import com.estoq.core.exceptions.FieldValidationException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

	private final IUsuarioRepository usuarioRepository;
	private final UsuarioService usuarioService;

	@GetMapping
	public List<UsuarioView> listar() {
		return usuarioRepository.findAllByAtivoTrue().stream().map(UsuarioView::of).toList();
	}

	@PutMapping("/{id}/pin")
	public UsuarioView alterarPin(@PathVariable UUID id, @RequestBody PinRequest request) {
		if (request == null || request.getPin() == null || !request.getPin().matches("\\d{6}")) {
			throw new FieldValidationException("pin", "O PIN deve conter exatamente 6 dígitos.");
		}
		UsuarioModel usuario = usuarioService.alterarPin(id, request.getPin());
		return UsuarioView.of(usuario);
	}
}