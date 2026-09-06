package com.estoq.business.relatorio;

import com.estoq.business.alerta.AlertaView;
import com.estoq.business.auditoria.AuditService;
import com.estoq.business.balanco.ConferenciaService;
import com.estoq.business.configuracao.ConfiguracaoService;
import com.estoq.business.lote.ILoteRepository;
import com.estoq.business.lote.LoteModel;
import com.estoq.business.movimentacao.ConsumoModel;
import com.estoq.business.movimentacao.DesperdicioModel;
import com.estoq.business.movimentacao.EntradaModel;
import com.estoq.business.movimentacao.IMovimentacaoEstoqueRepository;
import com.estoq.business.movimentacao.MovimentacaoEstoqueModel;
import com.estoq.business.movimentacao.MovimentacaoService;
import com.estoq.business.parametro.IParametroEstoqueRepository;
import com.estoq.business.parametro.ParametroEstoqueModel;
import com.estoq.business.produto.IProdutoRepository;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.business.produtoaberto.IProdutoAbertoRepository;
import com.estoq.business.produtoaberto.ProdutoAbertoModel;
import com.estoq.business.relatorio.CMVItemDTO;
import com.estoq.business.relatorio.CMVReportDTO;
import com.estoq.business.relatorio.DashboardDTO;
import com.estoq.business.relatorio.RelatorioLinhaDTO;
import com.estoq.business.relatorio.RelatorioViewDTO;
import com.estoq.business.sessao.SessaoService;
import com.estoq.business.sistema.BackupService;
import com.estoq.business.sistema.BackupStatus;
import com.estoq.business.venda.VendaMesService;
import com.estoq.core.helpers.NumeroUtil;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.reports.PdfReporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RelatorioService {

	private static final BigDecimal META_CMV = new BigDecimal("0.40");
	private static final int DIAS_ALERTA_VENCIMENTO = 7;

	@Autowired
	private IMovimentacaoEstoqueRepository movRepository;

	@Autowired
	private IProdutoRepository produtoRepository;

	@Autowired
	private ILoteRepository loteRepository;

	@Autowired
	private IProdutoAbertoRepository produtoAbertoRepository;

	@Autowired
	private IParametroEstoqueRepository parametroRepository;

	@Autowired
	private IRelatorioRepository relatorioRepository;

	@Autowired
	private MovimentacaoService movimentacaoService;

	@Autowired
	private ConferenciaService conferenciaService;

	@Autowired
	private com.estoq.business.alerta.AlertaService alertaService;

	@Autowired
	private VendaMesService vendaMesService;

	@Autowired
	private BackupService backupService;

	@Autowired
	private ConfiguracaoService configuracaoService;

	@Autowired
	private AuditService auditService;

	@Autowired
	private SessaoService sessaoService;

	// ------------------------------------------------------------ CMV

	/**
	 * CMV calculado como na planilha: CMV = Estoque Inicial + Entradas − Estoque
	 * Final do período, por produto. O que saiu do estoque sem ser lançado como
	 * consumo é tratado como desperdício real.
	 */
	@Transactional(readOnly = true)
	public CMVReportDTO cmv(LocalDate inicio, LocalDate fim, BigDecimal vendas) {
		LocalDate ini = inicio == null ? YearMonth.now().atDay(1) : inicio;
		LocalDate f = fim == null ? LocalDate.now() : fim;

		CMVReportDTO rel = new CMVReportDTO();
		rel.setDataInicio(ini);
		rel.setDataFim(f);
		rel.setVendas(resolverVendas(ini, f, vendas));
		rel.setMetaCmv(configuracaoService.obterBigDecimal(ConfiguracaoService.META_CMV, META_CMV));

		LocalDateTime iniDt = ini.atStartOfDay();
		LocalDateTime fimDt = f.plusDays(1).atStartOfDay();

		// agrega entradas/consumos/desperdícios por produto no período
		Map<UUID, CMVItemDTO> porProduto = new HashMap<>();
		for (ProdutoModel p : produtoRepository.findAllByAtivoTrue(PageRequest.of(0, Integer.MAX_VALUE)).getContent()) {
			porProduto.put(p.getId(), montarItem(p));
		}
		for (MovimentacaoEstoqueModel m : movRepository.findAllByDataHoraBetweenOrderByDataHoraAsc(iniDt, fimDt)) {
			if (m.getProduto() == null) {
				continue;
			}
			CMVItemDTO item = porProduto.computeIfAbsent(m.getProduto().getId(), k -> montarItem(m.getProduto()));
			if (m instanceof EntradaModel e) {
				item.setEntradasQtd(item.getEntradasQtd().add(NumeroUtil.s(e.getQuantidade())));
				item.setEntradasValor(item.getEntradasValor().add(NumeroUtil.s(e.getValorTotalPago())));
			} else if (m instanceof ConsumoModel c) {
				item.setConsumoQtd(item.getConsumoQtd().add(NumeroUtil.s(c.getQuantidade())));
				item.setConsumoValor(item.getConsumoValor().add(NumeroUtil.s(c.getCustoConsumo())));
			} else if (m instanceof DesperdicioModel d) {
				item.setDesperdicioQtd(item.getDesperdicioQtd().add(NumeroUtil.s(d.getQuantidade())));
				item.setDesperdicioValor(item.getDesperdicioValor().add(NumeroUtil.s(d.getValorPrejuizo())));
			}
		}

		List<CMVItemDTO> itens = new ArrayList<>(porProduto.values());
		for (CMVItemDTO item : itens) {
			UUID pid = item.getProdutoId();
			BigDecimal custo = NumeroUtil.s(movimentacaoService.custoMedioProduto(pid));
			BigDecimal eiQtd = movimentacaoService.obterSaldoEmData(pid, iniDt);
			BigDecimal efQtd = movimentacaoService.obterSaldoEmData(pid, fimDt);

			// CMV real (fórmula da planilha): Estoque Inicial + Entradas − Estoque Final
			BigDecimal cmvRealQtd = eiQtd.add(item.getEntradasQtd()).subtract(efQtd);
			BigDecimal eiValor = NumeroUtil.multiplica(eiQtd, custo);
			BigDecimal efValor = NumeroUtil.multiplica(efQtd, custo);
			BigDecimal cmvRealValor = eiValor.add(item.getEntradasValor()).subtract(efValor);

			item.setEstoqueInicialQtd(eiQtd);
			item.setEstoqueInicialValor(eiValor);
			item.setEstoqueFinalQtd(efQtd);
			item.setEstoqueFinalValor(efValor);

			// desperdício real: tudo que saiu sem ser lançado como consumo
			BigDecimal despRealQtd = cmvRealQtd.subtract(item.getConsumoQtd());
			BigDecimal despRealValor = cmvRealValor.subtract(item.getConsumoValor());
			if (despRealQtd.signum() < 0) {
				despRealQtd = BigDecimal.ZERO;
			}
			if (despRealValor.signum() < 0) {
				despRealValor = BigDecimal.ZERO;
			}

			item.setDesperdicioQtd(despRealQtd);
			item.setDesperdicioValor(despRealValor);
			item.setTotalValor(cmvRealValor);
		}

		itens.sort(Comparator.comparing(CMVItemDTO::getProdutoNome));
		BigDecimal totalConsumo = BigDecimal.ZERO;
		BigDecimal totalDesperdicio = BigDecimal.ZERO;
		BigDecimal totalGeral = BigDecimal.ZERO;
		for (CMVItemDTO item : itens) {
			totalConsumo = totalConsumo.add(item.getConsumoValor());
			totalDesperdicio = totalDesperdicio.add(item.getDesperdicioValor());
			totalGeral = totalGeral.add(item.getTotalValor());
		}
		rel.setItens(itens);
		rel.setTotalConsumo(NumeroUtil.money(totalConsumo));
		rel.setTotalDesperdicio(NumeroUtil.money(totalDesperdicio));
		rel.setTotalGeral(NumeroUtil.money(totalGeral));
		if (rel.getVendas().signum() > 0) {
			rel.setCmv(NumeroUtil.percentual(rel.getTotalGeral(), rel.getVendas()));
		}
		definirAvaliacao(rel);
		return rel;
	}

	private void definirAvaliacao(CMVReportDTO rel) {
		BigDecimal vendas = NumeroUtil.s(rel.getVendas());
		BigDecimal meta = NumeroUtil.s(rel.getMetaCmv());
		BigDecimal pct = NumeroUtil.s(rel.getCmv());
		BigDecimal despPct = NumeroUtil.percentual(rel.getTotalDesperdicio(), rel.getTotalGeral());

		String despSufixo = despPct.compareTo(new BigDecimal("0.10")) > 0
				? " Desperdício responde por " + pctStr(despPct) + " do CMV."
				: "";

		if (vendas.signum() <= 0) {
			rel.setAvaliacao("SEM_VENDAS");
			rel.setMensagem("SEM VENDAS — informe as vendas (R$) do período para calcular o percentual de CMV.");
			return;
		}
		if (meta.signum() <= 0) {
			rel.setAvaliacao("ATENCAO");
			rel.setMensagem("ATENÇÃO — não há meta de CMV definida para comparar. Calcule o CMV ("
					+ pctStr(pct) + ") e defina uma meta de controle.");
			return;
		}

		String base = "CMV em " + pctStr(pct) + " (meta: " + pctStr(meta) + ").";
		if (pct.compareTo(meta.multiply(new BigDecimal("0.90"))) <= 0) {
			rel.setAvaliacao("EXCELENTE");
			rel.setMensagem("EXCELENTE — " + base + " Bem abaixo da meta; estoque e compras sob controle."
					+ despSufixo);
		} else if (pct.compareTo(meta) <= 0) {
			rel.setAvaliacao("DENTRO_DA_META");
			rel.setMensagem("DENTRO DA META — " + base + " Resultado aceitável; siga acompanhando consumo e perdas."
					+ despSufixo);
		} else if (pct.compareTo(meta.multiply(new BigDecimal("1.15"))) <= 0) {
			rel.setAvaliacao("ATENCAO");
			rel.setMensagem("ATENÇÃO — " + base + " Acima da meta; revise compras, precificação e desperdícios."
					+ despSufixo);
		} else {
			rel.setAvaliacao("CRITICO");
			rel.setMensagem("CRÍTICO — " + base
					+ " Muito acima da meta; ação imediata em compras, preços, consumo e perdas." + despSufixo);
		}
	}

	private String pctStr(BigDecimal frac) {
		return NumeroUtil.money(NumeroUtil.multiplica(NumeroUtil.s(frac), BigDecimal.valueOf(100)))
				.stripTrailingZeros().toPlainString() + "%";
	}

	/**
	 * Vendas do período. Quando o parâmetro não vem preenchido e o período é um
	 * mês completo, usa o valor persistido em /api/vendas.
	 */
	private BigDecimal resolverVendas(LocalDate ini, LocalDate fim, BigDecimal vendas) {
		if (vendas != null) {
			return NumeroUtil.money(vendas);
		}
		if (ini == null || fim == null) {
			return BigDecimal.ZERO;
		}
		YearMonth ym = YearMonth.from(ini);
		if (!ym.atDay(1).equals(ini) || !ym.atEndOfMonth().equals(fim)) {
			return BigDecimal.ZERO;
		}
		return NumeroUtil.money(vendaMesService.obter(ini.getYear(), ini.getMonthValue()).getValorVendas());
	}

	private CMVItemDTO montarItem(ProdutoModel p) {
		CMVItemDTO dto = new CMVItemDTO();
		dto.setProdutoId(p.getId());
		dto.setProdutoNome(p.getNome());
		dto.setCategoriaNome(p.getCategoria() != null ? p.getCategoria().getNome() : null);
		dto.setUnidadeMedida(p.getUnidadeMedida() != null ? p.getUnidadeMedida().name() : null);
		dto.setEntradasQtd(BigDecimal.ZERO);
		dto.setEntradasValor(BigDecimal.ZERO);
		dto.setConsumoQtd(BigDecimal.ZERO);
		dto.setConsumoValor(BigDecimal.ZERO);
		dto.setDesperdicioQtd(BigDecimal.ZERO);
		dto.setDesperdicioValor(BigDecimal.ZERO);
		dto.setTotalValor(BigDecimal.ZERO);
		return dto;
	}

	// ------------------------------------------------------------ relatórios por tipo

	@Transactional
	public RelatorioViewDTO gerarTipo(TipoRelatorio tipo, LocalDate inicio, LocalDate fim) {
		LocalDate ini = inicio == null ? YearMonth.now().atDay(1) : inicio;
		LocalDate f = fim == null ? LocalDate.now() : fim;

		List<RelatorioLinhaDTO> linhas = montarLinhas(tipo, ini, f);

		RelatorioModel r = new RelatorioModel();
		r.setTipo(tipo);
		r.setDataInicio(ini);
		r.setDataFim(f);
		r.setDataGeracao(LocalDateTime.now());
		r.setLinhasGeradas(linhas.size());
		relatorioRepository.save(r);

		RelatorioViewDTO view = new RelatorioViewDTO();
		view.setId(r.getId());
		view.setTipo(tipo.name());
		view.setDataInicio(ini);
		view.setDataFim(f);
		view.setDataGeracao(r.getDataGeracao());
		view.setLinhasGeradas(linhas.size());
		view.setLinhas(linhas);
		return view;
	}

	private List<RelatorioLinhaDTO> montarLinhas(TipoRelatorio tipo, LocalDate ini, LocalDate f) {
		return switch (tipo) {
			case ESTOQUE_ATUAL -> estoqueAtual();
			case PROXIMO_VENCIMENTO -> vencendo(f);
			case VENCIDOS -> vencidos();
			case PRODUTOS_ABERTOS -> produtosAbertos();
			case DESPERDICIO -> desperdicios(ini, f);
			case CONSUMO_MEDIO -> consumoMedio(ini, f);
		};
	}

	private List<RelatorioLinhaDTO> estoqueAtual() {
		List<RelatorioLinhaDTO> linhas = new ArrayList<>();
		for (ProdutoModel p : produtoRepository.findAllByAtivoTrue(PageRequest.of(0, Integer.MAX_VALUE)).getContent()) {
			BigDecimal saldo = movimentacaoService.obterSaldo(p.getId());
			RelatorioLinhaDTO l = new RelatorioLinhaDTO();
			l.setChave(p.getNome());
			l.setDetalhe(p.getCategoria() != null ? p.getCategoria().getNome() : "");
			l.setUnidadeMedida(p.getUnidadeMedida() != null ? p.getUnidadeMedida().name() : "");
			l.setQuantidade(saldo);
			l.setValor(movimentacaoService.custoMedioProduto(p.getId()).multiply(saldo)
					.setScale(2, RoundingMode.HALF_UP));
			l.setStatus(saldo.signum() <= 0 ? "SEM_ESTOQUE" : "OK");
			linhas.add(l);
		}
		return linhas;
	}

	private List<RelatorioLinhaDTO> vencendo(LocalDate fim) {
		List<RelatorioLinhaDTO> linhas = new ArrayList<>();
		LocalDate limite = fim.plusDays(DIAS_ALERTA_VENCIMENTO);
		for (LoteModel lote : loteRepository.findAllByAtivoTrueAndDataValidadeBetweenOrderByDataValidadeAsc(
				LocalDate.now(), limite)) {
			if (lote.estaVencido() || !lote.estaDisponivel()) {
				continue;
			}
			RelatorioLinhaDTO l = new RelatorioLinhaDTO();
			l.setChave(lote.getCodigo());
			l.setDetalhe(lote.getProduto() != null ? lote.getProduto().getNome() : "");
			l.setUnidadeMedida(lote.getProduto() != null && lote.getProduto().getUnidadeMedida() != null
					? lote.getProduto().getUnidadeMedida().name()
					: "");
			l.setQuantidade(lote.getQuantidadeAtual());
			l.setValor(lote.getPrecoUnitario().multiply(lote.getQuantidadeAtual())
					.setScale(2, RoundingMode.HALF_UP));
			l.setData(Date.valueOf(lote.getDataValidade()).toString());
			l.setStatus("VENCE EM " + lote.diasParaVencimento() + " DIA(S)");
			linhas.add(l);
		}
		linhas.sort(Comparator.comparing(RelatorioLinhaDTO::getData, Comparator.nullsLast(Comparator.naturalOrder())));
		return linhas;
	}

	private List<RelatorioLinhaDTO> vencidos() {
		List<RelatorioLinhaDTO> linhas = new ArrayList<>();
		for (LoteModel lote : loteRepository.findAllByAtivoTrueAndDataValidadeBeforeOrderByDataValidadeAsc(
				LocalDate.now())) {
			if (!lote.estaVencido()) {
				continue;
			}
			RelatorioLinhaDTO l = new RelatorioLinhaDTO();
			l.setChave(lote.getCodigo());
			l.setDetalhe(lote.getProduto() != null ? lote.getProduto().getNome() : "");
			l.setUnidadeMedida(lote.getProduto() != null && lote.getProduto().getUnidadeMedida() != null
					? lote.getProduto().getUnidadeMedida().name()
					: "");
			l.setQuantidade(lote.getQuantidadeAtual());
			l.setValor(lote.getPrecoUnitario().multiply(lote.getQuantidadeAtual())
					.setScale(2, RoundingMode.HALF_UP));
			l.setData(Date.valueOf(lote.getDataValidade()).toString());
			l.setStatus("VENCIDO");
			linhas.add(l);
		}
		return linhas;
	}

	private List<RelatorioLinhaDTO> produtosAbertos() {
		List<RelatorioLinhaDTO> linhas = new ArrayList<>();
		for (ProdutoAbertoModel a : produtoAbertoRepository.findAllByFinalizadoFalseAndAtivoTrueOrderByDataAberturaAsc()) {
			RelatorioLinhaDTO l = new RelatorioLinhaDTO();
			l.setChave(a.getProduto() != null ? a.getProduto().getNome() : "?");
			l.setDetalhe("Aberto em " + a.getDataAbertura().toLocalDate());
			l.setUnidadeMedida(a.getProduto() != null && a.getProduto().getUnidadeMedida() != null
					? a.getProduto().getUnidadeMedida().name()
					: "");
			l.setQuantidade(a.getQuantidadeRestante());
			l.setValor(null);
			l.setStatus("ABERTO");
			linhas.add(l);
		}
		return linhas;
	}

	private List<RelatorioLinhaDTO> desperdicios(LocalDate ini, LocalDate f) {
		List<RelatorioLinhaDTO> linhas = new ArrayList<>();
		for (MovimentacaoEstoqueModel m : movRepository.findDesperdicios(ini.atStartOfDay(),
				f.plusDays(1).atStartOfDay())) {
			DesperdicioModel d = (DesperdicioModel) m;
			RelatorioLinhaDTO l = new RelatorioLinhaDTO();
			l.setChave(d.getProduto() != null ? d.getProduto().getNome() : "?");
			l.setDetalhe(d.getMotivo() != null ? d.getMotivo().name()
					: (d.getDescricaoMotivo() != null ? d.getDescricaoMotivo() : ""));
			l.setUnidadeMedida(d.getProduto() != null && d.getProduto().getUnidadeMedida() != null
					? d.getProduto().getUnidadeMedida().name()
					: "");
			l.setQuantidade(d.getQuantidade());
			l.setValor(NumeroUtil.s(d.getValorPrejuizo()));
			l.setData(Date.valueOf(d.getDataHora().toLocalDate()).toString());
			l.setStatus("DESPERDÍCIO");
			linhas.add(l);
		}
		linhas.sort(Comparator.comparing(RelatorioLinhaDTO::getData, Comparator.nullsLast(Comparator.naturalOrder())));
		return linhas;
	}

	private List<RelatorioLinhaDTO> consumoMedio(LocalDate ini, LocalDate f) {
		long dias = Math.max(java.time.temporal.ChronoUnit.DAYS.between(ini, f), 1);
		List<RelatorioLinhaDTO> linhas = new ArrayList<>();
		List<MovimentacaoEstoqueModel> consumos = movRepository.findConsumos(ini.atStartOfDay(),
				f.plusDays(1).atStartOfDay());
		Map<UUID, BigDecimal> porProduto = new HashMap<>();
		for (MovimentacaoEstoqueModel m : consumos) {
			if (m.getProduto() != null) {
				porProduto.merge(m.getProduto().getId(), NumeroUtil.s(m.getQuantidade()), BigDecimal::add);
			}
		}
		for (Map.Entry<UUID, BigDecimal> e : porProduto.entrySet()) {
			ProdutoModel p = produtoRepository.findByIdAndAtivoTrue(e.getKey()).orElse(null);
			if (p == null) {
				continue;
			}
			RelatorioLinhaDTO l = new RelatorioLinhaDTO();
			l.setChave(p.getNome());
			l.setDetalhe("Média diária no período");
			l.setUnidadeMedida(p.getUnidadeMedida() != null ? p.getUnidadeMedida().name() : "");
			l.setQuantidade(e.getValue().divide(BigDecimal.valueOf(dias), 3, RoundingMode.HALF_UP));
			l.setValor(null);
			l.setStatus("CONSUMO_MEDIO");
			linhas.add(l);
		}
		linhas.sort(Comparator.comparing(RelatorioLinhaDTO::getChave));
		return linhas;
	}

	// ------------------------------------------------------------ PDF

	/** Gera o PDF de um relatório por tipo (sem persistir novo registro). */
	public byte[] gerarPdfTipo(TipoRelatorio tipo, LocalDate inicio, LocalDate fim) {
		LocalDate ini = inicio == null ? YearMonth.now().atDay(1) : inicio;
		LocalDate f = fim == null ? LocalDate.now() : fim;
		List<RelatorioLinhaDTO> linhas = montarLinhas(tipo, ini, f);

		String titulo = switch (tipo) {
			case ESTOQUE_ATUAL -> "Estoque atual";
			case PROXIMO_VENCIMENTO -> "Próximo vencimento";
			case VENCIDOS -> "Vencidos";
			case PRODUTOS_ABERTOS -> "Produtos abertos";
			case DESPERDICIO -> "Desperdício";
			case CONSUMO_MEDIO -> "Consumo médio";
		};
		String[] cols = { "Item", "Detalhe", "Unidade", "Quantidade", "Valor", "Data", "Status" };
		List<String[]> linhasPdf = new ArrayList<>();
		for (RelatorioLinhaDTO l : linhas) {
			linhasPdf.add(new String[] {
					l.getChave(),
					l.getDetalhe() != null ? l.getDetalhe() : "",
					l.getUnidadeMedida() != null ? l.getUnidadeMedida() : "",
					String.valueOf(l.getQuantidade() != null ? NumeroUtil.money(l.getQuantidade()).stripTrailingZeros().toPlainString() : ""),
					NumeroUtil.money(l.getValor()).toPlainString(),
					l.getData() != null ? l.getData() : "",
					l.getStatus() != null ? l.getStatus() : ""
			});
		}
		BigDecimal totalQtd = linhas.stream().map(RelatorioLinhaDTO::getQuantidade)
				.filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal totalValor = linhas.stream().map(RelatorioLinhaDTO::getValor)
				.filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
		String[] total = { "Total", "", "", NumeroUtil.money(totalQtd).stripTrailingZeros().toPlainString(),
				NumeroUtil.money(totalValor).stripTrailingZeros().toPlainString(), "", "" };
		try {
			return PdfReporter.gerarRelatorio("EstoQ — " + titulo,
					"Período: " + ini + " a " + f + " · " + linhas.size() + " linha(s)",
					cols, linhasPdf, total);
		} catch (Exception e) {
			throw new BusinessException("Falha ao gerar o PDF do relatório.", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/** Gera o PDF do CMV do período. */
	public byte[] gerarPdfCmv(LocalDate inicio, LocalDate fim, BigDecimal vendas) {
		CMVReportDTO c = cmv(inicio, fim, vendas);
		String[] cols = { "Produto", "Categoria", "Unidade", "Est. Inicial", "Entradas",
				"Est. Final", "Consumo (R$)", "Desp. real (R$)", "CMV (R$)" };
		List<String[]> linhasPdf = new ArrayList<>();
		for (CMVItemDTO i : c.getItens()) {
			linhasPdf.add(new String[] {
					i.getProdutoNome(),
					i.getCategoriaNome() != null ? i.getCategoriaNome() : "",
					i.getUnidadeMedida() != null ? i.getUnidadeMedida() : "",
					NumeroUtil.money(i.getEstoqueInicialQtd()).stripTrailingZeros().toPlainString(),
					NumeroUtil.money(i.getEntradasQtd()).stripTrailingZeros().toPlainString(),
					NumeroUtil.money(i.getEstoqueFinalQtd()).stripTrailingZeros().toPlainString(),
					NumeroUtil.money(i.getConsumoValor()).toPlainString(),
					NumeroUtil.money(i.getDesperdicioValor()).toPlainString(),
					NumeroUtil.money(i.getTotalValor()).toPlainString()
			});
		}
		String[] total = { "Total", "", "",
				NumeroUtil.money(c.getItens().stream().map(CMVItemDTO::getEstoqueInicialQtd)
						.filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)).stripTrailingZeros().toPlainString(),
				NumeroUtil.money(c.getItens().stream().map(CMVItemDTO::getEntradasQtd)
						.filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)).stripTrailingZeros().toPlainString(),
				NumeroUtil.money(c.getItens().stream().map(CMVItemDTO::getEstoqueFinalQtd)
						.filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)).stripTrailingZeros().toPlainString(),
				NumeroUtil.money(c.getTotalConsumo()).stripTrailingZeros().toPlainString(),
				NumeroUtil.money(c.getTotalDesperdicio()).stripTrailingZeros().toPlainString(),
				NumeroUtil.money(c.getTotalGeral()).stripTrailingZeros().toPlainString() };
		try {
			return PdfReporter.gerarRelatorio("EstoQ — Relatório CMV",
					"Período: " + c.getDataInicio() + " a " + c.getDataFim()
							+ (c.getVendas() != null && c.getVendas().signum() > 0
									? " · Vendas: " + NumeroUtil.money(c.getVendas())
									: " · Vendas não informadas")
							+ (c.getCmv() != null ? " · % CMV: "
									+ NumeroUtil.money(NumeroUtil.multiplica(c.getCmv(), BigDecimal.valueOf(100)))
											.stripTrailingZeros().toPlainString() + "%" : ""),
					cols, linhasPdf, total);
		} catch (Exception e) {
			throw new BusinessException("Falha ao gerar o PDF do CMV.", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	// ------------------------------------------------------------ histórico e dashboard

	@Transactional(readOnly = true)
	public List<RelatorioModel> historico() {
		return relatorioRepository.findAllByAtivoTrueOrderByDataGeracaoDesc();
	}

	@Transactional(readOnly = true)
	public DashboardDTO dashboard(int ano, int mes) {
		YearMonth ym = YearMonth.of(ano, mes);
		CMVReportDTO cmvMes = cmv(ym.atDay(1), ym.atEndOfMonth(), null);

		DashboardDTO dto = new DashboardDTO();
		dto.setAno(ano);
		dto.setMes(mes);
		dto.setMetaCmv(configuracaoService.obterBigDecimal(ConfiguracaoService.META_CMV, META_CMV));
		dto.setConsumoMes(cmvMes.getTotalConsumo());
		dto.setDesperdicioMes(cmvMes.getTotalDesperdicio());
		dto.setCmvMes(cmvMes.getTotalGeral());
		dto.setVendasMes(NumeroUtil.money(cmvMes.getVendas()));
		dto.setTotalProdutos(produtoRepository.count());

		BigDecimal metaDesperdicio = configuracaoService.obterBigDecimal(
				ConfiguracaoService.META_DESPERDICIO, new BigDecimal("0.10"));
		dto.setMetaDesperdicio(metaDesperdicio);
		dto.setDesperdicioPct(NumeroUtil.percentual(dto.getDesperdicioMes(), dto.getCmvMes()));

		long baixo = 0;
		for (ProdutoModel p : produtoRepository.findAllByAtivoTrue(PageRequest.of(0, Integer.MAX_VALUE)).getContent()) {
			ParametroEstoqueModel par = parametroRepository.findByProduto_IdAndAtivoTrue(p.getId()).orElse(null);
			BigDecimal minimo = NumeroUtil.s(par != null ? par.getEstoqueMinimo() : null);
			if (minimo.signum() > 0 && movimentacaoService.obterSaldo(p.getId()).compareTo(minimo) < 0) {
				baixo++;
			}
		}
		dto.setProdutosComEstoqueBaixo(baixo);
		dto.setLotesVencendo(loteRepository.findAllByAtivoTrueAndDataValidadeBetweenOrderByDataValidadeAsc(
				LocalDate.now(), LocalDate.now().plusDays(DIAS_ALERTA_VENCIMENTO)).size());
		dto.setLotesVencidos(loteRepository.findAllByAtivoTrueAndDataValidadeBeforeOrderByDataValidadeAsc(
				LocalDate.now()).size());
		dto.setEntradasSemValor(movRepository.contarEntradasSemValor(
				ym.atDay(1).atStartOfDay(), ym.atEndOfMonth().plusDays(1).atStartOfDay()));
		dto.setBalancoPendente(conferenciaService.balancoPendente());
		dto.setAlertasPendentes(alertaService.contarPendentes());
		dto.setPrincipaisAlertas(alertaService.listarPendentes().stream().limit(10).toList());
		dto.setSessoesAtivas(sessaoService.contarAtivas());
		BackupStatus st = backupService.status();
		dto.setUltimoBackup(st.getUltimoBackup());
		dto.setBackupEmDia(st.isBackupEmDia());
		dto.setUltimosEventos(auditService.ultimosEventos(10));
		return dto;
	}
}