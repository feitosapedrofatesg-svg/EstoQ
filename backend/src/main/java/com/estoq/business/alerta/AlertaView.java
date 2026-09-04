package com.estoq.business.alerta;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlertaView {

	private UUID id;
	private String tipo;
	private String mensagem;
	private LocalDateTime dataGeracao;
	private String perfilDestino;
	private boolean visualizado;
	private String produtoNome;
	private String loteCodigo;
}