package com.estoq.business.balanco;

import com.estoq.core.domains.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "configuracoes_balanco")
public class ConfiguracaoBalancoModel extends BaseModel {

	@Enumerated(EnumType.STRING)
	@Column(name = "periodicidade", length = 20, nullable = false)
	private PeriodicidadeBalanco periodicidade;

	/** Dia do mês (mensal) ou dia da semana 1=seg..7=dom (semanal) usado na execução. */
	@Column(name = "dia_execucao", nullable = false)
	private Integer diaExecucao;

	@Column(name = "proxima_execucao")
	private LocalDate proximaExecucao;

	public LocalDate calcularProximaExecucao(LocalDate referencia, LocalDate ultimaExecucao) {
		switch (periodicidade) {
			case MENSAL: {
				// Próxima execução = próxima ocorrência do dia escolhido depois da
				// referência (hoje ou última execução futura), sem pular o mês corrente.
				LocalDate base = referencia;
				if (ultimaExecucao != null && ultimaExecucao.isAfter(base)) {
					base = ultimaExecucao;
				}
				int dia = Math.min(Math.max(diaExecucao, 1), base.lengthOfMonth());
				LocalDate prox = base.withDayOfMonth(dia);
				if (!prox.isAfter(base)) {
					LocalDate mesFuturo = base.plusMonths(1);
					dia = Math.min(Math.max(diaExecucao, 1), mesFuturo.lengthOfMonth());
					prox = mesFuturo.withDayOfMonth(dia);
				}
				return proximaExecucao = prox;
			}
			case SEMANAL: {
				final int alvo = Math.min(Math.max(diaExecucao, 1), 7);
				LocalDate base = ultimaExecucao != null ? ultimaExecucao : referencia;
				LocalDate prox = base;
				while (prox.getDayOfWeek().getValue() != alvo) {
					prox = prox.plusDays(1);
				}
				if (!prox.isAfter(base)) {
					prox = prox.plusDays(7);
				}
				return proximaExecucao = prox;
			}
			default: {
				// Diária: próxima execução no dia seguinte, independente do dia de execução.
				LocalDate base = ultimaExecucao != null ? ultimaExecucao : referencia;
				LocalDate prox = base.plusDays(1);
				return proximaExecucao = prox;
			}
		}
	}

	/** Há um balanço pendente quando a próxima execução não caiu ou existe contagem em aberto. */
	public boolean balancoEstaPendente(LocalDate referencia, long balancosEmAndamento) {
		if (balancosEmAndamento > 0) {
			return true;
		}
		return proximaExecucao != null && !proximaExecucao.isAfter(referencia);
	}
}