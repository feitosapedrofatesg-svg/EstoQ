package com.estoq.business.produto;

import com.estoq.core.dtos.BaseDTO;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProdutoDTO extends BaseDTO {

	private String nome;
	private String unidade;
	private String categoria;
	private BigDecimal estoqueMinimo;
}