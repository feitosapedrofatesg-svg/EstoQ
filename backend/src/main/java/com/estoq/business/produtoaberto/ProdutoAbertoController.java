package com.estoq.business.produtoaberto;

import com.estoq.business.movimentacao.MovimentacaoService;
import com.estoq.business.produtoaberto.ProdutoAbertoService;
import com.estoq.business.usuario.UsuarioModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/produtos-abertos")
public class ProdutoAbertoController {

	@Autowired
	private ProdutoAbertoService produtoAbertoService;

	@Autowired
	private MovimentacaoService movimentacaoService;

	@GetMapping
	public ResponseEntity<List<ProdutoAbertoView>> listar(@RequestParam(required = false) UUID produtoId) {
		return ResponseEntity.ok(produtoId != null
				? produtoAbertoService.listarPorProduto(produtoId)
				: produtoAbertoService.listarAbertos());
	}

	@PostMapping("/abrir")
	public ResponseEntity<ProdutoAbertoView> abrir(@RequestBody AbrirEmbalagemRequest req,
			@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		return ResponseEntity.ok(movimentacaoService.abrirEmbalagem(
				req.getProdutoId(), usuario, req.getQuantidade(), req.getLoteId()));
	}

	@GetMapping("/{id}/saldo")
	public ResponseEntity<BigDecimal> saldo(@PathVariable UUID id) {
		return ResponseEntity.ok(movimentacaoService.obterSaldo(id));
	}

	public static class AbrirEmbalagemRequest {
		private UUID produtoId;
		private BigDecimal quantidade;
		private UUID loteId;

		public UUID getProdutoId() {
			return produtoId;
		}

		public void setProdutoId(UUID produtoId) {
			this.produtoId = produtoId;
		}

		public BigDecimal getQuantidade() {
			return quantidade;
		}

		public void setQuantidade(BigDecimal quantidade) {
			this.quantidade = quantidade;
		}

		public UUID getLoteId() {
			return loteId;
		}

		public void setLoteId(UUID loteId) {
			this.loteId = loteId;
		}
	}
}