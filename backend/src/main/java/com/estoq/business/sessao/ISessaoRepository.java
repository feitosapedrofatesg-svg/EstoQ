package com.estoq.business.sessao;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ISessaoRepository extends JpaRepository<SessaoModel, UUID> {

	Optional<SessaoModel> findByToken(String token);

	void deleteByUsuarioId(UUID usuarioId);
}