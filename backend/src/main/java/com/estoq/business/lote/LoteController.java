package com.estoq.business.lote;

import com.estoq.business.lote.LoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lotes")
public class LoteController {

	public record ValidadeRequest(LocalDate dataValidade) {
	}

	@Autowired
	private LoteService loteService;

	@GetMapping
	public ResponseEntity<List<LoteView>> listarTodos() {
		return ResponseEntity.ok(loteService.listarTodos());
	}

	@GetMapping("/disponiveis")
	public ResponseEntity<List<LoteView>> listarDisponiveis(@RequestParam(required = false) UUID produtoId) {
		return ResponseEntity.ok(produtoId != null
				? loteService.listarPorProduto(produtoId).stream()
						.filter(LoteView::isDisponivel).toList()
				: loteService.listarDisponiveis());
	}

	@GetMapping("/vencendo")
	public ResponseEntity<List<LoteView>> listarVencendo(@RequestParam(required = false) UUID produtoId,
			@RequestParam(defaultValue = "7") int dias) {
		return ResponseEntity.ok(loteService.listarVencendo(produtoId, dias));
	}

	@GetMapping("/vencidos")
	public ResponseEntity<List<LoteView>> listarVencidos(@RequestParam(required = false) UUID produtoId) {
		return ResponseEntity.ok(loteService.listarVencidos(produtoId));
	}

	@GetMapping("/produto/{produtoId}")
	public ResponseEntity<List<LoteView>> listarPorProduto(@PathVariable UUID produtoId) {
		return ResponseEntity.ok(loteService.listarPorProduto(produtoId));
	}

	@PutMapping("/{id}/validade")
	public ResponseEntity<LoteView> editarValidade(@PathVariable UUID id, @RequestBody ValidadeRequest request) {
		return ResponseEntity.ok(loteService.editarValidade(id, request.dataValidade()));
	}
}