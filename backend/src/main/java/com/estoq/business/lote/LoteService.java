package com.estoq.business.lote;

import com.estoq.business.lote.LoteView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class LoteService {

	@Autowired
	private ILoteRepository repository;

	@Transactional(readOnly = true)
	public List<LoteView> listarTodos() {
		return repository.findAllAtivoOrdenado().stream().map(this::toView).toList();
	}

	@Transactional(readOnly = true)
	public List<LoteView> listarPorProduto(UUID produtoId) {
		return repository.findAllByProduto_IdAndAtivoTrue(produtoId).stream().map(this::toView).toList();
	}

	@Transactional(readOnly = true)
	public List<LoteView> listarDisponiveis() {
		return repository.findAllDisponiveis().stream().map(this::toView).toList();
	}

	@Transactional(readOnly = true)
	public List<LoteView> listarVencendo(UUID produtoId, int dias) {
		LocalDate hoje = LocalDate.now();
		LocalDate limite = hoje.plusDays(Math.max(dias, 1));
		List<LoteModel> lotes = produtoId != null
				? repository.findAllByProduto_IdAndAtivoTrue(produtoId)
				: repository.findAllByAtivoTrueAndDataValidadeBetweenOrderByDataValidadeAsc(hoje, limite);
		return lotes.stream()
				.filter(l -> !l.estaVencido() && l.estaDisponivel() && l.diasParaVencimento() >= 0
						&& l.diasParaVencimento() <= Math.max(dias, 1))
				.map(this::toView).toList();
	}

	@Transactional(readOnly = true)
	public List<LoteView> listarVencidos(UUID produtoId) {
		LocalDate hoje = LocalDate.now();
		List<LoteModel> lotes = produtoId != null
				? repository.findAllByProduto_IdAndAtivoTrue(produtoId)
				: repository.findAllByAtivoTrueAndDataValidadeBeforeOrderByDataValidadeAsc(hoje);
		return lotes.stream().filter(LoteModel::estaVencido).map(this::toView).toList();
	}

	private LoteView toView(LoteModel l) {
		LoteView v = new LoteView();
		v.setId(l.getId());
		v.setCodigo(l.getCodigo());
		v.setProdutoId(l.getProduto() != null ? l.getProduto().getId() : null);
		v.setProdutoNome(l.getProduto() != null ? l.getProduto().getNome() : null);
		v.setUnidadeMedida(l.getProduto() != null && l.getProduto().getUnidadeMedida() != null
				? l.getProduto().getUnidadeMedida().name()
				: null);
		v.setQuantidadeInicial(l.getQuantidadeInicial());
		v.setQuantidadeAtual(l.getQuantidadeAtual());
		v.setDataEntrada(l.getDataEntrada());
		v.setDataValidade(l.getDataValidade());
		v.setPrecoUnitario(l.getPrecoUnitario());
		v.setVencido(l.estaVencido());
		v.setDisponivel(l.estaDisponivel());
		v.setDiasParaVencimento(l.diasParaVencimento());
		return v;
	}
}