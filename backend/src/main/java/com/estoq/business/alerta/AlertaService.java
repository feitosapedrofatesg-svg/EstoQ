package com.estoq.business.alerta;

import com.estoq.business.alerta.AlertaView;
import com.estoq.business.balanco.BalancoModel;
import com.estoq.business.balanco.ConferenciaService;
import com.estoq.business.balanco.IBalancoRepository;
import com.estoq.business.balanco.ItemBalancoModel;
import com.estoq.business.balanco.StatusBalanco;
import com.estoq.business.lote.ILoteRepository;
import com.estoq.business.lote.LoteModel;
import com.estoq.business.parametro.IParametroEstoqueRepository;
import com.estoq.business.parametro.ParametroEstoqueModel;
import com.estoq.business.produto.IProdutoRepository;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.business.usuario.Perfil;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.helpers.NumeroUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AlertaService {

	private static final int DIAS_VENCIMENTO_PADRAO = 7;

	@Autowired
	private IAlertaRepository alertaRepository;

	@Autowired
	private IProdutoRepository produtoRepository;

	@Autowired
	private IParametroEstoqueRepository parametroRepository;

	@Autowired
	private ILoteRepository loteRepository;

	@Autowired
	private ConferenciaService conferenciaService;

	@Autowired
	private IBalancoRepository balancoRepository;

	@Autowired
	private com.estoq.business.movimentacao.MovimentacaoService movimentacaoService;

	@Transactional
	public int gerarAlertas() {
		int criados = 0;
		LocalDate hoje = LocalDate.now();
		LocalDateTime agora = LocalDateTime.now();

		for (ProdutoModel p : produtoRepository.findAllByAtivoTrue(PageRequest.of(0, Integer.MAX_VALUE)).getContent()) {
			ParametroEstoqueModel par = parametroRepository.findByProduto_IdAndAtivoTrue(p.getId()).orElse(null);
			BigDecimal minimo = NumeroUtil.s(par != null ? par.getEstoqueMinimo() : null);
			int diasReposicao = par != null && par.getTempoReposicaoDias() != null
					? par.getTempoReposicaoDias()
					: DIAS_VENCIMENTO_PADRAO;

			if (minimo.signum() > 0) {
				BigDecimal saldo = movimentacaoService.obterSaldo(p.getId());
				if (saldo.compareTo(minimo) < 0) {
					criados += criarAlerta(TipoAlerta.ESTOQUE_BAIXO,
							"Estoque de " + p.getNome() + " abaixo do mínimo (saldo: " + saldo
									+ ", mínimo: " + minimo + ").",
							Perfil.ADMIN, p, null, null, agora);
				}
			}

			for (LoteModel lote : loteRepository.findAllByProduto_IdAndAtivoTrue(p.getId())) {
				if (lote.estaVencido()) {
					criados += criarAlerta(TipoAlerta.VENCIDO,
							"Lote " + lote.getCodigo() + " do produto " + p.getNome() + " está vencido.",
							Perfil.ADMIN, p, lote, null, agora);
				} else if (lote.estaDisponivel() && lote.diasParaVencimento() <= diasReposicao) {
					criados += criarAlerta(TipoAlerta.PROXIMO_VENCIMENTO,
							"Lote " + lote.getCodigo() + " do produto " + p.getNome()
									+ " vence em " + lote.diasParaVencimento() + " dia(s).",
							Perfil.ADMIN, p, lote, null, agora);
				}
			}
		}

		if (conferenciaService.balancoPendente()) {
			criados += criarAlerta(TipoAlerta.BALANCO_PENDENTE,
					"Há balanço(s) de conferência física pendente(s); realize a contagem.",
					Perfil.ADMIN, null, null, null, agora);
		}

		for (BalancoModel b : balancoRepository
				.findAllByStatusInAndAtivoTrueOrderByDataHoraDesc(List.of(StatusBalanco.CONCLUIDO))) {
			if (b.getItens() == null || b.getItens().isEmpty()) {
				continue;
			}
			for (ItemBalancoModel item : b.getItens()) {
				if (item.getDiferenca().signum() != 0) {
					criados += criarAlerta(TipoAlerta.DIFERENCA_ESTOQUE,
							"Diferença apurada de " + item.getDiferenca() + " no produto "
									+ (item.getProduto() != null ? item.getProduto().getNome() : "?"),
							Perfil.ADMIN, item.getProduto(), null, b, agora);
				}
			}
		}
		return criados;
	}

	private int criarAlerta(TipoAlerta tipo, String mensagem, Perfil perfilDestino, ProdutoModel produto,
			LoteModel lote, BalancoModel balanco, LocalDateTime data) {
		if (jaExiste(tipo, produto, lote, balanco)) {
			return 0;
		}
		AlertaModel a = new AlertaModel();
		a.setTipo(tipo);
		a.setMensagem(mensagem);
		a.setPerfilDestino(perfilDestino);
		a.setDataGeracao(data);
		a.setVisualizado(false);
		a.setProduto(produto);
		a.setLote(lote);
		a.setBalanco(balanco);
		alertaRepository.save(a);
		return 1;
	}

	private boolean jaExiste(TipoAlerta tipo, ProdutoModel produto, LoteModel lote, BalancoModel balanco) {
		return alertaRepository.findAllByVisualizadoFalseAndAtivoTrue().stream().anyMatch(a ->
				a.getTipo() == tipo
						&& Objects.equals(a.getProduto() != null ? a.getProduto().getId() : null,
								produto != null ? produto.getId() : null)
						&& Objects.equals(a.getLote() != null ? a.getLote().getId() : null,
								lote != null ? lote.getId() : null)
						&& Objects.equals(a.getBalanco() != null ? a.getBalanco().getId() : null,
								balanco != null ? balanco.getId() : null));
	}

	@Transactional
	public void marcarComoVisualizado(UUID alertaId) {
		AlertaModel a = alertaRepository.findByIdAndAtivoTrue(alertaId)
				.orElseThrow(() -> new BusinessException("Alerta não encontrado.", HttpStatus.NOT_FOUND));
		a.marcarComoVisualizado();
		alertaRepository.save(a);
	}

	@Transactional(readOnly = true)
	public List<AlertaView> listar() {
		return alertaRepository.findAllByAtivoTrueOrderByDataGeracaoDesc().stream().map(this::toView).toList();
	}

	@Transactional(readOnly = true)
	public List<AlertaView> listarPendentes() {
		return alertaRepository.findAllByVisualizadoFalseAndAtivoTrueOrderByDataGeracaoDesc()
				.stream().map(this::toView).toList();
	}

	@Transactional(readOnly = true)
	public long contarPendentes() {
		return alertaRepository.countByVisualizadoFalseAndAtivoTrue();
	}

	private AlertaView toView(AlertaModel a) {
		AlertaView v = new AlertaView();
		v.setId(a.getId());
		v.setTipo(a.getTipo() != null ? a.getTipo().name() : null);
		v.setMensagem(a.getMensagem());
		v.setDataGeracao(a.getDataGeracao());
		v.setPerfilDestino(a.getPerfilDestino() != null ? a.getPerfilDestino().name() : null);
		v.setVisualizado(a.isVisualizado());
		v.setProdutoNome(a.getProduto() != null ? a.getProduto().getNome() : null);
		v.setLoteCodigo(a.getLote() != null ? a.getLote().getCodigo() : null);
		return v;
	}
}