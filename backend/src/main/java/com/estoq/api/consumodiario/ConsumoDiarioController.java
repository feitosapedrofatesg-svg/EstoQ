package com.estoq.api.consumodiario;

import com.estoq.api.dto.ConsumoDiarioView;
import com.estoq.business.consumodiario.ConsumoDiarioDTO;
import com.estoq.business.consumodiario.ConsumoDiarioService;
import com.estoq.business.usuario.UsuarioModel;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/consumo-diario")
@RequiredArgsConstructor
public class ConsumoDiarioController {

	private final ConsumoDiarioService consumoServico;

	@PostMapping
	public ResponseEntity<ConsumoDiarioView> registrar(
			@RequestBody ConsumoDiarioDTO dto,
			@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		ConsumoDiarioView view = ConsumoDiarioView.of(consumoServico.registrar(dto, usuario));
		return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(view);
	}

	@GetMapping("/hoje")
	public ResponseEntity<List<ConsumoDiarioView>> hoje() {
		return ResponseEntity.ok(toViews(LocalDate.now()));
	}

	@GetMapping
	public ResponseEntity<List<ConsumoDiarioView>> listar(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
		return ResponseEntity.ok(toViews(data != null ? data : LocalDate.now()));
	}

	@GetMapping("/todas")
	public ResponseEntity<List<ConsumoDiarioView>> todas() {
		return ResponseEntity.ok(
				consumoServico.listarTudo().stream().map(ConsumoDiarioView::of).toList());
	}

	@GetMapping("/sumario")
	public ResponseEntity<Map<String, Object>> sumario(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
		return ResponseEntity.ok(consumoServico.sumario(data != null ? data : LocalDate.now()));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<String> excluir(
			@PathVariable UUID id,
			@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		consumoServico.excluir(id, usuario);
		return ResponseEntity.ok("Registro de uso removido com sucesso.");
	}

	private List<ConsumoDiarioView> toViews(LocalDate data) {
		return consumoServico.listar(data).stream().map(ConsumoDiarioView::of).toList();
	}
}