package com.estoq.business.balanco;

import com.estoq.core.repositories.IGenericRepository;

import java.util.List;
import java.util.Optional;

public interface IBalancoRepository extends IGenericRepository<BalancoModel> {

	List<BalancoModel> findAllByAtivoTrueOrderByDataHoraDesc();

	List<BalancoModel> findAllByStatusInAndAtivoTrueOrderByDataHoraDesc(List<StatusBalanco> status);

	long countByStatusAndAtivoTrue(StatusBalanco status);

	Optional<BalancoModel> findFirstByStatusAndAtivoTrueOrderByDataHoraDesc(StatusBalanco status);
}