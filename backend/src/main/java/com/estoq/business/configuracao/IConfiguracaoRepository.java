package com.estoq.business.configuracao;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IConfiguracaoRepository extends JpaRepository<ConfiguracaoModel, UUID> {

	Optional<ConfiguracaoModel> findByChaveAndAtivoTrue(String chave);
}