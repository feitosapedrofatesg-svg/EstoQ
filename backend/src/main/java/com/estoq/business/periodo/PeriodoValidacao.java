package com.estoq.business.periodo;

import com.estoq.core.exceptions.FieldValidationException;
import com.estoq.core.validations.GenericValidation;
import org.springframework.stereotype.Component;

@Component
public class PeriodoValidacao extends GenericValidation<PeriodoModel, IPeriodoRepository>
		implements IPeriodoValidation {

	@Override
	public void validateFields(PeriodoModel entity) {
		super.validateFields(entity);

		if (entity.getNome() == null || entity.getNome().isBlank()) {
			throw new FieldValidationException("nome", "Nome do período é obrigatório.");
		}
		if (entity.getDataInicio() == null) {
			throw new FieldValidationException("dataInicio", "Data inicial é obrigatória.");
		}
		if (entity.getDataFim() == null) {
			throw new FieldValidationException("dataFim", "Data final é obrigatória.");
		}
		if (entity.getDataFim().isBefore(entity.getDataInicio())) {
			throw new FieldValidationException("dataFim", "A data final deve ser maior ou igual à data inicial.");
		}
		if (entity.getVendas() != null && entity.getVendas().signum() < 0) {
			throw new FieldValidationException("vendas", "O valor de vendas não pode ser negativo.");
		}
	}

	@Override
	public void validateFieldsInsert(PeriodoModel entity) {
		super.validateFieldsInsert(entity);
		if (entity.getStatus() == null) {
			entity.setStatus(PeriodoStatus.ABERTO);
		}
	}
}