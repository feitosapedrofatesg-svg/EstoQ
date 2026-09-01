package com.estoq.business.estoque;

import com.estoq.business.periodo.IPeriodoRepository;
import com.estoq.business.periodo.PeriodoModel;
import com.estoq.business.periodo.PeriodoStatus;
import com.estoq.business.produto.IProdutoRepository;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.helpers.NumeroUtil;
import com.estoq.core.services.GenericService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstoquePeriodoService
		extends GenericService<EstoquePeriodoModel, IEstoquePeriodoRepository, IEstoquePeriodoValidation>
		implements IEstoquePeriodoService {

	@Autowired
	private IPeriodoRepository periodoRepository;

	@Autowired
	private IProdutoRepository produtoRepository;

	@Override
	protected void beforeInsert(EstoquePeriodoModel entity) {
		if (entity.getQuantidadeInicial() == null) {
			entity.setQuantidadeInicial(BigDecimal.ZERO);
		}
		if (entity.getQuantidadeFinal() == null) {
			entity.setQuantidadeFinal(BigDecimal.ZERO);
		}
		validarPeriodoAberto(entity);
		preencherInicialAutomatico(entity);
	}

	@Override
	protected void beforeUpdate(EstoquePeriodoModel entity) {
		validarPeriodoAberto(entity);
	}

	@Override
	protected void beforeDelete(EstoquePeriodoModel entity) {
		validarPeriodoAberto(entity);
	}

	@Override
	@Transactional(readOnly = true)
	public List<EstoquePeriodoModel> listarPorPeriodo(UUID periodoId) {
		PeriodoModel periodo = periodoRepository.findByIdAndAtivoTrue(periodoId).orElseThrow(() ->
				new BusinessException("Período não encontrado.", HttpStatus.NOT_FOUND));
		return repository.findAllByPeriodo(periodo);
	}

	@Override
	@Transactional
	public int prepararPeriodo(UUID periodoId) {
		PeriodoModel periodo = periodoRepository.findByIdAndAtivoTrue(periodoId).orElseThrow(() ->
				new BusinessException("Período não encontrado.", HttpStatus.NOT_FOUND));

		PeriodoModel anterior = periodoRepository
				.findFirstByDataInicioBeforeAndAtivoTrueOrderByDataInicioDesc(periodo.getDataInicio())
				.orElse(null);

		List<ProdutoModel> produtos = produtoRepository.findAllByAtivoTrue(PageRequest.of(0, Integer.MAX_VALUE))
				.getContent();

		int criados = 0;
		for (ProdutoModel produto : produtos) {
			if (repository.existsByPeriodoIdAndProdutoId(periodoId, produto.getId())) {
				continue;
			}
			EstoquePeriodoModel ep = new EstoquePeriodoModel();
			ep.setPeriodo(periodo);
			ep.setProduto(produto);
			ep.setQuantidadeInicial(BigDecimal.ZERO);
			ep.setQuantidadeFinal(BigDecimal.ZERO);

			if (anterior != null) {
				repository.findByPeriodoIdAndProdutoId(anterior.getId(), produto.getId())
						.ifPresent(prev -> ep.setQuantidadeInicial(
								NumeroUtil.s(prev.getQuantidadeFinal())));
			}
			repository.save(ep);
			criados++;
		}
		return criados;
	}

	private void validarPeriodoAberto(EstoquePeriodoModel entity) {
		if (entity.getPeriodo() != null && entity.getPeriodo().getStatus() == PeriodoStatus.FECHADO) {
			throw new BusinessException("Não é possível alterar o estoque de um período fechado.", HttpStatus.CONFLICT);
		}
	}

	private void preencherInicialAutomatico(EstoquePeriodoModel entity) {
		if (entity.getQuantidadeInicial() != null && entity.getQuantidadeInicial().signum() > 0) {
			return;
		}
		PeriodoModel anterior = periodoRepository
				.findFirstByDataInicioBeforeAndAtivoTrueOrderByDataInicioDesc(entity.getPeriodo().getDataInicio())
				.orElse(null);
		if (anterior != null) {
			repository.findByPeriodoIdAndProdutoId(anterior.getId(), entity.getProduto().getId())
					.filter(prev -> NumeroUtil.s(prev.getQuantidadeFinal()).signum() > 0)
					.ifPresent(prev -> entity.setQuantidadeInicial(prev.getQuantidadeFinal()));
		}
	}
}