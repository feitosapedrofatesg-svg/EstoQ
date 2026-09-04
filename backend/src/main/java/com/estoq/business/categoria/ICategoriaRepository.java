package com.estoq.business.categoria;

import com.estoq.core.repositories.IGenericRepository;
import java.util.Optional;

public interface ICategoriaRepository extends IGenericRepository<CategoriaModel> {

	boolean existsByNomeIgnoreCase(String nome);

	Optional<CategoriaModel> findByNomeIgnoreCase(String nome);
}