package com.estoq.business.balanco;

import com.estoq.business.balanco.BalancoModel;
import com.estoq.business.balanco.ConferenciaService;
import com.estoq.business.balanco.ConfiguracaoBalancoModel;
import com.estoq.business.balanco.PeriodicidadeBalanco;
import com.estoq.business.usuario.UsuarioModel;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/conferencia")
public class ConferenciaController {

	@Autowired
	private ConferenciaService conferenciaService;

	@GetMapping("/configuracao")
	public ResponseEntity<ConfiguracaoBalancoView> configuracao() {
		ConfiguracaoBalancoModel cfg = conferenciaService.getConfiguracao();
		if (cfg == null) {
			return ResponseEntity.ok(null);
		}
		return ResponseEntity.ok(conferenciaService.toConfigView(cfg));
	}

	@PutMapping("/configuracao")
	public ResponseEntity<ConfiguracaoBalancoView> atualizarConfiguracao(@RequestBody Map<String, Object> body) {
		PeriodicidadeBalanco periodicidade = body.get("periodicidade") == null ? null
				: PeriodicidadeBalanco.valueOf(body.get("periodicidade").toString().trim().toUpperCase());
		Integer dia = body.get("diaExecucao") == null ? null
				: ((Number) body.get("diaExecucao")).intValue();
		return ResponseEntity.ok(conferenciaService.toConfigView(
				conferenciaService.atualizarConfiguracao(periodicidade, dia)));
	}

	@GetMapping("/balancos")
	public ResponseEntity<List<BalancoView>> listarBalancos() {
		return ResponseEntity.ok(conferenciaService.listarBalancos());
	}

	@PostMapping("/balancos/iniciar")
	public ResponseEntity<BalancoView> iniciar(@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		return ResponseEntity.ok(conferenciaService.iniciarBalanco(usuario));
	}

	@GetMapping("/balancos/{id}")
	public ResponseEntity<BalancoView> getBalanco(@PathVariable UUID id) {
		return ResponseEntity.ok(conferenciaService.getBalanco(id));
	}

	@PutMapping("/balancos/{id}/itens/{itemId}")
	public ResponseEntity<ItemBalancoView> atualizarQuantidadeFisica(@PathVariable UUID id,
			@PathVariable UUID itemId, @RequestBody Map<String, Object> body) {
		BigDecimal qtd = body.get("quantidadeFisica") == null ? null
				: new BigDecimal(body.get("quantidadeFisica").toString());
		return ResponseEntity.ok(conferenciaService.atualizarQuantidadeFisica(id, itemId, qtd));
	}

	@PostMapping("/balancos/{id}/apurar")
	public ResponseEntity<BalancoView> apurar(@PathVariable UUID id) {
		return ResponseEntity.ok(conferenciaService.apurar(id));
	}

	@PostMapping("/balancos/{id}/confirmar")
	public ResponseEntity<BalancoView> confirmar(@PathVariable UUID id,
			@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		return ResponseEntity.ok(conferenciaService.confirmar(id, usuario));
	}

	@PostMapping("/balancos/{id}/cancelar")
	public ResponseEntity<BalancoView> cancelar(@PathVariable UUID id) {
		return ResponseEntity.ok(conferenciaService.cancelarBalanco(id));
	}

	@PostMapping("/balancos/{id}/reabrir")
	public ResponseEntity<BalancoView> reabrir(@PathVariable UUID id) {
		return ResponseEntity.ok(conferenciaService.reabrirBalanco(id));
	}

	@DeleteMapping("/balancos/{id}")
	public ResponseEntity<Void> excluir(@PathVariable UUID id) {
		conferenciaService.excluirBalanco(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/balancos/{id}/exportar")
	public void exportar(@PathVariable UUID id, HttpServletResponse response) throws IOException {
		BalancoView b = conferenciaService.getBalanco(id);
		response.setContentType("text/csv; charset=UTF-8");
		response.setHeader("Content-Disposition",
				"attachment; filename=balanco-" + b.getDataHora().toLocalDate() + ".csv");
		StringBuilder sb = new StringBuilder("\uFEFF");
		sb.append("Data;Tipo;Status;Responsável\n");
		sb.append("\"").append(b.getDataHora()).append("\";\"")
				.append(b.getTipo() != null ? b.getTipo() : "").append("\";\"")
				.append(b.getStatus() != null ? b.getStatus() : "").append("\";\"")
				.append(b.getResponsavelNome() == null ? "" : b.getResponsavelNome().replace("\"", "\"\"")).append("\"\n\n");
		sb.append("Produto;Unidade;Qtd. Sistema;Qtd. Física;Diferença\n");
		for (ItemBalancoView i : b.getItens()) {
			sb.append(escapar(i.getProdutoNome())).append(';')
					.append(escapar(i.getUnidadeMedida() != null ? i.getUnidadeMedida() : "")).append(';')
					.append(numeral(i.getQuantidadeSistema())).append(';')
					.append(numeral(i.getQuantidadeFisica())).append(';')
					.append(numeral(i.getDiferenca())).append('\n');
		}
		sb.append(";TOTAL;;;").append(numeral(b.getTotalDiferenca())).append('\n');
		response.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
	}

	private String numeral(java.math.BigDecimal v) {
		if (v == null) {
			return "0";
		}
		return v.stripTrailingZeros().toPlainString();
	}

	private String escapar(String v) {
		return "\"" + (v == null ? "" : v.replace("\"", "\"\"")) + "\"";
	}

	private BalancoView toView(BalancoModel b) {
		return conferenciaService.toView(b);
	}
}