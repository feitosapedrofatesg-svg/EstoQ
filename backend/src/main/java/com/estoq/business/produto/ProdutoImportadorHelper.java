package com.estoq.business.produto;

import com.estoq.business.categoria.CategoriaModel;
import com.estoq.business.categoria.ICategoriaRepository;
import com.estoq.business.produto.IProdutoRepository;
import com.estoq.business.produto.ProdutoAdapter;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.business.produto.ProdutoService;
import com.estoq.business.produto.UnidadeMedida;
import com.estoq.core.exceptions.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ProdutoImportadorHelper {

	@Autowired
	private ICategoriaRepository categoriaRepository;

	@Autowired
	private IProdutoRepository produtoRepository;

	@Autowired
	private ProdutoAdapter produtoAdapter;

	@Autowired
	private ProdutoService produtoService;

	@Transactional
	public ProdutoModel criarProduto(String nome, String categoriaNome, String unidade) {
		if (nome == null || nome.isBlank()) {
			throw new BusinessException("Produto sem nome na planilha.", HttpStatus.BAD_REQUEST);
		}
		String nomeLimpo = nome.trim();
		CategoriaModel categoria = null;
		if (categoriaNome != null && !categoriaNome.isBlank()) {
			String catLimpo = categoriaNome.trim();
			categoria = categoriaRepository.findByNomeIgnoreCase(catLimpo).orElseGet(() -> {
				CategoriaModel nova = new CategoriaModel();
				nova.setNome(catLimpo);
				return categoriaRepository.save(nova);
			});
		}
		ProdutoModel existente = produtoRepository.findByNomeIgnoreCase(nomeLimpo).orElse(null);
		if (existente != null) {
			return existente;
		}
		ProdutoModel novo = new ProdutoModel();
		novo.setNome(nomeLimpo);
		novo.setUnidadeMedida(unidade == null || unidade.isBlank() ? UnidadeMedida.UN
				: produtoAdapter.resolverUnidade(unidade));
		novo.setCategoria(categoria);
		novo.setEstoqueMinimo(BigDecimal.ZERO);
		return produtoRepository.save(novo);
	}

	@Transactional(readOnly = true)
	public List<ProdutoModel> listarProdutos() {
		return produtoRepository.findAllByAtivoTrue(PageRequest.of(0, Integer.MAX_VALUE)).getContent().stream()
				.peek(p -> {
					p.setSaldoAtual(produtoService.obterSaldo(p));
					p.setEstoqueMinimo(produtoService.obterEstoqueMinimo(p));
				}).toList();
	}
}