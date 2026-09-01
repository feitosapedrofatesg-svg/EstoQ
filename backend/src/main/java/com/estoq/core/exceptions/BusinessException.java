package com.estoq.core.exceptions;

import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public class BusinessException extends BaseException {

	private static final long serialVersionUID = -224350778964358350L;

	public BusinessException(String message, HttpStatus httpStatus) {
		super("Erro de Processo de Negócio", message, httpStatus, Severity.ERROR, "BUSINESS_PROCESS_ERROR");
	}

	public BusinessException(String message) {
		super("Erro de Negócio", message, HttpStatus.UNPROCESSABLE_ENTITY, Severity.ERROR, "BUSINESS_ERROR");
	}

	public BusinessException(Throwable cause) {
		super("Erro Interno de Processamento", cause.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
				Severity.FATAL, "INTERNAL_SERVER_ERROR");
		this.initCause(cause);
	}

	public BusinessException(String customMessage, Throwable cause) {
		super("Erro de Operação", customMessage, HttpStatus.INTERNAL_SERVER_ERROR, Severity.ERROR, "OPERATION_FAILED");
		this.initCause(cause);
	}
}