package com.estoq.business.sistema;

import com.estoq.business.usuario.UsuarioModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Endpoints operacionais autenticados (admin) para backup e status do sistema. */
@RestController
@RequestMapping("/api/sistema")
public class SistemaOperacionalController {

	@Autowired
	private BackupService backupService;

	@GetMapping(value = "/backup/status", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<BackupStatus> status() {
		return ResponseEntity.ok(backupService.status());
	}

	@PostMapping(value = "/backup", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> backup(
			@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		Map<String, Object> resultado = backupService.executar(usuario);
		if (Boolean.TRUE.equals(resultado.get("sucesso"))) {
			return ResponseEntity.ok(resultado);
		}
		return ResponseEntity.badRequest().body(resultado);
	}
}