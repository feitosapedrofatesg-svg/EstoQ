package com.estoq.core.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	/** Mensagem padrão para erros inesperados — nunca expõe detalhes internos nem o código HTTP. */
	public static final String MSG_ERRO_INTERNO = "Ocorreu um erro inesperado. Tente novamente em alguns instantes.";

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
		return responder(ex, ex.getHttpStatus(), request);
	}

	@ExceptionHandler(FieldValidationException.class)
	public ResponseEntity<ErrorResponse> handleFieldValidationException(FieldValidationException ex,
			HttpServletRequest request) {
		return responder(ex, ex.getHttpStatus(), request);
	}

	@ExceptionHandler(RuleValidationException.class)
	public ResponseEntity<ErrorResponse> handleRuleValidationException(RuleValidationException ex,
			HttpServletRequest request) {
		return responder(ex, ex.getHttpStatus(), request);
	}

	@ExceptionHandler(BaseException.class)
	public ResponseEntity<ErrorResponse> handleBaseException(BaseException ex, HttpServletRequest request) {
		return responder(ex, ex.getHttpStatus(), request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
		log.error("Erro inesperado ao processar {} {}", request.getMethod(), request.getRequestURI(), ex);
		return ResponseEntity.status(500).body(ErrorResponse.error(MSG_ERRO_INTERNO));
	}

	/**
	 * Erros com status &gt;= 500 nunca expõem detalhes internos ao cliente; a causa raiz vai para o log.
	 * Erros de negócio (4xx) mantêm a mensagem amigável definida na regra.
	 */
	private ResponseEntity<ErrorResponse> responder(BaseException ex, HttpStatus status, HttpServletRequest request) {
		Object[] ctx = { request.getMethod(), request.getRequestURI(), status.value(), ex.getTitle(), ex.getMessage() };
		if (status.value() >= 500) {
			log.error("Erro interno ao processar {} {} ({}): [{}] {}", ctx);
			return ResponseEntity.status(500).body(ErrorResponse.error(MSG_ERRO_INTERNO));
		}
		log.warn("Erro de negócio ao processar {} {} ({}): [{}] {}", ctx);
		return ResponseEntity.status(status).body(ErrorResponse.error(ex));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public void handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request, HttpServletResponse response)
			throws Exception {
		String path = request.getRequestURI();
		if (path.startsWith("/api/")) {
			throw ex;
		}
		response.setStatus(HttpServletResponse.SC_OK);
		request.getRequestDispatcher("/").forward(request, response);
	}
}