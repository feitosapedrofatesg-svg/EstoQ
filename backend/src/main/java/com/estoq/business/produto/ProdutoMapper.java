package com.estoq.business.produto;

import com.estoq.core.helpers.IGenericMapper;
import org.springframework.stereotype.Component;

@Component
public class ProdutoMapper implements IGenericMapper<ProdutoModel, ProdutoDTO> {

	@Override
	public ProdutoDTO toDto(ProdutoModel entity) {
		if (entity == null) {
			return null;
		}
		ProdutoDTO dto = new ProdutoDTO();
		dto.setId(entity.getId());
		dto.setActive(entity.isAtivo());
		dto.setNome(entity.getNome());
		dto.setUnidade(entity.getUnidade());
		dto.setCategoria(entity.getCategoria());
		dto.setEstoqueMinimo(entity.getEstoqueMinimo());
		return dto;
	}

	@Override
	public ProdutoModel toEntity(ProdutoDTO dto) {
		if (dto == null) {
			return null;
		}
		ProdutoModel entity = new ProdutoModel();
		entity.setId(dto.getId());
		entity.setAtivo(dto.isActive());
		entity.setNome(dto.getNome());
		entity.setUnidade(dto.getUnidade());
		entity.setCategoria(dto.getCategoria());
		entity.setEstoqueMinimo(dto.getEstoqueMinimo());
		return entity;
	}
}