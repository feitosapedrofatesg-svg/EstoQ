package com.estoq.business.movimentacao;

import com.estoq.core.repositories.IGenericRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface IMovimentacaoEstoqueRepository extends IGenericRepository<MovimentacaoEstoqueModel> {

	List<MovimentacaoEstoqueModel> findAllByProduto_IdAndDataHoraBetweenOrderByDataHoraAsc(
			UUID produtoId, LocalDateTime inicio, LocalDateTime fim);

	List<MovimentacaoEstoqueModel> findAllByDataHoraBetweenOrderByDataHoraAsc(
			LocalDateTime inicio, LocalDateTime fim);

	@Query("select count(e) from EntradaModel e where e.ativo = true "
			+ "and e.dataHora between :inicio and :fim "
			+ "and (e.valorTotalPago is null or e.valorTotalPago <= 0)")
	long contarEntradasSemValor(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

	@Query("select m from MovimentacaoEstoqueModel m where type(m) = DesperdicioModel "
			+ "and m.dataHora between :inicio and :fim order by m.dataHora desc")
	List<MovimentacaoEstoqueModel> findDesperdicios(@Param("inicio") LocalDateTime inicio,
			@Param("fim") LocalDateTime fim);

	@Query("select m from MovimentacaoEstoqueModel m where type(m) = DesperdicioModel "
			+ "order by m.dataHora desc")
	List<MovimentacaoEstoqueModel> findDesperdiciosTodos();

	@Query("select m from MovimentacaoEstoqueModel m where type(m) = ConsumoModel "
			+ "and m.dataHora between :inicio and :fim order by m.dataHora asc")
	List<MovimentacaoEstoqueModel> findConsumos(@Param("inicio") LocalDateTime inicio,
			@Param("fim") LocalDateTime fim);

	@Query("select m from MovimentacaoEstoqueModel m where m.produto.id = :produtoId "
			+ "order by m.dataHora desc")
	List<MovimentacaoEstoqueModel> findUltimasPorProduto(@Param("produtoId") UUID produtoId);
}