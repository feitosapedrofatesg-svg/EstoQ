package com.estoq.business.parametro;

import com.estoq.business.movimentacao.ConsumoModel;
import com.estoq.business.movimentacao.IMovimentacaoEstoqueRepository;
import com.estoq.business.movimentacao.MovimentacaoEstoqueModel;
import com.estoq.business.produto.IProdutoRepository;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.helpers.NumeroUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ParametroEstoqueService {

	private static final int DIAS_PADRAO_ANALISE = 7;
	private static final int TEMPO_PADRAO_REPOSICAO = 3;

	@Autowired
	private IParametroEstoqueRepository parametroRepository;

	@Autowired
	private IProdutoRepository produtoRepository;

	@Autowired
	private IMovimentacaoEstoqueRepository movRepository;

	@Autowired
	private com.estoq.business.movimentacao.MovimentacaoService movimentacaoService;

	@Transactional(readOnly = true)
	public ParametroEstoqueModel getPorProduto(UUID produtoId) {
		buscarProduto(produtoId);
		return parametroRepository.findByProduto_IdAndAtivoTrue(produtoId).orElseGet(() -> {
			ParametroEstoqueModel p = new ParametroEstoqueModel();
			p.setTempoReposicaoDias(TEMPO_PADRAO_REPOSICAO);
			p.setPeriodoAnaliseDias(DIAS_PADRAO_ANALISE);
			p.setEstoqueMinimo(BigDecimal.ZERO);
			return p;
		});
	}

	@Transactional
	public ParametroEstoqueModel atualizar(UUID produtoId, Integer tempoReposicaoDias, Integer periodoAnaliseDias,
			BigDecimal estoqueMinimo) {
		ProdutoModel produto = buscarProduto(produtoId);
		ParametroEstoqueModel par = parametroRepository.findByProdutoAndAtivoTrue(produto).orElseGet(() -> {
			ParametroEstoqueModel novo = new ParametroEstoqueModel();
			novo.setProduto(produto);
			return novo;
		});
		par.setTempoReposicaoDias(tempoReposicaoDias == null ? TEMPO_PADRAO_REPOSICAO : tempoReposicaoDias);
		par.setPeriodoAnaliseDias(periodoAnaliseDias == null ? DIAS_PADRAO_ANALISE : periodoAnaliseDias);
		par.setEstoqueMinimo(estoqueMinimo == null ? BigDecimal.ZERO : estoqueMinimo);
		par.setDataAtualizacao(LocalDateTime.now());
		return parametroRepository.save(par);
	}

	@Transactional
	public ParametroEstoqueModel recalcular(UUID produtoId) {
		ProdutoModel produto = buscarProduto(produtoId);
		ParametroEstoqueModel par = atualizar(produtoId, null, null, null);

		int dias = Math.max(par.getPeriodoAnaliseDias(), 1);
		LocalDate fim = LocalDate.now();
		LocalDate inicio = fim.minusDays(dias);

		BigDecimal consumoPeriodo = BigDecimal.ZERO;
		for (MovimentacaoEstoqueModel m : movRepository.findConsumos(inicio.atStartOfDay(),
				fim.plusDays(1).atStartOfDay())) {
			if (m.getProduto() != null && m.getProduto().getId().equals(produtoId)
					&& m instanceof ConsumoModel) {
				consumoPeriodo = consumoPeriodo.add(NumeroUtil.s(m.getQuantidade()));
			}
		}

		BigDecimal consumoMedioDiario = NumeroUtil.divide(consumoPeriodo, BigDecimal.valueOf(dias), 3);
		BigDecimal tempo = BigDecimal.valueOf(Math.max(par.getTempoReposicaoDias(), 1));
		BigDecimal minimo = NumeroUtil.multiplica(consumoMedioDiario, tempo);

		par.setConsumoMedioDiario(consumoMedioDiario);
		par.setEstoqueMinimo(minimo);
		par.setEstoqueMedio(minimo.multiply(new BigDecimal("1.5")).setScale(3, RoundingMode.HALF_UP));
		par.setEstoqueMaximo(minimo.multiply(new BigDecimal("2")).setScale(3, RoundingMode.HALF_UP));
		par.setDataAtualizacao(LocalDateTime.now());
		return parametroRepository.save(par);
	}

	@Transactional
	public int recalcularTodos() {
		int qtd = 0;
		for (ProdutoModel p : produtoRepository.findAllByAtivoTrue(PageRequest.of(0, Integer.MAX_VALUE)).getContent()) {
			recalcular(p.getId());
			qtd++;
		}
		return qtd;
	}

	@Transactional(readOnly = true)
	public BigDecimal obterSaldoProduto(UUID produtoId) {
		return movimentacaoService.obterSaldo(produtoId);
	}

	@Transactional(readOnly = true)
	public long contarProdutosComEstoqueBaixo() {
		long count = 0;
		for (ProdutoModel p : produtoRepository.findAllByAtivoTrue(PageRequest.of(0, Integer.MAX_VALUE)).getContent()) {
			ParametroEstoqueModel par = parametroRepository.findByProduto_IdAndAtivoTrue(p.getId()).orElse(null);
			BigDecimal minimo = NumeroUtil.s(par != null ? par.getEstoqueMinimo() : null);
			if (minimo.signum() > 0 && movimentacaoService.obterSaldo(p.getId()).compareTo(minimo) < 0) {
				count++;
			}
		}
		return count;
	}

	private ProdutoModel buscarProduto(UUID produtoId) {
		return produtoRepository.findByIdAndAtivoTrue(produtoId)
				.orElseThrow(() -> new BusinessException("Produto não encontrado.", HttpStatus.NOT_FOUND));
	}
}