package com.estoq.business.lote;

import com.estoq.core.repositories.IGenericRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ILoteRepository extends IGenericRepository<LoteModel> {

	@Query("select l from LoteModel l where l.produto.id = :produtoId and l.ativo = true "
			+ "order by l.dataValidade asc nulls last, l.dataHoraCriacao asc")
	List<LoteModel> findAllByProduto_IdAndAtivoTrue(@Param("produtoId") UUID produtoId);

	@Query("select coalesce(sum(l.quantidadeAtual), 0) from LoteModel l where l.produto.id = :produtoId and l.ativo = true")
	BigDecimal sumQuantidadeAtualByProdutoId(@Param("produtoId") UUID produtoId);

	List<LoteModel> findAllByAtivoTrueAndDataValidadeBeforeOrderByDataValidadeAsc(LocalDate data);

	List<LoteModel> findAllByAtivoTrueAndDataValidadeBetweenOrderByDataValidadeAsc(LocalDate inicio, LocalDate fim);

	@Query("select l from LoteModel l where l.ativo = true and l.quantidadeAtual > 0 order by l.dataValidade asc nulls last, l.dataHoraCriacao asc")
	List<LoteModel> findAllDisponiveis();

	@Query("select l from LoteModel l where l.ativo = true order by l.dataValidade asc nulls last, l.dataHoraCriacao asc")
	List<LoteModel> findAllAtivoOrdenado();
}