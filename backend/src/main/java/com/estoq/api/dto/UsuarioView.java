package com.estoq.api.dto;

import com.estoq.business.usuario.Perfil;
import com.estoq.business.usuario.UsuarioModel;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UsuarioView {

	private UUID id;
	private String nome;
	private Perfil perfil;
	private boolean active;

	public static UsuarioView of(UsuarioModel u) {
		return new UsuarioView(u.getId(), u.getNome(), u.getPerfil(), u.isAtivo());
	}
}