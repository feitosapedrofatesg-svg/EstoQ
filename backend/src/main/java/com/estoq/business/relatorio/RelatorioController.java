package com.estoq.business.relatorio;

import com.estoq.business.relatorio.RelatorioModel;
import com.estoq.business.relatorio.RelatorioService;
import com.estoq.business.relatorio.TipoRelatorio;
import com.estoq.core.exceptions.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/relatorios")
public class RelatorioController {

	@Autowired
	private RelatorioService relatorioService;

	@Value("${estoq.mes.padrao:12}")
	private int mesPadrao;

	@Value("${estoq.ano.padrao:2023}")
	private int anoPadrao;

	@GetMapping("/cmv")
	public ResponseEntity<CMVReportDTO> cmv(@RequestParam(required = false) LocalDate inicio,
			@RequestParam(required = false) LocalDate fim, @RequestParam(required = false) BigDecimal vendas) {
		return ResponseEntity.ok(relatorioService.cmv(inicio, fim, vendas));
	}

	@GetMapping("/dashboard")
	public ResponseEntity<DashboardDTO> dashboard(@RequestParam(required = false) Integer ano,
			@RequestParam(required = false) Integer mes) {
		return ResponseEntity.ok(relatorioService.dashboard(
				ano != null ? ano : anoPadrao, mes != null ? mes : mesPadrao));
	}

	@PostMapping("/{tipo}")
	public ResponseEntity<RelatorioViewDTO> gerarPorTipo(@PathVariable String tipo,
			@RequestParam(required = false) LocalDate inicio, @RequestParam(required = false) LocalDate fim) {
		return ResponseEntity.ok(relatorioService.gerarTipo(resolverTipo(tipo), inicio, fim));
	}

	@GetMapping("/historico")
	public ResponseEntity<List<RelatorioModel>> historico() {
		return ResponseEntity.ok(relatorioService.historico());
	}

	private TipoRelatorio resolverTipo(String valor) {
		try {
			return TipoRelatorio.valueOf(valor.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			throw new BusinessException(
					"Tipo de relatório inválido. Valores aceitos: " + List.of(TipoRelatorio.values()).stream()
							.map(Enum::name).toList(),
					HttpStatus.BAD_REQUEST);
		}
	}
}