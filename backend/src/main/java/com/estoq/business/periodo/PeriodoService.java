package com.estoq.business.periodo;

import com.estoq.core.exceptions.RuleValidationException;
import com.estoq.core.services.GenericService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PeriodoService extends GenericService<PeriodoModel, IPeriodoRepository, IPeriodoValidation>
		implements IPeriodoService {

	@Override
	protected void beforeInsert(PeriodoModel entity) {
		entity.setNome(entity.getNome().trim());
		if (entity.getStatus() == null) {
			entity.setStatus(PeriodoStatus.ABERTO);
		}
	}

	@Override
	public List<PeriodoModel> listarOrdenados() {
		return repository.findAllByAtivoTrueOrderByDataInicioAsc();
	}

	@Override
	public PeriodoModel fechar(UUID id) {
		PeriodoModel periodo = findByIdActive(id);
		if (periodo.getStatus() == PeriodoStatus.FECHADO) {
			throw new RuleValidationException("periodo-fechado", "O período já está fechado.");
		}
		periodo.setStatus(PeriodoStatus.FECHADO);
		return repository.save(periodo);
	}
}