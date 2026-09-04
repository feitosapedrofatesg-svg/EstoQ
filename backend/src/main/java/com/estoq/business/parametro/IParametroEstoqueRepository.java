package com.estoq.business.parametro;

import com.estoq.business.produto.ProdutoModel;
import com.estoq.core.repositories.IGenericRepository;

import java.util.Optional;
import java.util.UUID;

public interface IParametroEstoqueRepository extends IGenericRepository<ParametroEstoqueModel> {

	Optional<ParametroEstoqueModel> findByProdutoAndAtivoTrue(ProdutoModel produto);

	Optional<ParametroEstoqueModel> findByProduto_IdAndAtivoTrue(UUID produtoId);
}