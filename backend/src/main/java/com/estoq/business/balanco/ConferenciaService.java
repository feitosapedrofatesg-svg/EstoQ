package com.estoq.business.balanco;

import com.estoq.business.balanco.BalancoView;
import com.estoq.business.balanco.ConfiguracaoBalancoView;
import com.estoq.business.balanco.ItemBalancoView;
import com.estoq.business.movimentacao.MovimentacaoService;
import com.estoq.business.produto.IProdutoRepository;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.business.usuario.UsuarioModel;
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
import java.util.UUID;

@Service
public class ConferenciaService {

	@Autowired
	private IConfiguracaoBalancoRepository configRepository;

	@Autowired
	private IBalancoRepository balancoRepository;

	@Autowired
	private IItemBalancoRepository itemBalancoRepository;

	@Autowired
	private IProdutoRepository produtoRepository;

	@Autowired
	private MovimentacaoService movimentacaoService;

	// ------------------------------------------------------------ configuração

	@Transactional(readOnly = true)
	public ConfiguracaoBalancoModel getConfiguracao() {
		return configRepository.findAll().stream()
				.filter(c -> c.isAtivo()).findFirst().orElse(null);
	}

	@Transactional
	public ConfiguracaoBalancoModel atualizarConfiguracao(PeriodicidadeBalanco periodicidade, Integer diaExecucao) {
		ConfiguracaoBalancoModel cfg = getConfiguracao();
		if (cfg == null) {
			cfg = new ConfiguracaoBalancoModel();
			cfg.setPeriodicidade(PeriodicidadeBalanco.SEMANAL);
			cfg.setDiaExecucao(1);
		}
		if (periodicidade != null) {
			cfg.setPeriodicidade(periodicidade);
		}
		if (diaExecucao != null) {
			cfg.setDiaExecucao(diaExecucao);
		}
		LocalDate ultimo = balancoRepository.findFirstByStatusAndAtivoTrueOrderByDataHoraDesc(StatusBalanco.CONCLUIDO)
				.map(b -> b.getDataHora().toLocalDate()).orElse(null);
		cfg.calcularProximaExecucao(LocalDate.now(), ultimo);
		return configRepository.save(cfg);
	}

	@Transactional(readOnly = true)
	public boolean balancoPendente() {
		ConfiguracaoBalancoModel cfg = getConfiguracao();
		long emAndamento = balancoRepository.countByStatusAndAtivoTrue(StatusBalanco.EM_ANDAMENTO);
		return cfg != null && cfg.balancoEstaPendente(LocalDate.now(), emAndamento);
	}

	// ------------------------------------------------------------ balanços

	@Transactional(readOnly = true)
	public List<BalancoView> listarBalancos() {
		return balancoRepository.findAllByAtivoTrueOrderByDataHoraDesc()
				.stream().map(this::toView).toList();
	}

	@Transactional(readOnly = true)
	public BalancoView getBalanco(UUID balancoId) {
		BalancoModel b = buscarBalanco(balancoId);
		return toView(b);
	}

	@Transactional
	public BalancoView iniciarBalanco(UsuarioModel usuario) {
		BalancoModel b = new BalancoModel();
		b.setDataHora(LocalDateTime.now());
		ConfiguracaoBalancoModel cfg = getConfiguracao();
		b.setTipo(cfg != null ? cfg.getPeriodicidade() : PeriodicidadeBalanco.SEMANAL);
		b.setStatus(StatusBalanco.EM_ANDAMENTO);
		b.setResponsavel(usuario);

		for (ProdutoModel p : produtoRepository.findAllByAtivoTrue(PageRequest.of(0, Integer.MAX_VALUE)).getContent()) {
			BigDecimal saldo = movimentacaoService.obterSaldo(p.getId());
			if (saldo.signum() <= 0) {
				continue;
			}
			ItemBalancoModel item = new ItemBalancoModel();
			item.setProduto(p);
			item.setQuantidadeSistema(saldo);
			item.setQuantidadeFisica(saldo);
			b.adicionarItem(item);
		}
		balancoRepository.save(b);
		return toView(b);
	}

