package com.estoq.business.estoque;

import com.estoq.core.exceptions.FieldValidationException;
import com.estoq.core.helpers.NumeroUtil;
import com.estoq.core.validations.GenericValidation;
import org.springframework.stereotype.Component;

@Component
public class EstoquePeriodoValidacao extends GenericValidation<EstoquePeriodoModel, IEstoquePeriodoRepository>
		implements IEstoquePeriodoValidation {

	@Override
	public void validateFields(EstoquePeriodoModel entity) {
		super.validateFields(entity);

		if (entity.getPeriodo() == null) {
			throw new FieldValidationException("periodoId", "O período é obrigatório.");
		}
		if (entity.getProduto() == null) {
			throw new FieldValidationException("produtoId", "O produto é obrigatório.");
		}
		if (NumeroUtil.negativo(entity.getQuantidadeInicial())) {
			throw new FieldValidationException("quantidadeInicial", "Estoque inicial não pode ser negativo.");
		}
		if (NumeroUtil.negativo(entity.getQuantidadeFinal())) {
			throw new FieldValidationException("quantidadeFinal", "Estoque final não pode ser negativo.");
		}
	}

	@Override
	public void validateFieldsInsert(EstoquePeriodoModel entity) {
		super.validateFieldsInsert(entity);
		if (repository.existsByPeriodoIdAndProdutoId(entity.getPeriodo().getId(), entity.getProduto().getId())) {
			throw new FieldValidationException("produtoId",
					"Já existe lançamento de estoque para este produto no período.");
		}
	}
}