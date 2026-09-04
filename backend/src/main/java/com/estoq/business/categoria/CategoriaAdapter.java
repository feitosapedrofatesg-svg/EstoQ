package com.estoq.business.categoria;

import com.estoq.business.produto.IProdutoRepository;
import com.estoq.core.helpers.IGenericAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CategoriaAdapter implements IGenericAdapter<CategoriaModel, CategoriaDTO> {

	@Autowired
	private IProdutoRepository produtoRepository;

	@Override
	public CategoriaDTO toDto(CategoriaModel entity) {
		if (entity == null) {
			return null;
		}
		CategoriaDTO dto = new CategoriaDTO();
		dto.setId(entity.getId());
		dto.setAtivo(entity.isAtivo());
		dto.setDataHoraCriacao(entity.getDataHoraCriacao());
		dto.setNome(entity.getNome());
		dto.setDescricao(entity.getDescricao());
		dto.setQtdProdutos(produtoRepository.countByCategoriaIdAndAtivoTrue(entity.getId()));
		return dto;
	}

	@Override
	public CategoriaModel toEntity(CategoriaDTO dto) {
		if (dto == null) {
			return null;
		}
		CategoriaModel entity = new CategoriaModel();
		entity.setId(dto.getId());
		entity.setAtivo(dto.isAtivo());
		entity.setNome(dto.getNome());
		entity.setDescricao(dto.getDescricao());
		return entity;
	}
}