	@Transactional
	public ItemBalancoView atualizarQuantidadeFisica(UUID balancoId, UUID itemId, BigDecimal quantidadeFisica) {
		if (quantidadeFisica == null || quantidadeFisica.signum() < 0) {
			throw new BusinessException("Quantidade física deve ser maior ou igual a zero.", HttpStatus.BAD_REQUEST);
		}
		BalancoModel b = buscarBalanco(balancoId);
		if (StatusBalanco.CONCLUIDO == b.getStatus()) {
			throw new BusinessException("Balanço já confirmado não aceita alterações.", HttpStatus.CONFLICT);
		}
		if (StatusBalanco.CANCELADO == b.getStatus()) {
			throw new BusinessException("Balanço cancelado não aceita alterações.", HttpStatus.CONFLICT);
		}
		ItemBalancoModel item = itemBalancoRepository.findByIdAndAtivoTrue(itemId)
				.filter(i -> i.getBalanco() != null && i.getBalanco().getId().equals(balancoId))
				.orElseThrow(() -> new BusinessException("Item de balanço não encontrado.", HttpStatus.NOT_FOUND));
		item.setQuantidadeFisica(quantidadeFisica);
		if (StatusBalanco.PENDENTE == b.getStatus()) {
			b.setStatus(StatusBalanco.EM_ANDAMENTO);
			balancoRepository.save(b);
		}
		itemBalancoRepository.save(item);
		return toItemView(item);
	}

	@Transactional
	public BalancoView apurar(UUID balancoId) {
		BalancoModel b = buscarBalanco(balancoId);
		if (StatusBalanco.CONCLUIDO == b.getStatus()) {
			throw new BusinessException("Balanço já confirmado.", HttpStatus.CONFLICT);
		}
		if (StatusBalanco.CANCELADO == b.getStatus()) {
			throw new BusinessException("Balanço cancelado não pode ser apurado.", HttpStatus.CONFLICT);
		}
		// atualiza o saldo de sistema com o estoque corrente antes de apurar
		for (ItemBalancoModel item : itemBalancoRepository.findAllByBalanco_IdAndAtivoTrue(balancoId)) {
			item.setQuantidadeSistema(movimentacaoService.obterSaldo(item.getProduto().getId()));
			itemBalancoRepository.save(item);
		}
		b.setStatus(StatusBalanco.EM_ANDAMENTO);
		b.apurarDiferencas();
		balancoRepository.save(b);
		return toView(b);
	}

	@Transactional
	public BalancoView confirmar(UUID balancoId, UsuarioModel usuario) {
		BalancoModel b = buscarBalanco(balancoId);
		if (StatusBalanco.CONCLUIDO == b.getStatus()) {
			throw new BusinessException("Balanço já confirmado.", HttpStatus.CONFLICT);
		}
		if (StatusBalanco.CANCELADO == b.getStatus()) {
			throw new BusinessException("Balanço cancelado não pode ser confirmado.", HttpStatus.CONFLICT);
		}
		// confirma divergências gerando ajustes de estoque
		for (ItemBalancoModel item : itemBalancoRepository.findAllByBalanco_IdAndAtivoTrue(balancoId)) {
			item.calcularDiferenca();
			if (item.getDiferenca().signum() != 0) {
				movimentacaoService.registrarAjuste(item.getProduto().getId(), usuario, item.getDiferenca(),
						"Ajuste por conferência física (balanço " + b.getId().toString().substring(0, 8) + ").",
						item.getId(), null);
			}
		}
		b.confirmar();
		b.apurarDiferencas();
		balancoRepository.save(b);

		ConfiguracaoBalancoModel cfg = getConfiguracao();
		if (cfg != null) {
			cfg.calcularProximaExecucao(LocalDate.now(), b.getDataHora() != null ? b.getDataHora().toLocalDate() : null);
			configRepository.save(cfg);
		}
		return toView(b);
	}

