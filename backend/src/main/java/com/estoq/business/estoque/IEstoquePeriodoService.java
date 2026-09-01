package com.estoq.business.estoque;

import com.estoq.core.services.IGenericService;
import java.util.List;
import java.util.UUID;

public interface IEstoquePeriodoService extends IGenericService<EstoquePeriodoModel, IEstoquePeriodoRepository, IEstoquePeriodoValidation> {

	List<EstoquePeriodoModel> listarPorPeriodo(UUID periodoId);

	int prepararPeriodo(UUID periodoId);
}