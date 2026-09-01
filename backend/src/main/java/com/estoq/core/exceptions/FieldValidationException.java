package com.estoq.core.exceptions;

import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public class FieldValidationException extends BaseException {

	private static final long serialVersionUID = -1215394565893203773L;

	private final String field;

	public FieldValidationException(String field, String message) {
		super("Erro de Validação de Campo", message, HttpStatus.BAD_REQUEST, Severity.WARNING, "FIELD_VALIDATION_ERROR");
		this.field = field;
	}
}