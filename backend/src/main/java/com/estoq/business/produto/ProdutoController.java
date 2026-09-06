package com.estoq.business.produto;

import com.estoq.business.categoria.ICategoriaRepository;
import com.estoq.business.produto.ProdutoDTO;
import com.estoq.business.produto.ProdutoAdapter;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.business.produto.ProdutoService;
import com.estoq.business.produto.UnidadeMedida;
import com.estoq.core.controllers.GenericController;
import com.estoq.core.exceptions.FieldValidationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController extends GenericController<ProdutoModel, ProdutoDTO, ProdutoService, ProdutoAdapter> {

	@Autowired
	private ICategoriaRepository categoriaRepository;

	@PatchMapping("/{id}/preco")
	public ResponseEntity<ProdutoDTO> atualizarPreco(@PathVariable UUID id, @RequestBody Map<String, BigDecimal> body) {
		BigDecimal preco = body.get("precoUnitario");
		if (preco != null && preco.signum() < 0) {
			throw new FieldValidationException("precoUnitario", "Preço unitário deve ser maior ou igual a zero.");
		}
		ProdutoModel produto = service.findByIdActive(id);
		produto.setPrecoUnitario(preco);
		return ResponseEntity.ok(adapter.toDto(service.update(produto)));
	}

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