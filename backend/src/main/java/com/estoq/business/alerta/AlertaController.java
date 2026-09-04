package com.estoq.business.alerta;

import com.estoq.business.alerta.AlertaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/alertas")
public class AlertaController {

	@Autowired
	private AlertaService alertaService;

	@PostMapping("/gerar")
	public ResponseEntity<Map<String, Object>> gerar() {
		int criados = alertaService.gerarAlertas();
		return ResponseEntity.ok(Map.of("gerados", criados));
	}

	@GetMapping
	public ResponseEntity<List<AlertaView>> listar() {
		return ResponseEntity.ok(alertaService.listar());
	}

	@GetMapping("/pendentes")
	public ResponseEntity<List<AlertaView>> listarPendentes() {
		return ResponseEntity.ok(alertaService.listarPendentes());
	}

	@GetMapping("/pendentes/contar")
	public ResponseEntity<Map<String, Object>> contarPendentes() {
		return ResponseEntity.ok(Map.of("qtd", alertaService.contarPendentes()));
	}

	@PutMapping("/{id}/visualizado")
	public ResponseEntity<Object> marcarVisualizado(@PathVariable UUID id) {
		alertaService.marcarComoVisualizado(id);
		return ResponseEntity.ok("Alerta marcado como visualizado.");
	}
}