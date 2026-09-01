package com.estoq.business.compra;

import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.services.GenericService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CompraService extends GenericService<CompraModel, ICompraRepository, ICompraValidation>
		implements ICompraService {

	@Override
	protected void beforeInsert(CompraModel entity) {
		validarPeriodoAberto(entity);
	}

	@Override
	protected void beforeUpdate(CompraModel entity) {
		validarPeriodoAberto(entity);
	}

	@Override
	protected void beforeDelete(CompraModel entity) {
		validarPeriodoAberto(entity);
	}

	private void validarPeriodoAberto(CompraModel entity) {
		if (entity.getPeriodo() != null
				&& entity.getPeriodo().getStatus() != null
				&& entity.getPeriodo().getStatus().name().equals("FECHADO")) {
			throw new BusinessException(
					"Não é possível alterar compras de um período fechado.", HttpStatus.CONFLICT);
		}
	}
}