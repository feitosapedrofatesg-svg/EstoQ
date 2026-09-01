package com.estoq.business.consumodiario;

import com.estoq.core.repositories.IGenericRepository;
import java.time.LocalDate;
import java.util.List;

public interface IConsumoDiarioRepository extends IGenericRepository<ConsumoDiarioModel> {

	List<ConsumoDiarioModel> findByDataAndAtivoTrueOrderByDataHoraRegistroAsc(LocalDate data);

	List<ConsumoDiarioModel> findAllByAtivoTrueOrderByDataHoraRegistroDesc();
}