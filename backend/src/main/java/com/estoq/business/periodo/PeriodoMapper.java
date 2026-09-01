package com.estoq.business.periodo;

import com.estoq.core.helpers.IGenericMapper;
import org.springframework.stereotype.Component;

@Component
public class PeriodoMapper implements IGenericMapper<PeriodoModel, PeriodoDTO> {

	@Override
	public PeriodoDTO toDto(PeriodoModel entity) {
		if (entity == null) {
			return null;
		}
		PeriodoDTO dto = new PeriodoDTO();
		dto.setId(entity.getId());
		dto.setActive(entity.isAtivo());
		dto.setNome(entity.getNome());
		dto.setDataInicio(entity.getDataInicio());
		dto.setDataFim(entity.getDataFim());
		dto.setVendas(entity.getVendas());
		dto.setStatus(entity.getStatus());
		return dto;
	}

	@Override
	public PeriodoModel toEntity(PeriodoDTO dto) {
		if (dto == null) {
			return null;
		}
		PeriodoModel entity = new PeriodoModel();
		entity.setId(dto.getId());
		entity.setAtivo(dto.isActive());
		entity.setNome(dto.getNome());
		entity.setDataInicio(dto.getDataInicio());
		entity.setDataFim(dto.getDataFim());
		entity.setVendas(dto.getVendas());
		entity.setStatus(dto.getStatus());
		return entity;
	}
}