package com.estoq.business.estoque;

import com.estoq.business.periodo.PeriodoModel;
import com.estoq.core.repositories.IGenericRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IEstoquePeriodoRepository extends IGenericRepository<EstoquePeriodoModel> {

	Optional<EstoquePeriodoModel> findByPeriodoIdAndProdutoId(UUID periodoId, UUID produtoId);

	List<EstoquePeriodoModel> findAllByPeriodo(PeriodoModel periodo);

	List<EstoquePeriodoModel> findAllByPeriodoAndAtivoTrue(PeriodoModel periodo);

	boolean existsByPeriodoIdAndProdutoId(UUID periodoId, UUID produtoId);
}