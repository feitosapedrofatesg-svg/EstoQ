package com.estoq.business.movimentacao;

import com.estoq.business.lote.LoteModel;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.business.produto.UnidadeMedida;
import com.estoq.core.helpers.NumeroUtil;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@DiscriminatorValue("ENTRADA")
public class EntradaModel extends MovimentacaoEstoqueModel {

	@Column(name = "valor_total_pago", precision = 14, scale = 2)
	private BigDecimal valorTotalPago;

	@Enumerated(EnumType.STRING)
	@Column(name = "unidade_compra", length = 20)
	private UnidadeMedida unidadeCompra;

	@Column(name = "fator_conversao", precision = 12, scale = 3)
	private BigDecimal fatorConversao;

	@Column(name = "data_validade")
	private LocalDate dataValidade;

	@Column(name = "preco_unitario", precision = 14, scale = 2)
	private BigDecimal precoUnitario;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "lote_gerado_id")
	private LoteModel loteGerado;

	/** Preço unitário = total pago ÷ quantidade recebida. */
	public BigDecimal calcularPrecoUnitario() {
		this.precoUnitario = NumeroUtil.divide(valorTotalPago, getQuantidade(), 2);
		return this.precoUnitario;
	}

	/** Gera o lote correspondente a esta entrada. */
	public LoteModel gerarLote(ProdutoModel produto, LocalDate dataEntrada) {
		if (precoUnitario == null) {
			calcularPrecoUnitario();
		}
		LoteModel lote = new LoteModel();
		lote.setProduto(produto);
		lote.setCodigo("E" + (getId() != null ? getId().toString().substring(0, 8) : System.currentTimeMillis() % 100000));
		lote.setQuantidadeInicial(getQuantidade());
		lote.setQuantidadeAtual(getQuantidade());
		lote.setDataEntrada(dataEntrada);
		lote.setDataValidade(dataValidade);
		lote.setPrecoUnitario(precoUnitario);
		this.loteGerado = lote;
		setLote(lote);
		return lote;
	}

	@Override
	public TipoMovimentacao getTipo() {
		return TipoMovimentacao.ENTRADA;
	}
}