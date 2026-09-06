package com.estoq.business.sistema;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Endpoints de operação do sistema utilizados pelo launcher desktop.
 *
 * <p>Ficam FORA de /api/** (o interceptor de autenticação não cobre) de propósito:
 * são usados localmente apenas pelo launcher no mesmo computador
 * (em produção o servidor escuta somente em 127.0.0.1).</p>
 */
@RestController
public class SistemaDadosController {

	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	@Autowired
	private DataSource dataSource;

	@GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, String> health() {
		return Map.of("status", "UP");
	}

	/**
	 * Encerramento gracioso do servidor. Responde na hora e agenda o
	 * {@code System.exit(0)} para daqui a pouco, deixando o Spring fechar o
	 * H2 (shutdown hook). Usado pelo launcher desktop; no Linux o SIGTERM já
	 * faz isso, no Windows o {@code kill} de processo é forçado, então este
	 * endpoint garante o fechamento limpo do banco nos dois sistemas.
	 */
	@PostMapping(value = "/encerrar", produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, Object> encerrar() {
		Thread disparador = new Thread(() -> {
			try {
				Thread.sleep(1200);
			} catch (InterruptedException ignored) {
				// prossegue
			}
			System.exit(0);
		}, "encerrar-estoq");
		disparador.setDaemon(false);
		disparador.start();
		Map<String, Object> ok = new LinkedHashMap<>();
		ok.put("encerrando", true);
		return ok;
	}

	@PostMapping(value = "/backup", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> backup() {
		String nome = "estoq-" + LocalDateTime.now().format(STAMP) + ".zip";
		Path pasta = Paths.get(System.getProperty("user.dir"), "backups");
		Path alvo = pasta.resolve(nome).toAbsolutePath();
		try {
			Files.createDirectories(pasta);
			try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
				st.execute("BACKUP TO '" + alvo.toString().replace("'", "''") + "'");
			}
		} catch (Exception e) {
			Map<String, Object> erro = new LinkedHashMap<>();
			erro.put("sucesso", false);
			erro.put("mensagem", "Falha no backup: " + e.getMessage());
			return ResponseEntity.badRequest().body(erro);
		}
		Map<String, Object> ok = new LinkedHashMap<>();
		ok.put("sucesso", true);
		ok.put("arquivo", nome);
		ok.put("pasta", pasta.toAbsolutePath().toString());
		return ResponseEntity.ok(ok);
	}
}