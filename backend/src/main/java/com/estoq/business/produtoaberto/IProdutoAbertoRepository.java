package com.estoq.business.produtoaberto;

import com.estoq.core.repositories.IGenericRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface IProdutoAbertoRepository extends IGenericRepository<ProdutoAbertoModel> {

	List<ProdutoAbertoModel> findAllByFinalizadoFalseAndAtivoTrueOrderByDataAberturaAsc();

	List<ProdutoAbertoModel> findAllByProduto_IdAndFinalizadoFalseAndAtivoTrueOrderByDataAberturaAsc(UUID produtoId);

	@Query("select coalesce(sum(pa.quantidadeAberta - pa.quantidadeUtilizada), 0) from ProdutoAbertoModel pa "
			+ "where pa.produto.id = :produtoId and pa.ativo = true and pa.finalizado = false")
	BigDecimal sumQuantidadeRestanteByProdutoId(@Param("produtoId") UUID produtoId);
}