	@Transactional
	public BalancoView cancelarBalanco(UUID balancoId) {
		BalancoModel b = buscarBalanco(balancoId);
		if (StatusBalanco.CONCLUIDO == b.getStatus()) {
			throw new BusinessException("Balanço já confirmado não pode ser cancelado.", HttpStatus.CONFLICT);
		}
		if (StatusBalanco.CANCELADO != b.getStatus()) {
			b.cancelar();
			balancoRepository.save(b);
		}
		return toView(b);
	}

	@Transactional
	public BalancoView reabrirBalanco(UUID balancoId) {
		BalancoModel b = buscarBalanco(balancoId);
		if (StatusBalanco.CANCELADO != b.getStatus()) {
			throw new BusinessException("Somente balanços cancelados podem ser reabertos.", HttpStatus.CONFLICT);
		}
		b.reabrir();
		balancoRepository.save(b);
		return toView(b);
	}

	@Transactional
	public void excluirBalanco(UUID balancoId) {
		BalancoModel b = buscarBalanco(balancoId);
		if (StatusBalanco.CANCELADO != b.getStatus()) {
			throw new BusinessException("Somente balanços cancelados podem ser excluídos.", HttpStatus.CONFLICT);
		}
		b.setAtivo(false);
		balancoRepository.save(b);
	}

	// ------------------------------------------------------------ views

	public ConfiguracaoBalancoView toConfigView(ConfiguracaoBalancoModel cfg) {
		ConfiguracaoBalancoView v = new ConfiguracaoBalancoView();
		v.setId(cfg.getId());
		v.setPeriodicidade(cfg.getPeriodicidade() != null ? cfg.getPeriodicidade().name() : null);
		v.setDiaExecucao(cfg.getDiaExecucao());
		v.setProximaExecucao(cfg.getProximaExecucao());
		long emAndamento = balancoRepository.countByStatusAndAtivoTrue(StatusBalanco.EM_ANDAMENTO);
		v.setBalancosEmAndamento(emAndamento);
		v.setPendente(cfg.balancoEstaPendente(LocalDate.now(), emAndamento));
		return v;
	}

	public BalancoView toView(BalancoModel b) {
		BalancoView v = new BalancoView();
		v.setId(b.getId());
		v.setDataHora(b.getDataHora());
		v.setTipo(b.getTipo() != null ? b.getTipo().name() : null);
		v.setStatus(b.getStatus() != null ? b.getStatus().name() : null);
		v.setResponsavelNome(b.getResponsavel() != null ? b.getResponsavel().getNome() : null);
		List<ItemBalancoModel> itens = itemBalancoRepository.findAllByBalanco_IdAndAtivoTrue(b.getId());
		v.setItens(itens.stream().map(this::toItemView).toList());
		v.setQtdItens(itens.size());
		BigDecimal total = BigDecimal.ZERO;
		for (ItemBalancoModel i : itens) {
			total = total.add(NumeroUtil.s(i.getDiferenca()));
		}
		v.setTotalDiferenca(total);
		return v;
	}

	private ItemBalancoView toItemView(ItemBalancoModel i) {
		ItemBalancoView v = new ItemBalancoView();
		v.setId(i.getId());
		v.setBalancoId(i.getBalanco() != null ? i.getBalanco().getId() : null);
		v.setProdutoId(i.getProduto() != null ? i.getProduto().getId() : null);
		v.setProdutoNome(i.getProduto() != null ? i.getProduto().getNome() : null);
		v.setUnidadeMedida(i.getProduto() != null && i.getProduto().getUnidadeMedida() != null
				? i.getProduto().getUnidadeMedida().name()
				: null);
		v.setQuantidadeSistema(i.getQuantidadeSistema());
		v.setQuantidadeFisica(i.getQuantidadeFisica());
		v.setDiferenca(i.getDiferenca());
		return v;
	}

	private BalancoModel buscarBalanco(UUID balancoId) {
		return balancoRepository.findByIdAndAtivoTrue(balancoId)
				.orElseThrow(() -> new BusinessException("Balanço não encontrado.", HttpStatus.NOT_FOUND));
	}
}