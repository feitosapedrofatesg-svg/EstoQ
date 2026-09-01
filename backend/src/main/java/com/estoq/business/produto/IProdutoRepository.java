package com.estoq.business.produto;

import com.estoq.core.repositories.IGenericRepository;
import java.util.Optional;

public interface IProdutoRepository extends IGenericRepository<ProdutoModel> {

	boolean existsByNome(String nome);

	Optional<ProdutoModel> findByNomeIgnoreCase(String nome);
}