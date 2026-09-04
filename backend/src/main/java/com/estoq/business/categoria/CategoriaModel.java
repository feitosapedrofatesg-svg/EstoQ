package com.estoq.business.categoria;

import com.estoq.core.domains.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "categorias")
public class CategoriaModel extends BaseModel {

	@Column(name = "nome", length = 60, unique = true, nullable = false)
	private String nome;

	@Column(name = "descricao", length = 200)
	private String descricao;

	public boolean possuiProdutosVinculados() {
		// consulta feita pelo serviço; mantido como gancho de domínio
		return false;
	}

	public String getNomeTrim() {
		return nome == null ? null : nome.trim();
	}
}