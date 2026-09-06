package com.estoq.business.produto;

import com.estoq.business.categoria.CategoriaModel;
import com.estoq.business.categoria.ICategoriaRepository;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.helpers.IGenericAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ProdutoAdapter implements IGenericAdapter<ProdutoModel, ProdutoDTO> {

	@Autowired
	private ICategoriaRepository categoriaRepository;

	@Override
	public ProdutoDTO toDto(ProdutoModel entity) {
		if (entity == null) {
			return null;
		}
		ProdutoDTO dto = new ProdutoDTO();
		dto.setId(entity.getId());
		dto.setAtivo(entity.isAtivo());
		dto.setDataHoraCriacao(entity.getDataHoraCriacao());
		dto.setNome(entity.getNome());
		dto.setUnidadeMedida(entity.getUnidadeMedida() == null ? null : entity.getUnidadeMedida().name());
		if (entity.getCategoria() != null) {
			dto.setCategoriaId(entity.getCategoria().getId());
			dto.setCategoriaNome(entity.getCategoria().getNome());
		}
		dto.setEstoqueMinimo(entity.getEstoqueMinimo());
		dto.setSaldoAtual(entity.getSaldoAtual());
		dto.setPrecoUnitario(entity.getPrecoUnitario());
		return dto;
	}

	@Override
	public ProdutoModel toEntity(ProdutoDTO dto) {
		if (dto == null) {
			return null;
		}
		ProdutoModel entity = new ProdutoModel();
		entity.setId(dto.getId());
		entity.setAtivo(dto.isAtivo());
		entity.setNome(dto.getNome());
		entity.setUnidadeMedida(dto.getUnidadeMedida() == null ? UnidadeMedida.UN
				: resolverUnidade(dto.getUnidadeMedida()));
		if (dto.getCategoriaId() != null) {
			entity.setCategoria(categoriaRepository.findByIdAndAtivoTrue(dto.getCategoriaId())
					.orElseThrow(() -> new BusinessException("Categoria não encontrada.", HttpStatus.NOT_FOUND)));
		}
		entity.setEstoqueMinimo(dto.getEstoqueMinimo() == null ? BigDecimal.ZERO : dto.getEstoqueMinimo());
		entity.setPrecoUnitario(dto.getPrecoUnitario());
		return entity;
	}

	public UnidadeMedida resolverUnidade(String valor) {
		try {
			return UnidadeMedida.valueOf(valor.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			return UnidadeMedida.fromLegado(valor);
		}
	}
}