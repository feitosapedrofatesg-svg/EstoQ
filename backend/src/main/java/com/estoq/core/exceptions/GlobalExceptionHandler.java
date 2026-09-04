package com.estoq.core.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
		return ResponseEntity.status(ex.getHttpStatus()).body(ErrorResponse.error(ex));
	}

	@ExceptionHandler(FieldValidationException.class)
	public ResponseEntity<ErrorResponse> handleFieldValidationException(FieldValidationException ex) {
		return ResponseEntity.status(ex.getHttpStatus()).body(ErrorResponse.error(ex));
	}

	@ExceptionHandler(RuleValidationException.class)
	public ResponseEntity<ErrorResponse> handleRuleValidationException(RuleValidationException ex) {
		return ResponseEntity.status(ex.getHttpStatus()).body(ErrorResponse.error(ex));
	}

	@ExceptionHandler(BaseException.class)
	public ResponseEntity<ErrorResponse> handleBaseException(BaseException ex) {
		return ResponseEntity.status(ex.getHttpStatus()).body(ErrorResponse.error(ex));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
		return ResponseEntity.status(500).body(ErrorResponse.error("Ocorreu um erro interno no servidor."));
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