package com.estoq.integracao.dto;

import lombok.Data;

@Data
public class ResultadoImportacao {

	private int periodos;
	private int compras;
	private int estoques;
	private int produtosCriados;
	private int produtosAtualizados;
	private String mensagem;
}