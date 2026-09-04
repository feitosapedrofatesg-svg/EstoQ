package com.estoq.business.categoria;

import com.estoq.core.exceptions.FieldValidationException;
import com.estoq.core.validations.GenericValidation;
import org.springframework.stereotype.Component;

@Component
public class CategoriaValidation extends GenericValidation<CategoriaModel, ICategoriaRepository>
		implements ICategoriaValidation {

	@Override
	public void validateFields(CategoriaModel entity) {
		super.validateFields(entity);
		if (entity.getNome() == null || entity.getNome().isBlank()) {
			throw new FieldValidationException("nome", "Nome da categoria é obrigatório.");
		}
	}

	@Override
	public void validateFieldsInsert(CategoriaModel entity) {
		super.validateFieldsInsert(entity);
		if (repository.existsByNomeIgnoreCase(entity.getNome().trim())) {
			throw new FieldValidationException("nome", "Já existe uma categoria com esse nome.");
		}
	}
}