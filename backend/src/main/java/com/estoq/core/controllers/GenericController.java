package com.estoq.core.controllers;

import com.estoq.core.domains.BaseModel;
import com.estoq.core.dtos.BaseDTO;
import com.estoq.core.helpers.IGenericAdapter;
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
 * E: Entity (Entidade de Banco)
 * D: DTO (Data Transfer Object)
 * S: Service especializado
 * A: Adapter (Mapeador especializado)
 *
 * @param <E>
 * @param <D>
 * @param <S>
 * @param <A>
 */
public abstract class GenericController<E extends BaseModel, D extends BaseDTO,
		S extends IGenericService<E, ?, ?>, A extends IGenericAdapter<E, D>> {

	@Autowired
	protected S service;

	@Autowired
	protected A adapter;

	@GetMapping("/{id}")
	public ResponseEntity findById(@PathVariable UUID id) {
		E entity = service.findByIdActive(id);
		D dto = adapter.toDto(entity);
		return ResponseEntity.ok(dto);
	}

	@GetMapping
	public ResponseEntity findAll(Pageable pageable) {
		Page<E> entities = service.findAllActive(pageable);
		Page<D> dtos = adapter.toDtoPage(entities);
		return ResponseEntity.ok(dtos);
	}

	@PostMapping
	public ResponseEntity insert(@RequestBody D dto) {
		E entity = adapter.toEntity(dto);
		E saved = service.insert(entity);
		return ResponseEntity.status(HttpStatus.CREATED).body(adapter.toDto(saved));
	}

	@PutMapping("/{id}")
	public ResponseEntity update(@PathVariable UUID id, @RequestBody D dto) {
		E entity = adapter.toEntity(dto);
		entity.setId(id); // Garante que o ID da URL seja o ID processado

		// Busca o original para preservar valores controlados pelo sistema
		E original = service.findByIdActive(id);
		entity.setAtivo(original.isAtivo());
		entity.setDataHoraCriacao(original.getDataHoraCriacao());

		E updated = service.update(entity);
		return ResponseEntity.ok(adapter.toDto(updated));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity delete(@PathVariable UUID id) {
		service.delete(id);
		return ResponseEntity.ok("Registro removido com sucesso.");
	}
}