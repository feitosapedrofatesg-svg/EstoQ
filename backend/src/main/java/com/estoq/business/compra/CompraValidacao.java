package com.estoq.business.compra;

import com.estoq.core.exceptions.FieldValidationException;
import com.estoq.core.validations.GenericValidation;
import org.springframework.stereotype.Component;

@Component
public class CompraValidacao extends GenericValidation<CompraModel, ICompraRepository>
		implements ICompraValidation {

	@Override
	public void validateFields(CompraModel entity) {
		super.validateFields(entity);

		if (entity.getPeriodo() == null) {
			throw new FieldValidationException("periodoId", "O período é obrigatório.");
		}
		if (entity.getProduto() == null) {
			throw new FieldValidationException("produtoId", "O produto é obrigatório.");
		}
		if (entity.getQuantidade() == null || entity.getQuantidade().signum() <= 0) {
			throw new FieldValidationException("quantidade", "A quantidade deve ser maior que zero.");
		}
		if (entity.getPrecoUnitario() == null || entity.getPrecoUnitario().signum() < 0) {
			throw new FieldValidationException("precoUnitario", "O preço unitário deve ser maior ou igual a zero.");
		}
		if (entity.getDataCompra() == null) {
			throw new FieldValidationException("dataCompra", "A data da compra é obrigatória.");
		}
	}
}