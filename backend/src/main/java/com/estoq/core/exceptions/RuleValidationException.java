package com.estoq.core.exceptions;

import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public class RuleValidationException extends BaseException {

	private static final long serialVersionUID = -8073301572236960178L;

	public RuleValidationException(String ruleName, String message) {
		super("Violação de Regra de Negócio: " + ruleName, message, HttpStatus.CONFLICT, Severity.ERROR,
				"BUSINESS_RULE_VIOLATION");
	}
}