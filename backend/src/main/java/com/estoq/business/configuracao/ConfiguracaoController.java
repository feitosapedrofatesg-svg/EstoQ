package com.estoq.business.configuracao;

import com.estoq.business.usuario.UsuarioModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/configuracoes")
public class ConfiguracaoController {

	@Autowired
	private ConfiguracaoService service;

	@GetMapping
	public ResponseEntity<Map<String, String>> listar() {
		return ResponseEntity.ok(service.listar());
	}

	@PutMapping("/{chave}")
	public ResponseEntity<Map<String, String>> salvar(@PathVariable String chave,
			@RequestBody Map<String, String> body,
			@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		service.salvar(chave, body != null ? body.get("valor") : null, usuario);
		return ResponseEntity.ok(Map.of("message", "Configuração salva.", "chave", chave));
	}
}