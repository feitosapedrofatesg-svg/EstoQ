package com.estoq.business.venda;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IVendaMesRepository extends JpaRepository<VendaMesModel, UUID> {

	Optional<VendaMesModel> findByAnoAndMesAndAtivoTrue(int ano, int mes);
}