package com.estoq.business.produto;

import com.estoq.core.exceptions.FieldValidationException;
import com.estoq.core.validations.GenericValidation;
import org.springframework.stereotype.Component;

@Component
public class ProdutoValidation extends GenericValidation<ProdutoModel, IProdutoRepository>
		implements IProdutoValidation {

	@Override
	public void validateFields(ProdutoModel entity) {
		super.validateFields(entity);

		if (entity.getNome() == null || entity.getNome().isBlank()) {
			throw new FieldValidationException("nome", "Nome do produto é obrigatório.");
		}
		if (entity.getUnidadeMedida() == null) {
			throw new FieldValidationException("unidadeMedida", "Unidade de medida é obrigatória.");
		}
		if (entity.getEstoqueMinimo() == null || entity.getEstoqueMinimo().signum() < 0) {
			throw new FieldValidationException("estoqueMinimo", "Estoque mínimo deve ser maior ou igual a zero.");
		}
	}

	@Override
	public void validateFieldsInsert(ProdutoModel entity) {
		super.validateFieldsInsert(entity);
		if (repository.existsByNome(entity.getNome().trim())) {
			throw new FieldValidationException("nome", "Já existe um produto com esse nome.");
		}
	}

	@Override
	public void validateFieldsUpdate(ProdutoModel entity) {
		super.validateFieldsUpdate(entity);
		repository.findByNomeIgnoreCase(entity.getNome().trim()).ifPresent(existente -> {
			if (!existente.getId().equals(entity.getId())) {
				throw new FieldValidationException("nome", "Já existe um produto com esse nome.");
			}
		});
	}
}