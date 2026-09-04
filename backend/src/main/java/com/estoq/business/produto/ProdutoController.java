package com.estoq.business.produto;

import com.estoq.business.categoria.ICategoriaRepository;
import com.estoq.business.produto.ProdutoDTO;
import com.estoq.business.produto.ProdutoAdapter;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.business.produto.ProdutoService;
import com.estoq.business.produto.UnidadeMedida;
import com.estoq.core.controllers.GenericController;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController extends GenericController<ProdutoModel, ProdutoDTO, ProdutoService, ProdutoAdapter> {

	@Autowired
	private ICategoriaRepository categoriaRepository;

	@GetMapping("/categorias")
	public ResponseEntity<List<String>> listarCategorias() {
		return ResponseEntity.ok(categoriaRepository
				.findAllByAtivoTrue(org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE))
				.getContent().stream().map(c -> c.getNome()).toList());
	}

	@GetMapping("/opcoes-unidade")
	public ResponseEntity<List<String>> listarUnidades() {
		return ResponseEntity.ok(java.util.Arrays.stream(UnidadeMedida.values()).map(Enum::name).toList());
	}
}