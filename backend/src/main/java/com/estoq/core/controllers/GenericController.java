package com.estoq.core.controllers;

import com.estoq.core.domains.BaseModel;
import com.estoq.core.dtos.BaseDTO;
import com.estoq.core.helpers.IGenericMapper;
import com.estoq.core.services.IGenericService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Contrato REST genérico.
 *
 * @param <E> Entidade de banco
 * @param <D> DTO de transferência
 * @param <S> Service especializado
 * @param <M> Mapper especializado
 */
public abstract class GenericController<E extends BaseModel, D extends BaseDTO,
		S extends IGenericService<E, ?, ?>, M extends IGenericMapper<E, D>> {

	@Autowired
	protected S service;

	@Autowired
	protected M mapper;

	@GetMapping("/{id}")
	public ResponseEntity findById(@PathVariable UUID id) {
		E entity = service.findByIdActive(id);
		return ResponseEntity.ok(mapper.toDto(entity));
	}

	@GetMapping
	public ResponseEntity findAll(Pageable pageable) {
		Page<E> entities = service.findAllActive(pageable);
		return ResponseEntity.ok(mapper.toDtoPage(entities));
	}

	@PostMapping
	public ResponseEntity insert(@RequestBody D dto) {
		E entity = mapper.toEntity(dto);
		E saved = service.insert(entity);
		return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(saved));
	}

	@PutMapping("/{id}")
	public ResponseEntity update(@PathVariable UUID id, @RequestBody D dto) {
		E entity = mapper.toEntity(dto);
		entity.setId(id);

		E original = service.findByIdActive(id);
		entity.setAtivo(original.isAtivo());
		entity.setDataHoraCriacao(original.getDataHoraCriacao());

		E updated = service.update(entity);
		return ResponseEntity.ok(mapper.toDto(updated));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity delete(@PathVariable UUID id) {
		service.delete(id);
		return ResponseEntity.ok("Registro removido com sucesso.");
	}
}