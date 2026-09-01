package com.estoq.integracao.controller;

import com.estoq.business.relatorio.RelatorioService;
import com.estoq.integracao.dto.ResultadoImportacao;
import com.estoq.integracao.recibo.LeitorReciboResultado;
import com.estoq.integracao.recibo.LeitorReciboService;
import com.estoq.integracao.service.ExportadorCsvService;
import com.estoq.integracao.service.ImportadorPlanilhaService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/integracao")
@RequiredArgsConstructor
public class IntegracaoController {

	private final ImportadorPlanilhaService importadorPlanilhaService;
	private final RelatorioService relatorioService;
	private final ExportadorCsvService exportadorCsvService;
	private final LeitorReciboService leitorReciboService;

	@PostMapping("/importar-planilha")
	public ResponseEntity<ResultadoImportacao> importarPlanilha(@RequestParam("arquivo") MultipartFile arquivo) {
		return ResponseEntity.ok(importadorPlanilhaService.importar(arquivo));
	}

	@PostMapping("/importar-recibo")
	public ResponseEntity<LeitorReciboResultado> importarRecibo(@RequestParam("arquivo") MultipartFile arquivo) {
		return ResponseEntity.ok(leitorReciboService.importar(arquivo));
	}

	@GetMapping("/relatorio-cmv/{periodoId}.csv")
	public ResponseEntity<byte[]> exportarCsvPeriodo(@PathVariable UUID periodoId) {
		String csv = exportadorCsvService.relatorioCmvPeriodo(relatorioService.relatorioPeriodo(periodoId));
		return csvResponse(csv, "relatorio-cmv-" + periodoId + ".csv");
	}

	@GetMapping("/relatorio-cmv-mensal.csv")
	public ResponseEntity<byte[]> exportarCsvMensal(@RequestParam int ano, @RequestParam int mes) {
		String csv = exportadorCsvService.relatorioCmvMensal(relatorioService.relatorioMensal(ano, mes));
		return csvResponse(csv, "relatorio-cmv-mensal-" + mes + "-" + ano + ".csv");
	}

	private ResponseEntity<byte[]> csvResponse(String csv, String nomeArquivo) {
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.body("\uFEFF".concat(csv).getBytes(StandardCharsets.UTF_8));
	}
}