package com.estoq.business.compra;

import com.estoq.business.periodo.PeriodoModel;
import com.estoq.core.repositories.IGenericRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ICompraRepository extends IGenericRepository<CompraModel> {

	List<CompraModel> findAllByPeriodoAndAtivoTrueOrderByDataCompraAsc(PeriodoModel periodo);

	List<CompraModel> findAllByPeriodoIdAndProdutoIdAndAtivoTrue(UUID periodoId, UUID produtoId);

	Optional<CompraModel> findFirstByProdutoIdAndDataCompraBeforeAndAtivoTrueOrderByDataCompraDesc(
			UUID produtoId, LocalDate data);

	List<CompraModel> findAllByPeriodoIdInAndAtivoTrue(List<UUID> periodoIds);

	long countByPeriodoIdInAndAtivoTrue(List<UUID> periodoIds);
}