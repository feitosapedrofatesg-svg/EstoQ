package com.estoq.api.controllers;

import com.estoq.business.periodo.PeriodoDTO;
import com.estoq.business.periodo.PeriodoMapper;
import com.estoq.business.periodo.PeriodoModel;
import com.estoq.business.periodo.PeriodoService;
import com.estoq.core.controllers.GenericController;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/periodos")
@RequiredArgsConstructor
public class PeriodoController extends GenericController<PeriodoModel, PeriodoDTO, PeriodoService, PeriodoMapper> {

	private final PeriodoService periodoService;

	@GetMapping("/listar")
	public ResponseEntity<List<PeriodoDTO>> listarOrdenados() {
		return ResponseEntity.ok(periodoService.listarOrdenados().stream().map(mapper::toDto).toList());
	}

	@PostMapping("/{id}/fechar")
	public ResponseEntity<PeriodoDTO> fechar(@PathVariable UUID id) {
		return ResponseEntity.ok(mapper.toDto(periodoService.fechar(id)));
	}
}