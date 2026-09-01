package com.estoq.business.usuario;

import com.estoq.core.services.GenericService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService extends GenericService<UsuarioModel, IUsuarioRepository, IUsuarioValidation>
		implements IUsuarioService {

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Override
	protected void beforeInsert(UsuarioModel entity) {
		entity.setNome(entity.getNome().trim());
		entity.setPin(passwordEncoder.encode(entity.getPin()));
	}

	@Override
	@Transactional
	public UsuarioModel alterarPin(UUID id, String pin) {
		UsuarioModel usuario = findByIdActive(id);
		usuario.setPin(passwordEncoder.encode(pin));
		return repository.save(usuario);
	}
}