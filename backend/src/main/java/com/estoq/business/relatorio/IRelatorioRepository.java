package com.estoq.business.relatorio;

import com.estoq.core.repositories.IGenericRepository;

import java.time.LocalDate;
import java.util.List;

public interface IRelatorioRepository extends IGenericRepository<RelatorioModel> {

	List<RelatorioModel> findAllByTipoAndDataInicioGreaterThanEqualAndDataFimLessThanEqualAndAtivoTrueOrderByDataGeracaoDesc(
			TipoRelatorio tipo, LocalDate dataInicio, LocalDate dataFim);

	List<RelatorioModel> findAllByAtivoTrueOrderByDataGeracaoDesc();
}