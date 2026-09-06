package com.estoq.business.usuario;

import com.estoq.business.auditoria.AuditService;
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

	@Autowired
	private AuditService auditService;

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
		usuario.setTentativasFalhas(0);
		usuario.setBloqueadoAte(null);
		UsuarioModel salvo = repository.save(usuario);
		auditService.registrar("ALTERACAO", "USUARIO", id.toString(),
				"PIN alterado para o usuário " + usuario.getNome(), null);
		return salvo;
	}
}