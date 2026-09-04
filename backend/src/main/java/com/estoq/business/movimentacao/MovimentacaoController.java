package com.estoq.business.movimentacao;

import com.estoq.business.movimentacao.MotivoDesperdicio;
import com.estoq.business.movimentacao.MovimentacaoService;
import com.estoq.business.produto.ProdutoAdapter;
import com.estoq.business.usuario.UsuarioModel;
import com.estoq.core.exceptions.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/movimentacoes")
public class MovimentacaoController {

	@Autowired
	private MovimentacaoService movimentacaoService;

	@Autowired
	private ProdutoAdapter produtoAdapter;

	@GetMapping
	public ResponseEntity<List<MovimentacaoView>> listar(@RequestParam(required = false) UUID produtoId,
			@RequestParam(required = false) LocalDate inicio, @RequestParam(required = false) LocalDate fim) {
		return ResponseEntity.ok(movimentacaoService.listar(produtoId, inicio, fim));
	}

	@PostMapping("/entrada")
	public ResponseEntity<MovimentacaoView> registrarEntrada(@RequestBody EntradaRequest req,
			@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		return ResponseEntity.status(HttpStatus.CREATED).body(movimentacaoService.registrarEntrada(
				req.getProdutoId(), usuario, req.getQuantidade(), req.getValorTotalPago(),
				req.getUnidadeCompra() == null ? null : produtoAdapter.resolverUnidade(req.getUnidadeCompra()),
				req.getDataValidade(), req.getObservacao()));
	}

	@PostMapping("/consumo")
	public ResponseEntity<MovimentacaoView> registrarConsumo(@RequestBody ConsumoRequest req,
			@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		return ResponseEntity.status(HttpStatus.CREATED).body(movimentacaoService.registrarConsumo(
				req.getProdutoId(), usuario, req.getQuantidade(), req.getProdutoAbertoId(), req.getObservacao()));
	}

	@PostMapping("/desperdicio")
	public ResponseEntity<MovimentacaoView> registrarDesperdicio(@RequestBody DesperdicioRequest req,
			@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		return ResponseEntity.status(HttpStatus.CREATED).body(movimentacaoService.registrarDesperdicio(
				req.getProdutoId(), usuario, req.getQuantidade(), resolverMotivo(req.getMotivo()),
				req.getDescricaoMotivo(), req.getLoteId(), req.getObservacao()));
	}

	@PostMapping("/ajuste")
	public ResponseEntity<MovimentacaoView> registrarAjuste(@RequestBody AjusteRequest req,
			@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		return ResponseEntity.status(HttpStatus.CREATED).body(movimentacaoService.registrarAjuste(
				req.getProdutoId(), usuario, req.getDiferenca(), req.getJustificativa(), null, req.getObservacao()));
	}

	@PostMapping("/sobra")
	public ResponseEntity<com.estoq.business.produtoaberto.ProdutoAbertoView> registrarSobra(@RequestBody SobraRequest req,
			@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(movimentacaoService.registrarSobra(req.getProdutoAbertoId(), usuario, req.getQuantidade()));
	}

	@PostMapping("/{id}/reverter")
	public ResponseEntity<Object> reverter(@PathVariable UUID id) {
		movimentacaoService.reverterMovimentacao(id);
		return ResponseEntity.ok("Movimentação revertida com sucesso.");
	}

	private MotivoDesperdicio resolverMotivo(String valor) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		try {
			return MotivoDesperdicio.valueOf(valor.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			throw new BusinessException("Motivo de desperdício inválido.", HttpStatus.BAD_REQUEST);
		}
	}
}