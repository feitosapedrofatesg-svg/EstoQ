package com.estoq.business.compra;

import com.estoq.business.periodo.IPeriodoRepository;
import com.estoq.business.periodo.PeriodoModel;
import com.estoq.business.produto.IProdutoRepository;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.helpers.IGenericMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CompraMapper implements IGenericMapper<CompraModel, CompraDTO> {

	@Autowired
	private IProdutoRepository produtoRepository;

	@Autowired
	private IPeriodoRepository periodoRepository;

	@Override
	public CompraDTO toDto(CompraModel entity) {
		if (entity == null) {
			return null;
		}
		CompraDTO dto = new CompraDTO();
		dto.setId(entity.getId());
		dto.setActive(entity.isAtivo());
		dto.setProdutoId(entity.getProduto() != null ? entity.getProduto().getId() : null);
		dto.setPeriodoId(entity.getPeriodo() != null ? entity.getPeriodo().getId() : null);
		dto.setQuantidade(entity.getQuantidade());
		dto.setPrecoUnitario(entity.getPrecoUnitario());
		dto.setDataCompra(entity.getDataCompra());
		return dto;
	}

	@Override
	public CompraModel toEntity(CompraDTO dto) {
		if (dto == null) {
			return null;
		}
		CompraModel entity = new CompraModel();
		entity.setId(dto.getId());
		entity.setAtivo(dto.isActive());
		entity.setProduto(buscarProduto(dto.getProdutoId()));
		entity.setPeriodo(buscarPeriodo(dto.getPeriodoId()));
		entity.setQuantidade(dto.getQuantidade());
		entity.setPrecoUnitario(dto.getPrecoUnitario());
		entity.setDataCompra(dto.getDataCompra());
		return entity;
	}

	private ProdutoModel buscarProduto(UUID id) {
		if (id == null) {
			return null;
		}
		return produtoRepository.findByIdAndAtivoTrue(id).orElseThrow(() ->
				new BusinessException("Produto não encontrado.", HttpStatus.NOT_FOUND));
	}

	private PeriodoModel buscarPeriodo(UUID id) {
		if (id == null) {
			return null;
		}
		return periodoRepository.findByIdAndAtivoTrue(id).orElseThrow(() ->
				new BusinessException("Período não encontrado.", HttpStatus.NOT_FOUND));
	}
}