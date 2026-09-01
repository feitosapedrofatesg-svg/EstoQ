package com.estoq.business.produto;

import com.estoq.core.services.GenericService;
import org.springframework.stereotype.Service;

@Service
public class ProdutoService extends GenericService<ProdutoModel, IProdutoRepository, IProdutoValidation> {

	@Override
	protected void beforeInsert(ProdutoModel entity) {
		normalizar(entity);
	}

	@Override
	protected void beforeUpdate(ProdutoModel entity) {
		normalizar(entity);
	}

	private void normalizar(ProdutoModel entity) {
		entity.setNome(entity.getNome().trim());
		entity.setUnidade(entity.getUnidade().trim().toLowerCase());
		if (entity.getCategoria() != null) {
			entity.setCategoria(entity.getCategoria().trim());
		}
	}
}