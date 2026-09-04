package com.estoq.core.helpers;

import com.estoq.core.domains.BaseModel;
import com.estoq.core.dtos.BaseDTO;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;

public interface IGenericAdapter<E extends BaseModel, D extends BaseDTO> {

	D toDto(E entity);

	E toEntity(D dto);

	default List<D> toDtoList(List<E> entities) {
		if (entities == null) {
			return null;
		}
		return entities.stream().map(this::toDto).collect(Collectors.toList());
	}

	default Page<D> toDtoPage(Page<E> page) {
		if (page == null) {
			return null;
		}
		return page.map(this::toDto);
	}
}