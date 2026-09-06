package com.estoq.business.auditoria;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IAuditLogRepository extends JpaRepository<AuditLogModel, UUID> {

	List<AuditLogModel> findTop20ByAtivoTrueOrderByDataHoraDesc();
}