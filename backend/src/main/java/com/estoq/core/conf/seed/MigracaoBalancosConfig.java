package com.estoq.core.conf.seed;

import com.estoq.business.balanco.StatusBalanco;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * Migrações idempotentes executadas na inicialização, antes do restante do sistema.
 * <p>
 * O Hibernate cria colunas de enum como ENUM no H2 (modo PostgreSQL); em bancos
 * existentes ele não expande o ENUM quando novos valores surgem. Aqui o status do
 * balanço é ampliado para incluir {@link StatusBalanco#CANCELADO} sem o admin
 * precisar rodar SQL manual.
 */
@Configuration
public class MigracaoBalancosConfig {

	private static final Logger log = LoggerFactory.getLogger(MigracaoBalancosConfig.class);

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Bean
	@Order(1)
	public CommandLineRunner migrarStatusCANCELADO() {
		return args -> {
			try {
				List<Map<String, Object>> linhas = jdbcTemplate.queryForList(
						"SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
								+ "WHERE TABLE_NAME = 'BALANCOS' AND COLUMN_NAME = 'STATUS' AND DATA_TYPE = 'ENUM'");
				if (!linhas.isEmpty()) {
					jdbcTemplate.execute("ALTER TABLE BALANCOS ALTER COLUMN STATUS TYPE "
							+ "ENUM('PENDENTE','EM_ANDAMENTO','CONCLUIDO','CANCELADO')");
					log.info("Coluna BALANCOS.STATUS ampliada para incluir CANCELADO.");
				}
			} catch (Exception e) {
				log.warn("Migração de BALANCOS.STATUS não executada: {}", e.getMessage());
			}
		};
	}
}