package com.estoq.business.relatorio;

import com.estoq.api.relatorio.AlertaEstoque;
import com.estoq.api.relatorio.ConsumoMatriz;
import com.estoq.api.relatorio.ConsumoMatrizItem;
import com.estoq.api.relatorio.DashboardDTO;
import com.estoq.api.relatorio.ItemRelatorioCMV;
import com.estoq.api.relatorio.RelatorioCMVMensal;
import com.estoq.api.relatorio.RelatorioCMVPeriodo;
import com.estoq.business.compra.CompraModel;
import com.estoq.business.compra.ICompraRepository;
import com.estoq.business.estoque.EstoquePeriodoModel;
import com.estoq.business.estoque.IEstoquePeriodoRepository;
import com.estoq.business.periodo.IPeriodoRepository;
import com.estoq.business.periodo.PeriodoModel;
import com.estoq.business.periodo.PeriodoStatus;
import com.estoq.business.produto.IProdutoRepository;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.helpers.NumeroUtil;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RelatorioService {

	private static final int LIMITE_ALERTAS = 10;

	@Autowired
	private IProdutoRepository produtoRepository;

	@Autowired
	private IPeriodoRepository periodoRepository;

	@Autowired
	private ICompraRepository compraRepository;

	@Autowired
	private IEstoquePeriodoRepository estoqueRepository;

	@Transactional(readOnly = true)
	public RelatorioCMVPeriodo relatorioPeriodo(UUID periodoId) {
		PeriodoModel periodo = periodoRepository.findByIdAndAtivoTrue(periodoId).orElseThrow(() ->
				new BusinessException("Período não encontrado.", HttpStatus.NOT_FOUND));

		RelatorioCMVPeriodo rel = new RelatorioCMVPeriodo();
		rel.setPeriodoId(periodo.getId());
		rel.setPeriodoNome(periodo.getNome());
		rel.setDataInicio(periodo.getDataInicio());
		rel.setDataFim(periodo.getDataFim());
		rel.setVendas(NumeroUtil.money(periodo.getVendas()));

		List<CompraModel> compras = compraRepository.findAllByPeriodoAndAtivoTrueOrderByDataCompraAsc(periodo);
		Map<UUID, List<CompraModel>> comprasPorProduto = agruparPorProduto(compras);

		List<EstoquePeriodoModel> estoques = estoqueRepository.findAllByPeriodoAndAtivoTrue(periodo);
		Map<UUID, EstoquePeriodoModel> estoquePorProduto = new HashMap<>();
		for (EstoquePeriodoModel ep : estoques) {
			estoquePorProduto.put(ep.getProduto().getId(), ep);
		}

		BigDecimal totalInicial = BigDecimal.ZERO;
		BigDecimal totalComprasValor = BigDecimal.ZERO;
		BigDecimal totalFinal = BigDecimal.ZERO;

		for (ProdutoModel produto : produtoRepository.findAllByAtivoTrue(PageRequest.of(0, Integer.MAX_VALUE)).getContent()) {
			ItemRelatorioCMV item = montarItem(periodo, produto,
					comprasPorProduto.getOrDefault(produto.getId(), List.of()),
					estoquePorProduto.get(produto.getId()));

			boolean temDado = (item.getComprasQtd() != null && item.getComprasQtd().signum() > 0)
					|| (item.getEstoqueInicialQtd() != null && item.getEstoqueInicialQtd().signum() > 0)
					|| (item.getEstoqueFinalQtd() != null && item.getEstoqueFinalQtd().signum() > 0);
			if (!temDado) {
				continue;
			}

			rel.getItens().add(item);
			totalInicial = totalInicial.add(item.getEstoqueInicialValor());
			totalComprasValor = totalComprasValor.add(item.getComprasValor());
			totalFinal = totalFinal.add(item.getEstoqueFinalValor());
		}

		rel.getItens().sort(Comparator.comparing(ItemRelatorioCMV::getCategoria)
				.thenComparing(ItemRelatorioCMV::getProdutoNome));

		rel.setTotalEstoqueInicial(NumeroUtil.money(totalInicial));
		rel.setTotalCompras(NumeroUtil.money(totalComprasValor));
		rel.setTotalEstoqueFinal(NumeroUtil.money(totalFinal));
		rel.setTotalConsumo(NumeroUtil.money(totalInicial.add(totalComprasValor).subtract(totalFinal)));
		rel.setCmv(NumeroUtil.percentual(rel.getTotalConsumo(), rel.getVendas()));

		return rel;
	}

	private ItemRelatorioCMV montarItem(PeriodoModel periodo, ProdutoModel produto,
			List<CompraModel> compras, EstoquePeriodoModel estoque) {
		ItemRelatorioCMV item = new ItemRelatorioCMV();
		item.setProdutoId(produto.getId());
		item.setProdutoNome(produto.getNome());
		item.setUnidade(produto.getUnidade());
		item.setCategoria(produto.getCategoria());

		// --- Compras: soma de quantidades + valor; preço unitário médio ---
		BigDecimal comprasQtd = BigDecimal.ZERO;
		BigDecimal comprasValor = BigDecimal.ZERO;
		for (CompraModel c : compras) {
			comprasQtd = comprasQtd.add(NumeroUtil.s(c.getQuantidade()));
			comprasValor = comprasValor.add(NumeroUtil.multiplica(c.getQuantidade(), c.getPrecoUnitario()));
		}
		item.setComprasQtd(comprasQtd.setScale(3, java.math.RoundingMode.HALF_UP));
		item.setComprasValor(NumeroUtil.money(comprasValor));
		item.setComprasValorUnitarioMedio(
				NumeroUtil.divide(comprasValor, comprasQtd, 4));

		// --- Estoque inicial e final ---
		BigDecimal qtdInicial = estoque != null ? NumeroUtil.s(estoque.getQuantidadeInicial()) : BigDecimal.ZERO;
		BigDecimal qtdFinal = estoque != null ? NumeroUtil.s(estoque.getQuantidadeFinal()) : BigDecimal.ZERO;

		BigDecimal valorUnitInicial = NumeroUtil.s(estoque != null ? estoque.getValorUnitarioInicial() : null);
		if (valorUnitInicial.signum() == 0) {
			valorUnitInicial = precoReferenciaAnterior(produto.getId(), periodo.getDataInicio());
		}
		BigDecimal valorUnitFinal = NumeroUtil.s(estoque != null ? estoque.getValorUnitarioFinal() : null);
		if (valorUnitFinal.signum() == 0 && !compras.isEmpty()) {
			valorUnitFinal = compras.get(compras.size() - 1).getPrecoUnitario();
		}
		if (valorUnitFinal.signum() == 0) {
			valorUnitFinal = valorUnitInicial;
		}

		item.setEstoqueInicialQtd(qtdInicial.setScale(3, java.math.RoundingMode.HALF_UP));
		item.setEstoqueInicialValor(NumeroUtil.money(qtdInicial.multiply(valorUnitInicial)));
		item.setEstoqueFinalQtd(qtdFinal.setScale(3, java.math.RoundingMode.HALF_UP));
		item.setEstoqueFinalValorUnitario(NumeroUtil.money(valorUnitFinal));
		item.setEstoqueFinalValor(NumeroUtil.money(qtdFinal.multiply(valorUnitFinal)));

		// --- Consumo = estoque inicial + compras - estoque final ---
		BigDecimal consumoQtd = qtdInicial.add(comprasQtd).subtract(qtdFinal);
		item.setConsumoQtd(consumoQtd.setScale(3, java.math.RoundingMode.HALF_UP));
		item.setConsumoValor(NumeroUtil.money(item.getEstoqueInicialValor()
				.add(item.getComprasValor())
				.subtract(item.getEstoqueFinalValor())));

		return item;
	}

	private BigDecimal precoReferenciaAnterior(UUID produtoId, LocalDate dataInicio) {
		return compraRepository
				.findFirstByProdutoIdAndDataCompraBeforeAndAtivoTrueOrderByDataCompraDesc(produtoId, dataInicio)
				.map(CompraModel::getPrecoUnitario)
				.orElse(BigDecimal.ZERO);
	}

	private Map<UUID, List<CompraModel>> agruparPorProduto(List<CompraModel> compras) {
		Map<UUID, List<CompraModel>> map = new LinkedHashMap<>();
		for (CompraModel c : compras) {
			map.computeIfAbsent(c.getProduto().getId(), k -> new ArrayList<>()).add(c);
		}
		return map;
	}

	@Transactional(readOnly = true)
	public RelatorioCMVMensal relatorioMensal(int ano, int mes) {
		YearMonth ym = YearMonth.of(ano, mes);

		RelatorioCMVMensal rel = new RelatorioCMVMensal();
		rel.setAno(ano);
		rel.setMes(mes);

		List<PeriodoModel> periodos = periodoRepository.findAllByAtivoTrueAndDataInicioBetweenOrderByDataInicioAsc(
				ym.atDay(1), ym.atEndOfMonth());

		BigDecimal totalInicial = BigDecimal.ZERO;
		BigDecimal totalCompras = BigDecimal.ZERO;
		BigDecimal totalFinal = BigDecimal.ZERO;
		BigDecimal vendas = BigDecimal.ZERO;

		for (PeriodoModel p : periodos) {
			RelatorioCMVPeriodo rp = relatorioPeriodo(p.getId());
			rel.getPeriodos().add(rp);
			totalInicial = totalInicial.add(rp.getTotalEstoqueInicial());
			totalCompras = totalCompras.add(rp.getTotalCompras());
			totalFinal = totalFinal.add(rp.getTotalEstoqueFinal());
			vendas = vendas.add(rp.getVendas());
		}

		rel.setTotalEstoqueInicial(NumeroUtil.money(totalInicial));
		rel.setTotalCompras(NumeroUtil.money(totalCompras));
		rel.setTotalEstoqueFinal(NumeroUtil.money(totalFinal));
		rel.setTotalConsumo(NumeroUtil.money(totalInicial.add(totalCompras).subtract(totalFinal)));
		rel.setVendas(NumeroUtil.money(vendas));
		rel.setCmv(NumeroUtil.percentual(rel.getTotalConsumo(), rel.getVendas()));

		return rel;
	}

	@Transactional(readOnly = true)
	public ConsumoMatriz matrizConsumo(int ano, int mes) {
		YearMonth ym = YearMonth.of(ano, mes);
		List<PeriodoModel> periodos = periodoRepository.findAllByAtivoTrueAndDataInicioBetweenOrderByDataInicioAsc(
				ym.atDay(1), ym.atEndOfMonth());

		ConsumoMatriz matriz = new ConsumoMatriz();
		for (PeriodoModel p : periodos) {
			ConsumoMatriz.ColunaPeriodo col = new ConsumoMatriz.ColunaPeriodo();
			col.setPeriodoId(p.getId());
			col.setPeriodoNome(p.getNome());
			col.setDataInicio(p.getDataInicio());
			col.setDataFim(p.getDataFim());
			matriz.getPeriodos().add(col);
		}

		for (ProdutoModel produto : produtoRepository.findAllByAtivoTrue(PageRequest.of(0, Integer.MAX_VALUE)).getContent()) {
			ConsumoMatrizItem item = new ConsumoMatrizItem();
			item.setProdutoId(produto.getId());
			item.setProdutoNome(produto.getNome());
			item.setUnidade(produto.getUnidade());
			item.setCategoria(produto.getCategoria());
			item.setEstoqueMinimo(NumeroUtil.s(produto.getEstoqueMinimo()));

			BigDecimal[] consumo = new BigDecimal[periodos.size()];
			BigDecimal consumoTotal = BigDecimal.ZERO;
			for (int i = 0; i < periodos.size(); i++) {
				PeriodoModel p = periodos.get(i);
				BigDecimal c = consumoProdutoPeriodo(p, produto.getId());
				consumo[i] = c == null ? null : c.setScale(3, java.math.RoundingMode.HALF_UP);
				if (c != null) {
					consumoTotal = consumoTotal.add(c);
				}
			}
			item.setConsumoPorPeriodo(consumo);
			item.setConsumoTotal(consumoTotal.setScale(3, java.math.RoundingMode.HALF_UP));
			matriz.getItens().add(item);
		}
		return matriz;
	}

	/** Consumo (em quantidade) de um produto em um período; null quando não há dados. */
	private BigDecimal consumoProdutoPeriodo(PeriodoModel periodo, UUID produtoId) {
		List<CompraModel> compras = compraRepository.findAllByPeriodoIdAndProdutoIdAndAtivoTrue(
				periodo.getId(), produtoId);
		Optional<EstoquePeriodoModel> optEstoque = estoqueRepository.findByPeriodoIdAndProdutoId(
				periodo.getId(), produtoId);

		if (optEstoque.isEmpty() && compras.isEmpty()) {
			return null;
		}
		BigDecimal qtdInicial = optEstoque.map(e -> NumeroUtil.s(e.getQuantidadeInicial())).orElse(BigDecimal.ZERO);
		BigDecimal qtdFinal = optEstoque.map(e -> NumeroUtil.s(e.getQuantidadeFinal())).orElse(BigDecimal.ZERO);
		BigDecimal compraQtd = compras.stream()
				.map(c -> NumeroUtil.s(c.getQuantidade()))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return qtdInicial.add(compraQtd).subtract(qtdFinal);
	}

	@Transactional(readOnly = true)
	public List<AlertaEstoque> alertasEstoque() {
		List<AlertaEstoque> alertas = new ArrayList<>();
		List<PeriodoModel> periodos = periodoRepository.findAllByAtivoTrueOrderByDataInicioAsc();

		for (ProdutoModel produto : produtoRepository.findAllByAtivoTrue(PageRequest.of(0, Integer.MAX_VALUE)).getContent()) {
			AlertaEstoque alerta = montarAlerta(produto, periodos);
			alertas.add(alerta);
		}
		alertas.sort(Comparator.comparing(AlertaEstoque::getStatus)
				.thenComparing(AlertaEstoque::getProdutoNome));
		return alertas;
	}

	private AlertaEstoque montarAlerta(ProdutoModel produto, List<PeriodoModel> periodos) {
		AlertaEstoque alerta = new AlertaEstoque();
		alerta.setProdutoId(produto.getId());
		alerta.setProdutoNome(produto.getNome());
		alerta.setUnidade(produto.getUnidade());
		alerta.setCategoria(produto.getCategoria());
		alerta.setEstoqueMinimo(NumeroUtil.s(produto.getEstoqueMinimo()));

		BigDecimal estoqueAtual = BigDecimal.ZERO;
		boolean achouEstoque = false;
		List<BigDecimal> consumos = new ArrayList<>();

		for (int i = periodos.size() - 1; i >= 0; i--) {
			PeriodoModel p = periodos.get(i);
			Optional<EstoquePeriodoModel> opt = estoqueRepository.findByPeriodoIdAndProdutoId(p.getId(), produto.getId());
			if (opt.isPresent()) {
				EstoquePeriodoModel ep = opt.get();
				BigDecimal fin = NumeroUtil.s(ep.getQuantidadeFinal());
				if (!achouEstoque && (fin.signum() > 0 || NumeroUtil.s(ep.getQuantidadeInicial()).signum() > 0)) {
					estoqueAtual = fin.signum() > 0 ? fin : NumeroUtil.s(ep.getQuantidadeInicial());
					achouEstoque = true;
				}
			}
			BigDecimal consumo = consumoProdutoPeriodo(p, produto.getId());
			if (consumo != null) {
				consumos.add(consumo);
			}
		}

		if (!achouEstoque && consumos.isEmpty()) {
			alerta.setEstoqueAtual(BigDecimal.ZERO);
			alerta.setConsumoMedioSemanal(BigDecimal.ZERO);
			alerta.setStatus(AlertaEstoque.SEM_DADOS);
			return alerta;
		}

		// média das últimas (até 4) semanas com dados
		int limite = Math.min(consumos.size(), 4);
		BigDecimal soma = BigDecimal.ZERO;
		for (int i = consumos.size() - limite; i < consumos.size(); i++) {
			soma = soma.add(consumos.get(i));
		}
		BigDecimal consumoMedio = limite == 0 ? BigDecimal.ZERO
				: soma.divide(BigDecimal.valueOf(limite), 3, java.math.RoundingMode.HALF_UP);

		alerta.setEstoqueAtual(estoqueAtual.setScale(3, java.math.RoundingMode.HALF_UP));
		alerta.setConsumoMedioSemanal(consumoMedio);

		BigDecimal minimo = alerta.getEstoqueMinimo();
		if (estoqueAtual.signum() <= 0 || estoqueAtual.compareTo(minimo) < 0) {
			alerta.setStatus(AlertaEstoque.REPOR);
		} else if (estoqueAtual.compareTo(minimo.multiply(BigDecimal.valueOf(1.5))) <= 0) {
			alerta.setStatus(AlertaEstoque.ATENCAO);
		} else {
			alerta.setStatus(AlertaEstoque.OK);
		}
		return alerta;
	}

	@Transactional(readOnly = true)
	public DashboardDTO dashboard(int ano, int mes) {
		DashboardDTO dto = new DashboardDTO();
		dto.setCmvMeta(BigDecimal.valueOf(0.40));

		RelatorioCMVMensal mensal = relatorioMensal(ano, mes);
		dto.setCmvDoMes(mensal.getCmv());
		dto.setConsumoDoMes(mensal.getTotalConsumo());
		dto.setVendasDoMes(mensal.getVendas());

		dto.setTotalProdutos(produtoRepository.count());

		List<AlertaEstoque> alertas = alertasEstoque();
		long baixos = alertas.stream()
				.filter(a -> AlertaEstoque.REPOR.equals(a.getStatus()) || AlertaEstoque.ATENCAO.equals(a.getStatus()))
				.count();
		dto.setProdutosComEstoqueBaixo(baixos);

		dto.setPeriodosAbertos(periodoRepository.findAllByAtivoTrue(PageRequest.of(0, Integer.MAX_VALUE)).getContent()
				.stream().filter(p -> p.getStatus() == PeriodoStatus.ABERTO).count());

		dto.setComprasNoMes(comprasNoMes(ano, mes));

		alertas.stream()
				.filter(a -> AlertaEstoque.REPOR.equals(a.getStatus()) || AlertaEstoque.ATENCAO.equals(a.getStatus()))
				.limit(LIMITE_ALERTAS)
				.forEach(dto.getPrincipaisAlertas()::add);

		return dto;
	}

	private long comprasNoMes(int ano, int mes) {
		YearMonth ym = YearMonth.of(ano, mes);
		List<UUID> ids = periodoRepository
				.findAllByAtivoTrueAndDataInicioBetweenOrderByDataInicioAsc(ym.atDay(1), ym.atEndOfMonth())
				.stream().map(PeriodoModel::getId).toList();
		if (ids.isEmpty()) {
			return 0;
		}
		return compraRepository.countByPeriodoIdInAndAtivoTrue(ids);
	}
}