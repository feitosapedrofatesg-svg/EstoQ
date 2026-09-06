package com.estoq.business.venda;

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

@RestController
@RequestMapping("/api/vendas")
public class VendaMesController {

	@Autowired
	private VendaMesService service;

	@GetMapping("/{ano}/{mes}")
	public ResponseEntity<VendaMesView> obter(@PathVariable int ano, @PathVariable int mes) {
		return ResponseEntity.ok(service.obter(ano, mes));
	}

	@PutMapping("/{ano}/{mes}")
	public ResponseEntity<VendaMesView> salvar(@PathVariable int ano, @PathVariable int mes,
			@RequestBody VendaMesRequest request,
			@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		return ResponseEntity.ok(service.salvar(ano, mes, request != null ? request.getValor() : null, usuario));
	}
}