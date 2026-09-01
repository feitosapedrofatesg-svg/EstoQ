package com.estoq.business.usuario;

import com.estoq.core.services.IGenericService;
import java.util.UUID;

public interface IUsuarioService extends IGenericService<UsuarioModel, IUsuarioRepository, IUsuarioValidation> {

	UsuarioModel alterarPin(UUID id, String pin);
}