package com.estoq.business.configuracao;

import com.estoq.core.domains.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "configuracoes")
public class ConfiguracaoModel extends BaseModel {

	@Column(name = "chave", length = 60, unique = true, nullable = false)
	private String chave;

	@Column(name = "valor", length = 200, nullable = false)
	private String valor;
}