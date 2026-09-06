package com.estoq.business.produto;

import com.estoq.business.auditoria.AuditService;
import com.estoq.business.lote.ILoteRepository;
import com.estoq.business.parametro.IParametroEstoqueRepository;
import com.estoq.business.parametro.ParametroEstoqueModel;
import com.estoq.business.produtoaberto.IProdutoAbertoRepository;
import com.estoq.core.helpers.NumeroUtil;
import com.estoq.core.services.GenericService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ProdutoService extends GenericService<ProdutoModel, IProdutoRepository, IProdutoValidation> {

	@Autowired
	private ILoteRepository loteRepository;

	@Autowired
	private IProdutoAbertoRepository produtoAbertoRepository;

	@Autowired
	private IParametroEstoqueRepository parametroRepository;

	@Autowired
	private AuditService auditService;

	@Override
	protected void beforeInsert(ProdutoModel entity) {
		normalizar(entity);
	}

	@Override
	protected void beforeUpdate(ProdutoModel entity) {
		normalizar(entity);
	}

	private void normalizar(ProdutoModel entity) {
		entity.setNome(entity.getNome().trim());
		if (entity.getUnidadeMedida() == null) {
			entity.setUnidadeMedida(UnidadeMedida.UN);
		}
		if (entity.getEstoqueMinimo() == null) {
			entity.setEstoqueMinimo(BigDecimal.ZERO);
		}
	}

	@Override
	@Transactional
	public ProdutoModel insert(ProdutoModel entity) {
		ProdutoModel saved = super.insert(entity);
		atualizarParametro(saved);
		return saved;
	}

	@Override
	@Transactional
	public ProdutoModel update(ProdutoModel entity) {
		BigDecimal minimo = entity.getEstoqueMinimo();
		ProdutoModel saved = super.update(entity);
		saved.setEstoqueMinimo(minimo);
		atualizarParametro(saved);
		return saved;
	}

	@Override
	protected void afterInsert(ProdutoModel saved, ProdutoModel original) {
		auditService.registrar("CRIACAO", "PRODUTO", String.valueOf(saved.getId()),
				"Produto criado: " + saved.getNome(), null);
	}

	@Override
	protected void afterUpdate(ProdutoModel saved, ProdutoModel original) {
		auditService.registrar("ALTERACAO", "PRODUTO", String.valueOf(saved.getId()),
				"Produto atualizado: " + saved.getNome(), null);
	}

	@Override
	protected void afterDelete(ProdutoModel entity) {
		auditService.registrar("EXCLUSAO", "PRODUTO", String.valueOf(entity.getId()),
				"Produto removido: " + entity.getNome(), null);
	}

	@Override
	@Transactional(readOnly = true)
	public ProdutoModel findByIdActive(UUID id) {
		ProdutoModel p = super.findByIdActive(id);
		preencherExtras(p);
		return p;
	}

	@Override
	@Transactional(readOnly = true)
	public Page<ProdutoModel> findAllActive(Pageable pageable) {
		Page<ProdutoModel> page = super.findAllActive(pageable);
		page.getContent().forEach(this::preencherExtras);
		return page;
	}

	/** Saldo derivado do produto: lotes disponíveis + embalagens abertas não finalizadas. */
	public BigDecimal obterSaldo(ProdutoModel produto) {
		BigDecimal lotes = loteRepository.sumQuantidadeAtualByProdutoId(produto.getId());
		BigDecimal abertos = produtoAbertoRepository.sumQuantidadeRestanteByProdutoId(produto.getId());
		return NumeroUtil.s(lotes).add(NumeroUtil.s(abertos));
	}

	/** Estoque mínimo do produto (campo mantido em ParametroEstoque). */
	public BigDecimal obterEstoqueMinimo(ProdutoModel produto) {
		return parametroRepository.findByProduto_IdAndAtivoTrue(produto.getId())
				.map(p -> NumeroUtil.s(p.getEstoqueMinimo()))
				.orElse(BigDecimal.ZERO);
	}

	private void preencherExtras(ProdutoModel p) {
		p.setSaldoAtual(obterSaldo(p));
		parametroRepository.findByProduto_IdAndAtivoTrue(p.getId())
				.ifPresent(par -> p.setEstoqueMinimo(NumeroUtil.s(par.getEstoqueMinimo())));
	}

	private void atualizarParametro(ProdutoModel saved) {
		if (saved.getEstoqueMinimo() == null) {
			return;
		}
		ParametroEstoqueModel par = parametroRepository.findByProdutoAndAtivoTrue(saved).orElseGet(() -> {
			ParametroEstoqueModel novo = new ParametroEstoqueModel();
			novo.setProduto(saved);
			novo.setTempoReposicaoDias(3);
			novo.setPeriodoAnaliseDias(7);
			return novo;
		});
		par.setEstoqueMinimo(NumeroUtil.s(saved.getEstoqueMinimo()));
		par.setDataAtualizacao(LocalDateTime.now());
		parametroRepository.save(par);
	}

	public ParametroEstoqueModel buscarParametro(ProdutoModel produto) {
		return parametroRepository.findByProdutoAndAtivoTrue(produto).orElse(null);
	}
}