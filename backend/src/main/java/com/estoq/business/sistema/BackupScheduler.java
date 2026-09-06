package com.estoq.business.sistema;

import com.estoq.business.auditoria.AuditService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Realiza um backup automático do banco em horário programado (produção). */
@Component
public class BackupScheduler {

	private static final Logger LOG = LoggerFactory.getLogger(BackupScheduler.class);

	@Autowired
	private BackupService backupService;

	@Value("${estoq.backup.habilitado:false}")
	private boolean habilitado;

	@Scheduled(cron = "${estoq.backup.cron:0 30 3 * * *}")
	public void executarDiario() {
		if (!habilitado) {
			return;
		}
try {
			Map<String, Object> resultado = backupService.executar(null);
			LOG.info("Backup automático: {} / {}", resultado.get("sucesso"), resultado.get("arquivo"));
		} catch (Exception e) {
			LOG.warn("Falha no backup automático: {}", e.getMessage());
		}
	}
}