package com.estoq.api.controllers;

import com.estoq.business.produto.IProdutoRepository;
import com.estoq.business.produto.ProdutoDTO;
import com.estoq.business.produto.ProdutoMapper;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.business.produto.ProdutoService;
import com.estoq.core.controllers.GenericController;
import com.estoq.core.exceptions.BusinessException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController extends GenericController<ProdutoModel, ProdutoDTO, ProdutoService, ProdutoMapper> {

	@Autowired
	private IProdutoRepository produtoRepository;

	@GetMapping("/categorias")
	public ResponseEntity<List<String>> listarCategorias() {
		Set<String> categorias = new LinkedHashSet<>();
		produtoRepository.findAllByAtivoTrue(org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE))
				.getContent().forEach(p -> {
					if (p.getCategoria() != null && !p.getCategoria().isBlank()) {
						categorias.add(p.getCategoria());
					}
				});
		return ResponseEntity.ok(List.copyOf(categorias));
	}
}