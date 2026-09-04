package com.estoq.core.dtos;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class BaseDTO {

	private UUID id;
	private boolean ativo;
	private LocalDateTime dataHoraCriacao;
}