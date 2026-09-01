package com.estoq.core.services;

import com.estoq.core.domains.BaseModel;
import com.estoq.core.repositories.IGenericRepository;
import com.estoq.core.validations.IGenericValidation;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IGenericService<E extends BaseModel, R extends IGenericRepository<E>, V extends IGenericValidation<E, R>> {

	E findByIdActive(UUID id);

	Page<E> findAllActive(Pageable pageable);

	E insert(E entity);

	E update(E entity);

	void delete(UUID id);
}