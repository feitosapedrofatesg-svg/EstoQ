package com.estoq.business.periodo;

import com.estoq.core.repositories.IGenericRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface IPeriodoRepository extends IGenericRepository<PeriodoModel> {

	List<PeriodoModel> findAllByAtivoTrueOrderByDataInicioAsc();

	Optional<PeriodoModel> findFirstByDataInicioBeforeAndAtivoTrueOrderByDataInicioDesc(LocalDate data);

	List<PeriodoModel> findAllByAtivoTrueAndDataInicioBetweenOrderByDataInicioAsc(LocalDate inicio, LocalDate fim);
}