package com.estoq.business.sistema;

import com.estoq.business.auditoria.AuditService;
import com.estoq.business.usuario.UsuarioModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class BackupService {

	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final long TEMPO_MAXIMO_HORAS = 27;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private AuditService auditService;

	/** Executa um backup do banco (H2 BACKUP TO) na pasta ./backups ao lado do jar. */
	public Map<String, Object> executar(UsuarioModel usuario) {
		String nome = "estoq-" + LocalDateTime.now().format(STAMP) + ".zip";
		Path pasta = Paths.get(System.getProperty("user.dir"), "backups");
		Path alvo = pasta.resolve(nome).toAbsolutePath();
		Map<String, Object> ok = new LinkedHashMap<>();
		try {
			Files.createDirectories(pasta);
			try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
				st.execute("BACKUP TO '" + alvo.toString().replace("'", "''") + "'");
			}
			ok.put("sucesso", true);
			ok.put("arquivo", nome);
			ok.put("pasta", pasta.toAbsolutePath().toString());
			ok.put("dataHora", LocalDateTime.now());
			auditService.registrar("BACKUP", "SISTEMA", null,
					"Backup do banco criado: " + nome, usuario);
		} catch (Exception e) {
			ok.put("sucesso", false);
			ok.put("mensagem", "Falha no backup.");
		}
		return ok;
	}

	/** Status do backup: data do último arquivo e se está em dia (nas últimas 27h). */
	public BackupStatus status() {
		Path pasta = Paths.get(System.getProperty("user.dir"), "backups");
		BackupStatus status = new BackupStatus();
		if (!Files.isDirectory(pasta)) {
			return status;
		}
		try (Stream<Path> files = Files.list(pasta)) {
			Path novo = files
					.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".zip"))
					.max(Comparator.comparingLong(p -> {
						try {
							return Files.getLastModifiedTime(p).toMillis();
						} catch (Exception ignored) {
							return 0L;
						}
					}))
					.orElse(null);
			if (novo == null) {
				return status;
			}
			long millis = Files.getLastModifiedTime(novo).toMillis();
			LocalDateTime ultimo = LocalDateTime.ofInstant(
					java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault());
			status.setArquivo(novo.getFileName().toString());
			status.setUltimoBackup(ultimo);
			status.setBackupEmDia(ultimo.plusHours(TEMPO_MAXIMO_HORAS).isAfter(LocalDateTime.now()));
		} catch (Exception ignored) {
			return status;
		}
		return status;
	}
}