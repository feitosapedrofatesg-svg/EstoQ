package com.estoq.core.dtos;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class BaseDTO {

	private UUID id;
	private boolean active;
}