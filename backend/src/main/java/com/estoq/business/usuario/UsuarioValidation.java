package com.estoq.business.usuario;

import com.estoq.core.exceptions.FieldValidationException;
import com.estoq.core.validations.GenericValidation;
import org.springframework.stereotype.Component;

@Component
public class UsuarioValidation extends GenericValidation<UsuarioModel, IUsuarioRepository>
		implements IUsuarioValidation {

	@Override
	public void validateFields(UsuarioModel entity) {
		super.validateFields(entity);

		if (entity.getNome() == null || entity.getNome().isBlank()) {
			throw new FieldValidationException("nome", "O nome do usuário é obrigatório.");
		}
		if (entity.getPin() == null || entity.getPin().isBlank()) {
			throw new FieldValidationException("pin", "O PIN é obrigatório.");
		}
		if (entity.getPerfil() == null) {
			throw new FieldValidationException("perfil", "O perfil (ADMIN ou COZINHA) é obrigatório.");
		}
	}

	@Override
	public void validateFieldsInsert(UsuarioModel entity) {
		super.validateFieldsInsert(entity);
		if (!entity.getPin().matches("\\d{6}")) {
			throw new FieldValidationException("pin", "O PIN deve conter exatamente 6 dígitos.");
		}
	}
}