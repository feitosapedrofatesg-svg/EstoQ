package com.estoq.business.sistema;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class BackupStatus {

	private LocalDateTime ultimoBackup;
	private String arquivo;
	private boolean backupEmDia;
}