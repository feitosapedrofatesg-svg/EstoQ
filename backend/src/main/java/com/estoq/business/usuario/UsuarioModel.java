package com.estoq.business.usuario;

import com.estoq.core.domains.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "usuarios")
public class UsuarioModel extends BaseModel {

	@Column(name = "nome", length = 120, nullable = false)
	private String nome;

	@Column(name = "pin", length = 100, nullable = false)
	private String pin;

	@Enumerated(EnumType.STRING)
	@Column(name = "perfil", length = 20, nullable = false)
	private Perfil perfil;

	@Column(name = "tentativas_falhas")
	private Integer tentativasFalhas = 0;

	@Column(name = "bloqueado_ate")
	private LocalDateTime bloqueadoAte;

	@Column(name = "trocar_pin", nullable = false)
	private boolean trocarPin;
}