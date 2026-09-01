package com.estoq.integracao.recibo;

import com.estoq.integracao.dto.ResultadoImportacao;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Resultado do processamento de um recibo (foto de cupom fiscal).
 * Estende o resultado padrão de importação e acrescenta as datas e os itens lidos.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LeitorReciboResultado extends ResultadoImportacao {

	private LocalDate dataCompra;

	private Integer totalItens;

	private List<ItemReciboDTO> itens;
}
