package com.estoq.business.produto;

import com.estoq.core.dtos.BaseDTO;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProdutoDTO extends BaseDTO {

	private String nome;
	private String unidadeMedida;
	private UUID categoriaId;
	private String categoriaNome;
	private BigDecimal estoqueMinimo;
	private BigDecimal saldoAtual;
}