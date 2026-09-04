package com.estoq.core.repositories;

import com.estoq.core.domains.BaseModel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Contrato genérico de acesso a dados.
 *
 * @param <E> Entidade que estende BaseModel
 */
@NoRepositoryBean
public interface IGenericRepository<E extends BaseModel> extends JpaRepository<E, UUID> {

	Optional<E> findByIdAndAtivoTrue(UUID id);

	Page<E> findAllByAtivoTrue(Pageable pageable);

	List<E> findAllByAtivoTrue();

	boolean existsById(UUID id);
}