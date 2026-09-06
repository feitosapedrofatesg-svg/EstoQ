package com.estoq.business.movimentacao;

import com.estoq.business.auditoria.AuditService;
import com.estoq.business.configuracao.ConfiguracaoService;
import com.estoq.business.movimentacao.MovimentacaoView;
import com.estoq.business.produtoaberto.ProdutoAbertoView;
import com.estoq.business.balanco.ItemBalancoModel;
import com.estoq.business.balanco.IItemBalancoRepository;
import com.estoq.business.lote.ILoteRepository;
import com.estoq.business.lote.LoteModel;
import com.estoq.business.produto.IProdutoRepository;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.business.produto.UnidadeMedida;
import com.estoq.business.produtoaberto.IProdutoAbertoRepository;
import com.estoq.business.produtoaberto.ProdutoAbertoModel;
import com.estoq.business.usuario.UsuarioModel;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.helpers.NumeroUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class MovimentacaoService {

	private static final BigDecimal META_CMV = new BigDecimal("0.40");

	private static final LocalDateTime INICIO_PADRAO = LocalDateTime.of(2000, 1, 1, 0, 0);

	@Autowired
	private IMovimentacaoEstoqueRepository movRepository;

	@Autowired
	private ILoteRepository loteRepository;

	@Autowired
	private IProdutoAbertoRepository produtoAbertoRepository;

	@Autowired
	private IProdutoRepository produtoRepository;

	@Autowired
	private IItemBalancoRepository itemBalancoRepository;

	@Autowired
	private AuditService auditService;

	@Autowired
	private ConfiguracaoService configuracaoService;

	// ------------------------------------------------------------------ entradas

	@Transactional
	public MovimentacaoView registrarEntrada(UUID produtoId, UsuarioModel usuario, BigDecimal quantidade,
			BigDecimal valorTotalPago, UnidadeMedida unidadeCompra, BigDecimal fatorConversao,
			LocalDate dataValidade, String observacao) {
		ProdutoModel produto = buscarProduto(produtoId);
		if (quantidade == null || quantidade.signum() <= 0) {
			throw new BusinessException("Quantidade deve ser maior que zero.", HttpStatus.BAD_REQUEST);
		}
		BigDecimal fator = NumeroUtil.s(fatorConversao);
		if (fator.signum() <= 0) {
			fator = BigDecimal.ONE;
		}
		BigDecimal quantidadeEstoque = quantidade.multiply(fator);
		if (quantidadeEstoque.signum() <= 0) {
			throw new BusinessException("Quantidade em estoque deve ser maior que zero.", HttpStatus.BAD_REQUEST);
		}
		BigDecimal saldoAnterior = obterSaldo(produto.getId());

		EntradaModel entrada = new EntradaModel();
		entrada.setProduto(produto);
		entrada.setUsuario(usuario);
		entrada.setDataHora(LocalDateTime.now());
		entrada.setQuantidade(quantidadeEstoque);
		entrada.setQuantidadeAnterior(saldoAnterior);
		entrada.setValorTotalPago(valorTotalPago);
		entrada.setUnidadeCompra(unidadeCompra == null ? UnidadeMedida.UN : unidadeCompra);
		entrada.setFatorConversao(fator);
		entrada.setDataValidade(dataValidade);
		entrada.setObservacao(observacao);

		LoteModel lote = entrada.gerarLote(produto, LocalDate.now());
		loteRepository.save(lote);
		entrada.setQuantidadePosterior(saldoAnterior.add(quantidadeEstoque));
		movRepository.save(entrada);
		auditService.registrar("ENTRADA", "MOVIMENTACAO", String.valueOf(entrada.getId()),
				"Entrada de " + quantidade + " " + (unidadeCompra != null ? unidadeCompra : "") + " de "
						+ produto.getNome(),
				usuario);
		return toView(entrada);
	}

	// ------------------------------------------------------------------ consumo

	@Transactional
	public MovimentacaoView registrarConsumo(UUID produtoId, UsuarioModel usuario, BigDecimal quantidade,
			UUID produtoAbertoId, String observacao) {
		ProdutoModel produto = buscarProduto(produtoId);
		quantidade = validarQuantidade(quantidade);
		BigDecimal saldoAnterior = obterSaldo(produto.getId());
		BigDecimal restante = quantidade;
		boolean usouAberto = false;

		ProdutoAbertoModel aberto = produtoAbertoId != null
				? produtoAbertoRepository.findByIdAndAtivoTrue(produtoAbertoId).orElse(null)
				: buscarAbertoComSaldo(produto.getId());
		LoteModel lotePrincipal = null;

		// 1) consome primeiro das embalagens já abertas
		if (aberto != null && aberto.getQuantidadeRestante().signum() > 0) {
			BigDecimal usada = min(restante, aberto.getQuantidadeRestante());
			aberto.setQuantidadeUtilizada(aberto.getQuantidadeUtilizada().add(usada));
			if (aberto.getQuantidadeRestante().signum() <= 0) {
				aberto.marcarFinalizado();
			}
			produtoAbertoRepository.save(aberto);
			if (aberto.getLote() != null) {
				lotePrincipal = aberto.getLote();
			}
			usouAberto = true;
			restante = restante.subtract(usada);
		}
		// 2) o que faltar sai dos lotes (FIFO por vencimento)
		if (restante.signum() > 0) {
			LoteModel primario = baixarLotesFifo(produto.getId(), restante, null);
			if (lotePrincipal == null) {
				lotePrincipal = primario;
			}
		}

		ConsumoModel consumo = new ConsumoModel();
		consumo.setProduto(produto);
		consumo.setUsuario(usuario);
		consumo.setDataHora(LocalDateTime.now());
		consumo.setQuantidade(quantidade);
		consumo.setQuantidadeAnterior(saldoAnterior);
		consumo.setProdutoAberto(usouAberto ? aberto : null);
		consumo.setLote(lotePrincipal);
		consumo.setObservacao(observacao);
		consumo.setCustoConsumo(calcularCusto(consumo));
		consumo.setQuantidadePosterior(obterSaldo(produto.getId()));
		movRepository.save(consumo);
		auditService.registrar("CONSUMO", "MOVIMENTACAO", String.valueOf(consumo.getId()),
				"Consumo de " + quantidade + " de " + produto.getNome(), usuario);
		return toView(consumo);
	}

	// ------------------------------------------------------------------ desperdício

	@Transactional
	public MovimentacaoView registrarDesperdicio(UUID produtoId, UsuarioModel usuario, BigDecimal quantidade,
			MotivoDesperdicio motivo, String descricaoMotivo, UUID loteId, String observacao) {
		ProdutoModel produto = buscarProduto(produtoId);
		quantidade = validarQuantidade(quantidade);
		if (motivo == null) {
			throw new BusinessException("Motivo do desperdício é obrigatório.", HttpStatus.BAD_REQUEST);
		}
		BigDecimal saldoAnterior = obterSaldo(produto.getId());

		LoteModel lote = loteId != null
				? loteRepository.findByIdAndAtivoTrue(loteId)
						.orElseThrow(() -> new BusinessException("Lote não encontrado.", HttpStatus.NOT_FOUND))
				: null;
		lote = baixarLotesFifo(produto.getId(), quantidade, lote);

		DesperdicioModel desp = new DesperdicioModel();
		desp.setProduto(produto);
		desp.setUsuario(usuario);
		desp.setDataHora(LocalDateTime.now());
		desp.setQuantidade(quantidade);
		desp.setQuantidadeAnterior(saldoAnterior);
		desp.setLote(lote);
		desp.setMotivo(motivo);
		desp.setDescricaoMotivo(descricaoMotivo);
		desp.setObservacao(observacao);
		desp.setQuantidadePosterior(obterSaldo(produto.getId()));
		movRepository.save(desp);
		auditService.registrar("DESPERDICIO", "MOVIMENTACAO", String.valueOf(desp.getId()),
				"Desperdício de " + quantidade + " de " + produto.getNome()
						+ (motivo != null ? " (" + motivo + ")" : ""),
				usuario);
		return toView(desp);
	}

	// ------------------------------------------------------------------ ajuste

	@Transactional
	public MovimentacaoView registrarAjuste(UUID produtoId, UsuarioModel usuario, BigDecimal diferenca,
			String justificativa, UUID itemBalancoId, String observacao) {
		ProdutoModel produto = buscarProduto(produtoId);
		if (diferenca == null || diferenca.signum() == 0) {
			throw new BusinessException("Diferença de ajuste deve ser diferente de zero.", HttpStatus.BAD_REQUEST);
		}
		BigDecimal saldoAnterior = obterSaldo(produto.getId());
		BigDecimal magnitude = diferenca.abs();

		AjusteModel ajuste = new AjusteModel();
		ajuste.setProduto(produto);
		ajuste.setUsuario(usuario);
		ajuste.setDataHora(LocalDateTime.now());
		ajuste.setQuantidade(magnitude);
		ajuste.setQuantidadeAnterior(saldoAnterior);
		ajuste.setDiferencaApurada(diferenca);
		ajuste.setJustificativa(justificativa);
		ajuste.setObservacao(observacao);

		if (itemBalancoId != null) {
			ItemBalancoModel item = itemBalancoRepository.findByIdAndAtivoTrue(itemBalancoId)
					.orElseThrow(() -> new BusinessException("Item de balanço não encontrado.", HttpStatus.NOT_FOUND));
			ajuste.setItemBalanco(item);
		}

		LoteModel lote = null;
		if (diferenca.signum() > 0) {
			lote = criarLoteCredito(produto, magnitude, "AJ " + LocalDate.now());
			loteRepository.save(lote);
		} else {
			lote = baixarLotesFifo(produto.getId(), magnitude, null);
		}
		ajuste.setLote(lote);
		ajuste.setQuantidadePosterior(obterSaldo(produto.getId()));
		movRepository.save(ajuste);
		auditService.registrar("AJUSTE", "MOVIMENTACAO", String.valueOf(ajuste.getId()),
				"Ajuste de " + diferenca + " em " + produto.getNome()
						+ (justificativa != null ? " — " + justificativa : ""),
				usuario);
		return toView(ajuste);
	}

	// ------------------------------------------------------------------ embalagens abertas

	@Transactional
	public ProdutoAbertoView abrirEmbalagem(UUID produtoId, UsuarioModel usuario, BigDecimal quantidade,
			UUID loteId) {
		ProdutoModel produto = buscarProduto(produtoId);
		quantidade = validarQuantidade(quantidade);

		LoteModel lote = null;
		if (loteId != null) {
			lote = loteRepository.findByIdAndAtivoTrue(loteId)
					.orElseThrow(() -> new BusinessException("Lote não encontrado.", HttpStatus.NOT_FOUND));
			lote.baixar(quantidade);
			loteRepository.save(lote);
		} else {
			lote = baixarLotesFifo(produto.getId(), quantidade, null);
		}

		ProdutoAbertoModel aberto = new ProdutoAbertoModel();
		aberto.setProduto(produto);
		aberto.setLote(lote);
		aberto.setDataAbertura(LocalDateTime.now());
		aberto.setQuantidadeAberta(quantidade);
		aberto.setQuantidadeUtilizada(BigDecimal.ZERO);
		aberto.setFinalizado(false);
		produtoAbertoRepository.save(aberto);
		return toView(aberto);
	}

	@Transactional
	public ProdutoAbertoView registrarSobra(UUID produtoAbertoId, UsuarioModel usuario, BigDecimal quantidade) {
		ProdutoAbertoModel aberto = produtoAbertoRepository.findByIdAndAtivoTrue(produtoAbertoId)
				.orElseThrow(() -> new BusinessException("Embalagem aberta não encontrada.", HttpStatus.NOT_FOUND));
		quantidade = validarQuantidade(quantidade);

		// a sobra é tratada como desperdício de produto que não foi aproveitado,
		// portanto é baixada do estoque e não devolvida ao lote.
		BigDecimal saldoAnterior = obterSaldo(aberto.getProduto().getId());
		LoteModel lote = baixarLotesFifo(aberto.getProduto().getId(), quantidade, aberto.getLote());

		aberto.registrarSobra(quantidade);
		if (aberto.getQuantidadeRestante().signum() <= 0) {
			aberto.marcarFinalizado();
		}
		produtoAbertoRepository.save(aberto);

		DesperdicioModel desp = new DesperdicioModel();
		desp.setProduto(aberto.getProduto());
		desp.setUsuario(usuario);
		desp.setDataHora(LocalDateTime.now());
		desp.setQuantidade(quantidade);
		desp.setQuantidadeAnterior(saldoAnterior);
		desp.setLote(lote);
		desp.setMotivo(MotivoDesperdicio.SOBRA_NAO_APROVEITADA);
		desp.setObservacao("Sobra de embalagem aberta");
		desp.setQuantidadePosterior(obterSaldo(aberto.getProduto().getId()));
		movRepository.save(desp);
		auditService.registrar("SOBRA", "MOVIMENTACAO", String.valueOf(desp.getId()),
				"Sobra de " + quantidade + " de " + aberto.getProduto().getNome() + " (embalagem aberta)",
				usuario);
		return toView(aberto);
	}

	// ------------------------------------------------------------------ listagens

	@Transactional(readOnly = true)
	public List<MovimentacaoView> listar(UUID produtoId, LocalDate inicio, LocalDate fim) {
		LocalDateTime ini = inicio == null ? INICIO_PADRAO : inicio.atStartOfDay();
		LocalDateTime fimDt = fim == null ? LocalDateTime.now() : fim.plusDays(1).atStartOfDay();
		List<MovimentacaoEstoqueModel> movs;
		if (produtoId != null) {
			movs = movRepository.findAllByProduto_IdAndDataHoraBetweenOrderByDataHoraAsc(produtoId, ini, fimDt);
		} else {
			movs = movRepository.findAllByDataHoraBetweenOrderByDataHoraAsc(ini, fimDt);
		}
		return movs.stream().filter(MovimentacaoEstoqueModel::isAtivo).map(this::toView).toList();
	}

	@Transactional(readOnly = true)
	public List<MovimentacaoView> listarDesperdicios(LocalDate inicio, LocalDate fim) {
		LocalDateTime ini = inicio == null ? INICIO_PADRAO : inicio.atStartOfDay();
		LocalDateTime fimDt = fim == null ? LocalDateTime.now() : fim.plusDays(1).atStartOfDay();
		return movRepository.findDesperdicios(ini, fimDt).stream().map(this::toView).toList();
	}

	@Transactional(readOnly = true)
	public BigDecimal obterSaldo(UUID produtoId) {
		BigDecimal lotes = NumeroUtil.s(loteRepository.sumQuantidadeAtualByProdutoId(produtoId));
		BigDecimal abertos = NumeroUtil.s(produtoAbertoRepository.sumQuantidadeRestanteByProdutoId(produtoId));
		return lotes.add(abertos);
	}

	/**
	 * Saldo do produto em uma data limite. Quando a data é igual ou posterior à
	 * data atual usa o saldo corrente (mais preciso); caso contrário reconstrói o
	 * saldo a partir do histórico de movimentações (quantidade posterior).
	 */
	@Transactional(readOnly = true)
	public BigDecimal obterSaldoEmData(UUID produtoId, LocalDateTime ate) {
		if (ate == null || !ate.isBefore(LocalDateTime.now())) {
			return obterSaldo(produtoId);
		}
		BigDecimal saldo = BigDecimal.ZERO;
		for (MovimentacaoEstoqueModel m : movRepository.findUltimasPorProduto(produtoId)) {
			if (!m.getDataHora().isAfter(ate)) {
				BigDecimal pos = m.getQuantidadePosterior();
				if (pos != null) {
					saldo = pos;
				} else {
					BigDecimal ant = m.getQuantidadeAnterior();
					if (ant != null) {
						saldo = ant.add(NumeroUtil.s(m.getQuantidade()));
					}
				}
				break;
			}
		}
		return saldo;
	}

	/** Custo médio das entradas recentes do produto, usado para precificar sobras e ajustes. */
	@Transactional(readOnly = true)
	public BigDecimal custoMedioProduto(UUID produtoId) {
		List<LoteModel> lotes = loteRepository.findAllByProduto_IdAndAtivoTrue(produtoId);
		return lotes.stream()
				.filter(LoteModel::estaDisponivel)
				.map(LoteModel::getPrecoUnitario)
				.filter(java.util.Objects::nonNull)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.divide(BigDecimal.valueOf(Math.max(lotes.stream().filter(LoteModel::estaDisponivel).count(), 1)),
						4, java.math.RoundingMode.HALF_UP);
	}

	@Transactional
	public void reverterMovimentacao(UUID movId, UsuarioModel usuario) {
		MovimentacaoEstoqueModel mov = movRepository.findByIdAndAtivoTrue(movId)
				.orElseThrow(() -> new BusinessException("Movimentação não encontrada.", HttpStatus.NOT_FOUND));
		switch (mov.getTipo()) {
			case ENTRADA: {
				EntradaModel e = (EntradaModel) mov;
				if (e.getLoteGerado() != null
						&& e.getLoteGerado().getQuantidadeAtual().compareTo(e.getLoteGerado().getQuantidadeInicial()) == 0) {
					e.getLoteGerado().setAtivo(false);
					loteRepository.save(e.getLoteGerado());
					mov.setAtivo(false);
				} else {
					throw new BusinessException(
							"Entrada não pode ser revertida: o lote gerado já foi utilizado.", HttpStatus.BAD_REQUEST);
				}
				break;
			}
			case CONSUMO: {
				ConsumoModel c = (ConsumoModel) mov;
				if (c.getLote() != null) {
					c.getLote().creditar(c.getQuantidade());
					loteRepository.save(c.getLote());
					mov.setAtivo(false);
				} else {
					throw new BusinessException("Consumo não pode ser revertido (sem lote de origem).",
							HttpStatus.BAD_REQUEST);
				}
				break;
			}
			case DESPERDICIO: {
				DesperdicioModel d = (DesperdicioModel) mov;
				if (d.getLote() != null) {
					d.getLote().creditar(d.getQuantidade());
					loteRepository.save(d.getLote());
					mov.setAtivo(false);
				} else {
					throw new BusinessException("Desperdício não pode ser revertido (sem lote de origem).",
							HttpStatus.BAD_REQUEST);
				}
				break;
			}
			default:
				throw new BusinessException("Tipo de movimentação não suporta reversão.", HttpStatus.BAD_REQUEST);
		}
		movRepository.save(mov);
		auditService.registrar("REVERSAO", "MOVIMENTACAO", String.valueOf(mov.getId()),
				"Reversão de " + mov.getTipo() + " de " + (mov.getProduto() != null ? mov.getProduto().getNome() : "?"),
				usuario);
	}

	// ------------------------------------------------------------------ helpers

	private ProdutoModel buscarProduto(UUID produtoId) {
		return produtoRepository.findByIdAndAtivoTrue(produtoId)
				.orElseThrow(() -> new BusinessException("Produto não encontrado.", HttpStatus.NOT_FOUND));
	}

	private ProdutoAbertoModel buscarAbertoComSaldo(UUID produtoId) {
		return produtoAbertoRepository.findAllByProduto_IdAndFinalizadoFalseAndAtivoTrueOrderByDataAberturaAsc(produtoId)
				.stream().filter(a -> a.getQuantidadeRestante().signum() > 0).findFirst().orElse(null);
	}

	/** Baixa em lotes disponíveis e embalagens abertas (FIFO por vencimento). Retorna o lote principal baixado. */
	private LoteModel baixarLotesFifo(UUID produtoId, BigDecimal quantidade, LoteModel preferido) {
		List<LoteModel> lotes = new ArrayList<>();
		if (preferido != null && preferido.estaDisponivel()) {
			lotes.add(preferido);
		}
		lotes.addAll(loteRepository.findAllByProduto_IdAndAtivoTrue(produtoId).stream()
				.filter(LoteModel::estaDisponivel)
				.filter(l -> preferido == null || !l.getId().equals(preferido.getId()))
				.sorted(Comparator.comparing(LoteModel::getDataValidade, Comparator.nullsLast(Comparator.naturalOrder())))
				.toList());

		BigDecimal restante = quantidade;
		LoteModel principal = null;

		for (LoteModel lote : lotes) {
			if (restante.signum() <= 0) {
				break;
			}
			BigDecimal aBaixar = min(restante, lote.getQuantidadeAtual());
			lote.baixar(aBaixar);
			loteRepository.save(lote);
			if (principal == null) {
				principal = lote;
			}
			restante = restante.subtract(aBaixar);
		}

		// Consome embalagens abertas (FIFO por abertura) quando os lotes não cobrem o total,
		// já que o saldo de sistema inclui a quantidade restante das embalagens abertas.
		if (restante.signum() > 0) {
			for (ProdutoAbertoModel aberto : produtoAbertoRepository
					.findAllByProduto_IdAndFinalizadoFalseAndAtivoTrueOrderByDataAberturaAsc(produtoId)) {
				if (restante.signum() <= 0) {
					break;
				}
				BigDecimal restanteEmbalagem = aberto.getQuantidadeRestante();
				if (restanteEmbalagem.signum() <= 0) {
					continue;
				}
				BigDecimal aRegistrar = min(restante, restanteEmbalagem);
				aberto.registrarSobra(aRegistrar);
				if (principal == null && aberto.getLote() != null && aberto.getLote().estaDisponivel()) {
					principal = aberto.getLote();
				}
				produtoAbertoRepository.save(aberto);
				restante = restante.subtract(aRegistrar);
			}
		}

		if (restante.signum() > 0) {
			throw new BusinessException(
					"Saldo insuficiente: faltam " + restante + " para completar a movimentação.",
					HttpStatus.BAD_REQUEST);
		}
		return principal;
	}

	private LoteModel criarLoteCredito(ProdutoModel produto, BigDecimal quantidade, String codigo) {
		LoteModel lote = new LoteModel();
		lote.setCodigo(codigo);
		lote.setProduto(produto);
		lote.setQuantidadeInicial(quantidade);
		lote.setQuantidadeAtual(quantidade);
		lote.setDataEntrada(LocalDate.now());
		lote.setPrecoUnitario(custoMedioProduto(produto.getId()));
		return lote;
	}

	private BigDecimal calcularCusto(ConsumoModel consumo) {
		BigDecimal preco = NumeroUtil.s(consumo.getLote() != null
				? consumo.getLote().getPrecoUnitario()
				: (consumo.getProdutoAberto() != null
						? (consumo.getProdutoAberto().getLote() != null
								? consumo.getProdutoAberto().getLote().getPrecoUnitario()
								: null)
						: null));
		return NumeroUtil.multiplica(consumo.getQuantidade(), preco);
	}

	private BigDecimal validarQuantidade(BigDecimal q) {
		if (q == null || q.signum() <= 0) {
			throw new BusinessException("Quantidade deve ser maior que zero.", HttpStatus.BAD_REQUEST);
		}
		return q;
	}

	private BigDecimal min(BigDecimal a, BigDecimal b) {
		return a.compareTo(b) <= 0 ? a : b;
	}

	MovimentacaoView toView(MovimentacaoEstoqueModel m) {
		MovimentacaoView v = new MovimentacaoView();
		v.setId(m.getId());
		v.setTipo(m.getTipo().name());
		v.setDataHora(m.getDataHora());
		v.setProdutoId(m.getProduto() != null ? m.getProduto().getId() : null);
		v.setProdutoNome(m.getProduto() != null ? m.getProduto().getNome() : null);
		v.setUnidadeMedida(m.getProduto() != null && m.getProduto().getUnidadeMedida() != null
				? m.getProduto().getUnidadeMedida().name()
				: null);
		v.setQuantidade(m.getQuantidade());
		v.setQuantidadeAnterior(m.getQuantidadeAnterior());
		v.setQuantidadePosterior(m.getQuantidadePosterior());
		v.setObservacao(m.getObservacao());
		v.setUsuarioNome(m.getUsuario() != null ? m.getUsuario().getNome() : null);
		v.setLoteId(m.getLote() != null ? m.getLote().getId() : null);
		v.setLoteCodigo(m.getLote() != null ? m.getLote().getCodigo() : null);
		if (m instanceof DesperdicioModel desp) {
			v.setMotivo(desp.getMotivo() != null ? desp.getMotivo().name() : null);
			v.setValorPrejuizo(desp.getValorPrejuizo());
		} else if (m instanceof ConsumoModel cons) {
			v.setCustoConsumo(cons.getCustoConsumo());
		} else if (m instanceof AjusteModel aj) {
			v.setDiferencaApurada(aj.getDiferencaApurada());
		}
		return v;
	}

	private ProdutoAbertoView toView(ProdutoAbertoModel a) {
		ProdutoAbertoView v = new ProdutoAbertoView();
		v.setId(a.getId());
		v.setProdutoId(a.getProduto() != null ? a.getProduto().getId() : null);
		v.setProdutoNome(a.getProduto() != null ? a.getProduto().getNome() : null);
		v.setUnidadeMedida(a.getProduto() != null && a.getProduto().getUnidadeMedida() != null
				? a.getProduto().getUnidadeMedida().name()
				: null);
		v.setDataAbertura(a.getDataAbertura());
		v.setQuantidadeAberta(a.getQuantidadeAberta());
		v.setQuantidadeUtilizada(a.getQuantidadeUtilizada());
		v.setQuantidadeRestante(a.getQuantidadeRestante());
		v.setFinalizado(a.isFinalizado());
		return v;
	}

	public BigDecimal getMetaCmv() {
		return configuracaoService.obterBigDecimal(ConfiguracaoService.META_CMV, META_CMV);
	}
}