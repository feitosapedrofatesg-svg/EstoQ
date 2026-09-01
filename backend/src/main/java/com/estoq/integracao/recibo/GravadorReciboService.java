package com.estoq.integracao.recibo;

import com.estoq.business.compra.CompraModel;
import com.estoq.business.compra.ICompraRepository;
import com.estoq.business.periodo.IPeriodoRepository;
import com.estoq.business.periodo.PeriodoModel;
import com.estoq.business.periodo.PeriodoStatus;
import com.estoq.business.produto.IProdutoRepository;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.business.produto.ProdutoService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava os itens lidos de um recibo como compras no banco, vinculadas ao período
 * corrente e reaproveitando/criando produtos automaticamente (mesmo padrão do
 * importador de planilha).
 */
@Service
public class GravadorReciboService {

	private static final String CATEGORIA_RECIBO = "Recibo";
	private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);

	private final IProdutoRepository produtoRepository;
	private final IPeriodoRepository periodoRepository;
	private final ICompraRepository compraRepository;
	private final ProdutoService produtoService;
	private final ParserReciboService parserService;

	public GravadorReciboService(IProdutoRepository produtoRepository, IPeriodoRepository periodoRepository,
			ICompraRepository compraRepository, ProdutoService produtoService, ParserReciboService parserService) {
		this.produtoRepository = produtoRepository;
		this.periodoRepository = periodoRepository;
		this.compraRepository = compraRepository;
		this.produtoService = produtoService;
		this.parserService = parserService;
	}

	@Transactional
	public LeitorReciboResultado gravar(String textoBruto, LeitorReciboResultado resultado) {
		ParserReciboService.ParseResult parse = parserService.parsear(textoBruto);
		resultado.setItens(parse.getItens());
		resultado.setTotalItens(parse.getItens().size());
		if (parse.getItens().isEmpty()) {
			resultado.setMensagem("Nenhum item reconhecido no recibo. Verifique a qualidade da foto.");
			return resultado;
		}

		LocalDate dataCompra = parse.getData() != null ? parse.getData() : LocalDate.now();
		resultado.setDataCompra(dataCompra);
		PeriodoModel periodo = busyPeriodo(dataCompra);

		for (ItemReciboDTO item : parse.getItens()) {
			ProdutoModel produto = obterProduto(item.getDescricao(), resultado);
			if (produto == null) {
				continue;
			}
			salvarCompra(periodo, produto, item, dataCompra);
			resultado.setCompras(resultado.getCompras() + 1);
			if (resultado.getPeriodos() == 0) {
				resultado.setPeriodos(1);
			}
		}

		if (resultado.getCompras() == 0) {
			resultado.setMensagem("Nenhuma compra gravada. Verifique os itens reconhecidos.");
		} else {
			resultado.setMensagem(
					"Recibo processado com sucesso: " + resultado.getCompras()
							+ " compra(s) gravada(s) em \"" + periodo.getNome()
							+ "\" (data " + dataCompra.format(BR) + ").");
		}
		return resultado;
	}

	/**
	 * Encontra o período aberto que contém a data; senão o mais recente aberto;
	 * senão cria um período para o mês da data do recibo.
	 */
	private PeriodoModel busyPeriodo(LocalDate data) {
		List<PeriodoModel> periodos = periodoRepository.findAllByAtivoTrueOrderByDataInicioAsc();

		PeriodoModel contem = periodos.stream()
				.filter(p -> !data.isBefore(p.getDataInicio()) && !data.isAfter(p.getDataFim()))
				.filter(p -> p.getStatus() == PeriodoStatus.ABERTO)
				.findFirst().orElse(null);
		if (contem != null) {
			return contem;
		}

		PeriodoModel abertoMaisRecente = null;
		for (PeriodoModel p : periodos) {
			if (p.getStatus() == PeriodoStatus.ABERTO && (abertoMaisRecente == null
					|| p.getDataInicio().isAfter(abertoMaisRecente.getDataInicio()))) {
				abertoMaisRecente = p;
			}
		}
		if (abertoMaisRecente != null) {
			return abertoMaisRecente;
		}

		PeriodoModel periodo = new PeriodoModel();
		LocalDate inicio = data.withDayOfMonth(1);
		LocalDate fim = data.withDayOfMonth(data.lengthOfMonth());
		periodo.setNome("Semana " + inicio.format(BR) + " a " + fim.format(BR));
		periodo.setDataInicio(inicio);
		periodo.setDataFim(fim);
		periodo.setStatus(PeriodoStatus.ABERTO);
		return periodoRepository.save(periodo);
	}

	private ProdutoModel obterProduto(String nome, LeitorReciboResultado resultado) {
		String nomeLimpo = normalizar(nome);
		if (nomeLimpo.isEmpty()) {
			return null;
		}
		return produtoRepository.findByNomeIgnoreCase(nomeLimpo).orElseGet(() -> {
			ProdutoModel p = new ProdutoModel();
			p.setNome(nomeLimpo);
			p.setUnidade("und");
			p.setCategoria(CATEGORIA_RECIBO);
			p.setEstoqueMinimo(BigDecimal.valueOf(7));
			p.setAtivo(true);
			ProdutoModel salvo = produtoService.insert(p);
			resultado.setProdutosCriados(resultado.getProdutosCriados() + 1);
			return salvo;
		});
	}

	private void salvarCompra(PeriodoModel periodo, ProdutoModel produto, ItemReciboDTO item, LocalDate dataCompra) {
		BigDecimal qtd = item.getQuantidade() != null && item.getQuantidade().signum() > 0
				? item.getQuantidade()
				: BigDecimal.ONE;
		BigDecimal preco = item.getPrecoUnitario();
		if (preco == null || preco.signum() <= 0) {
			return;
		}
		CompraModel compra = new CompraModel();
		compra.setPeriodo(periodo);
		compra.setProduto(produto);
		compra.setQuantidade(qtd);
		compra.setPrecoUnitario(preco.setScale(2, RoundingMode.HALF_UP));
		compra.setDataCompra(dataCompra);
		compraRepository.save(compra);
	}

	private String normalizar(String nome) {
		return nome.replaceAll("\\s+", " ").trim();
	}
}