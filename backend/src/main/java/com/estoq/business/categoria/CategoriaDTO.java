package com.estoq.business.categoria;

import com.estoq.core.dtos.BaseDTO;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoriaDTO extends BaseDTO {

	private String nome;
	private String descricao;
	private Long qtdProdutos;
}