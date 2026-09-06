package com.estoq.business.sessao;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SessaoView {

	private UUID id;
	private UUID usuarioId;
	private String usuarioNome;
	private LocalDateTime criadoEm;
	private LocalDateTime expiraEm;
	private String origem;
}