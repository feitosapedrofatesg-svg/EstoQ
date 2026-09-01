package com.estoq.core.exceptions;

import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public class BaseException extends RuntimeException {

	private static final long serialVersionUID = -5611894968496351778L;

	private final String title;
	private final HttpStatus httpStatus;
	private final Severity severity;
	private final String motive;

	public BaseException(String title, String message, HttpStatus httpStatus, Severity severity, String motive) {
		super(message);
		this.title = title;
		this.httpStatus = httpStatus;
		this.severity = severity;
		this.motive = motive;
	}
}