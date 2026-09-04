package com.estoq.business.produtoaberto;

import com.estoq.business.produtoaberto.ProdutoAbertoView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProdutoAbertoService {

	@Autowired
	private IProdutoAbertoRepository repository;

	@Transactional(readOnly = true)
	public List<ProdutoAbertoView> listarAbertos() {
		return repository.findAllByFinalizadoFalseAndAtivoTrueOrderByDataAberturaAsc()
				.stream().map(this::toView).toList();
	}

	@Transactional(readOnly = true)
	public List<ProdutoAbertoView> listarPorProduto(UUID produtoId) {
		return repository.findAllByProduto_IdAndFinalizadoFalseAndAtivoTrueOrderByDataAberturaAsc(produtoId)
				.stream().map(this::toView).toList();
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
}