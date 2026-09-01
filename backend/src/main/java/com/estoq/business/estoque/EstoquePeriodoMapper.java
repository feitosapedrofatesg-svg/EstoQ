package com.estoq.business.estoque;

import com.estoq.business.periodo.IPeriodoRepository;
import com.estoq.business.periodo.PeriodoModel;
import com.estoq.business.produto.IProdutoRepository;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.helpers.IGenericMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class EstoquePeriodoMapper implements IGenericMapper<EstoquePeriodoModel, EstoquePeriodoDTO> {

	@Autowired
	private IProdutoRepository produtoRepository;

	@Autowired
	private IPeriodoRepository periodoRepository;

	@Override
	public EstoquePeriodoDTO toDto(EstoquePeriodoModel entity) {
		if (entity == null) {
			return null;
		}
		EstoquePeriodoDTO dto = new EstoquePeriodoDTO();
		dto.setId(entity.getId());
		dto.setActive(entity.isAtivo());
		dto.setProdutoId(entity.getProduto() != null ? entity.getProduto().getId() : null);
		dto.setPeriodoId(entity.getPeriodo() != null ? entity.getPeriodo().getId() : null);
		dto.setQuantidadeInicial(entity.getQuantidadeInicial());
		dto.setValorUnitarioInicial(entity.getValorUnitarioInicial());
		dto.setQuantidadeFinal(entity.getQuantidadeFinal());
		dto.setValorUnitarioFinal(entity.getValorUnitarioFinal());
		return dto;
	}

	@Override
	public EstoquePeriodoModel toEntity(EstoquePeriodoDTO dto) {
		if (dto == null) {
			return null;
		}
		EstoquePeriodoModel entity = new EstoquePeriodoModel();
		entity.setId(dto.getId());
		entity.setAtivo(dto.isActive());
		entity.setProduto(buscarProduto(dto.getProdutoId()));
		entity.setPeriodo(buscarPeriodo(dto.getPeriodoId()));
		entity.setQuantidadeInicial(dto.getQuantidadeInicial());
		entity.setValorUnitarioInicial(dto.getValorUnitarioInicial());
		entity.setQuantidadeFinal(dto.getQuantidadeFinal());
		entity.setValorUnitarioFinal(dto.getValorUnitarioFinal());
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