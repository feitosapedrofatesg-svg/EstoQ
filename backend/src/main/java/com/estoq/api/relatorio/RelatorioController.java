package com.estoq.api.relatorio;

import com.estoq.business.relatorio.RelatorioService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
public class RelatorioController {

	private final RelatorioService relatorioService;

	@Value("${estoq.mes.padrao:12}")
	private int mesPadrao;

	@Value("${estoq.ano.padrao:2023}")
	private int anoPadrao;

	@GetMapping("/cmv/periodo/{periodoId}")
	public ResponseEntity<RelatorioCMVPeriodo> cmvPeriodo(@PathVariable UUID periodoId) {
		return ResponseEntity.ok(relatorioService.relatorioPeriodo(periodoId));
	}

	@GetMapping("/cmv/mensal")
	public ResponseEntity<RelatorioCMVMensal> cmvMensal(@RequestParam(required = false) Integer ano,
			@RequestParam(required = false) Integer mes) {
		return ResponseEntity.ok(relatorioService.relatorioMensal(
				ano != null ? ano : anoPadrao, mes != null ? mes : mesPadrao));
	}

	@GetMapping("/consumo/matriz")
	public ResponseEntity<ConsumoMatriz> consumoMatriz(@RequestParam(required = false) Integer ano,
			@RequestParam(required = false) Integer mes) {
		return ResponseEntity.ok(relatorioService.matrizConsumo(
				ano != null ? ano : anoPadrao, mes != null ? mes : mesPadrao));
	}

	@GetMapping("/alertas-estoque")
	public ResponseEntity<List<AlertaEstoque>> alertasEstoque() {
		return ResponseEntity.ok(relatorioService.alertasEstoque());
	}

	@GetMapping("/dashboard")
	public ResponseEntity<DashboardDTO> dashboard(@RequestParam(required = false) Integer ano,
			@RequestParam(required = false) Integer mes) {
		return ResponseEntity.ok(relatorioService.dashboard(
				ano != null ? ano : anoPadrao, mes != null ? mes : mesPadrao));
	}
}