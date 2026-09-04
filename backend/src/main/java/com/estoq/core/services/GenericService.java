package com.estoq.core.services;

import com.estoq.core.domains.BaseModel;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.exceptions.FieldValidationException;
import com.estoq.core.exceptions.RuleValidationException;
import com.estoq.core.repositories.IGenericRepository;
import com.estoq.core.validations.IGenericValidation;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

public abstract class GenericService<E extends BaseModel, R extends IGenericRepository<E>, V extends IGenericValidation<E, R>>
		implements IGenericService<E, R, V> {

	@Autowired
	protected R repository;

	@Autowired
	protected V validation;

	@Override
	@Transactional(readOnly = true)
	public E findByIdActive(UUID id) {
		try {
			return repository.findByIdAndAtivoTrue(id).orElseThrow(() -> new BusinessException(
					"O registro com o ID informado não foi encontrado ou está inativo.", HttpStatus.NOT_FOUND));
		} catch (BusinessException be) {
			throw be;
		} catch (Exception e) {
			throw new BusinessException("Erro ao localizar o registro em " + getEntityName(), e);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Page<E> findAllActive(Pageable pageable) {
		try {
			return repository.findAllByAtivoTrue(pageable);
		} catch (Exception e) {
			throw new BusinessException("Erro ao localizar os registros em " + getEntityName(), e);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<E> findAllActive() {
		return repository.findAll()
				.stream()
				.filter(BaseModel::isAtivo)
				.toList();
	}

	@Override
	@Transactional
	public E insert(E entity) {
		try {
			validation.validateFieldsInsert(entity);
			validation.validateInsert(entity);
			beforeInsert(entity);
			E savedEntity = repository.save(entity);
			afterInsert(savedEntity, entity);
			return savedEntity;
		} catch (BusinessException | FieldValidationException | RuleValidationException be) {
			throw be;
		} catch (DataAccessException dae) {
			throw new BusinessException("Falha de persistência ao inserir " + getEntityName(), dae);
		} catch (Exception e) {
			throw new BusinessException("Erro inesperado ao processar inserção em " + getEntityName(), e);
		}
	}

	@Override
	@Transactional
	public E update(E entity) {
		try {
			validation.validateFieldsUpdate(entity);
			validation.validateUpdate(entity);
			beforeUpdate(entity);
			E savedEntity = repository.save(entity);
			afterUpdate(savedEntity, entity);
			return savedEntity;
		} catch (BusinessException | FieldValidationException | RuleValidationException be) {
			throw be;
		} catch (DataAccessException dae) {
			throw new BusinessException("Erro de integridade ao atualizar " + getEntityName(), dae);
		} catch (Exception e) {
			throw new BusinessException("Erro inesperado ao processar atualização em " + getEntityName(), e);
		}
	}

	@Override
	@Transactional
	public void delete(UUID id) {
		try {
			validation.validateDelete(id);
			E entity = findByIdActive(id);
			beforeDelete(entity);
			entity.setAtivo(false);
			repository.save(entity);
			afterDelete(entity);
		} catch (BusinessException | FieldValidationException | RuleValidationException be) {
			throw be;
		} catch (Exception e) {
			throw new BusinessException("Não foi possível excluir o registro em " + getEntityName(), e);
		}
	}

	// Auxiliar para mensagens dinâmicas
	protected String getEntityName() {
		return this.getClass().getSimpleName().replace("Service", "");
	}

	// Métodos Hook (Ganchos) para serem sobrescritos no Business
	protected void beforeInsert(E entity) {
	}

	protected void afterInsert(E saved, E original) {
	}

	protected void beforeUpdate(E entity) {
	}

	protected void afterUpdate(E saved, E original) {
	}

	protected void beforeDelete(E entity) {
	}

	protected void afterDelete(E entity) {
	}
}