package com.estoq.core.exceptions;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ErrorResponse {

	private String title;
	private String message;
	/** Código técnico interno para diagnose (não exibido ao usuário final). */
	private String motive;

	private ErrorResponse(String message) {
		this("Erro", message, null);
	}

	private ErrorResponse(String title, String message, String motive) {
		this.title = title;
		this.message = message;
		this.motive = motive;
	}

	public static ErrorResponse error(BusinessException ex) {
		return new ErrorResponse(ex.getTitle(), ex.getMessage(), ex.getMotive());
	}

	public static ErrorResponse error(FieldValidationException ex) {
		return new ErrorResponse(ex.getTitle(), ex.getMessage(), ex.getMotive());
	}

	public static ErrorResponse error(RuleValidationException ex) {
		return new ErrorResponse(ex.getTitle(), ex.getMessage(), ex.getMotive());
	}

	public static ErrorResponse error(BaseException ex) {
		return new ErrorResponse(ex.getTitle(), ex.getMessage(), ex.getMotive());
	}

	public static ErrorResponse error(String message) {
		return new ErrorResponse(message);
	}
}