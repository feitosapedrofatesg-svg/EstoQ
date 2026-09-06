package com.estoq.business.sessao;

import com.estoq.business.usuario.Perfil;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {

	private String token;
	private String nome;
	private Perfil perfil;
	private boolean trocarPin;
}