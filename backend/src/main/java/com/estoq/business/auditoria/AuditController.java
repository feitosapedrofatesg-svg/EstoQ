package com.estoq.business.auditoria;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auditoria")
public class AuditController {

	@Autowired
	private AuditService service;

	@GetMapping
	public ResponseEntity<List<AuditLogView>> ultimos(@RequestParam(defaultValue = "10") int limite) {
		return ResponseEntity.ok(service.ultimosEventos(limite));
	}
}