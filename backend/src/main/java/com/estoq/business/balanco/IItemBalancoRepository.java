package com.estoq.business.balanco;

import com.estoq.core.repositories.IGenericRepository;

import java.util.List;
import java.util.UUID;

public interface IItemBalancoRepository extends IGenericRepository<ItemBalancoModel> {

	List<ItemBalancoModel> findAllByBalanco_IdAndAtivoTrue(UUID balancoId);
}