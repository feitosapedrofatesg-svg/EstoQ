package com.estoq.business.balanco;

import com.estoq.business.usuario.UsuarioModel;
import com.estoq.core.domains.BaseModel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "balancos")
public class BalancoModel extends BaseModel {

	@Column(name = "data_hora", nullable = false)
	private LocalDateTime dataHora;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo", length = 20, nullable = false)
	private PeriodicidadeBalanco tipo;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", length = 20, nullable = false)
	private StatusBalanco status;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "responsavel_id")
	private UsuarioModel responsavel;

	@OneToMany(mappedBy = "balanco", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ItemBalancoModel> itens = new ArrayList<>();

	public void adicionarItem(ItemBalancoModel item) {
		item.setBalanco(this);
		itens.add(item);
	}

	public void apurarDiferencas() {
		for (ItemBalancoModel item : itens) {
			item.calcularDiferenca();
		}
	}

	public void confirmar() {
		this.status = StatusBalanco.CONCLUIDO;
	}

	public void cancelar() {
		this.status = StatusBalanco.CANCELADO;
	}

	public void reabrir() {
		this.status = StatusBalanco.EM_ANDAMENTO;
	}

	public boolean isPendente() {
		return status == StatusBalanco.PENDENTE;
	}
}