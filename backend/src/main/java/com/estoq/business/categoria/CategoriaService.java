package com.estoq.business.categoria;

import com.estoq.business.produto.IProdutoRepository;
import com.estoq.core.exceptions.RuleValidationException;
import com.estoq.core.services.GenericService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CategoriaService extends GenericService<CategoriaModel, ICategoriaRepository, ICategoriaValidation> {

	@Autowired
	private IProdutoRepository produtoRepository;

	@Override
	protected void beforeInsert(CategoriaModel entity) {
		entity.setNome(entity.getNome().trim());
	}

	@Override
	protected void beforeUpdate(CategoriaModel entity) {
		entity.setNome(entity.getNome().trim());
	}

	@Override
	protected void beforeDelete(CategoriaModel entity) {
		if (produtoRepository.countByCategoriaIdAndAtivoTrue(entity.getId()) > 0) {
			throw new RuleValidationException("exclusao-com-produtos",
					"Não é possível excluir uma categoria com produtos vinculados.");
		}
	}
}