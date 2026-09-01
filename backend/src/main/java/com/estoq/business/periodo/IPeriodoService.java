package com.estoq.business.periodo;

import com.estoq.core.services.IGenericService;
import java.util.List;
import java.util.UUID;

public interface IPeriodoService extends IGenericService<PeriodoModel, IPeriodoRepository, IPeriodoValidation> {

	List<PeriodoModel> listarOrdenados();

	PeriodoModel fechar(UUID id);
}