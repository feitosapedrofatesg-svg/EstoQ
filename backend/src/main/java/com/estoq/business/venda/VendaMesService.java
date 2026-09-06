package com.estoq.business.venda;

import com.estoq.business.auditoria.AuditService;
import com.estoq.business.usuario.UsuarioModel;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.helpers.NumeroUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class VendaMesService {

	@Autowired
	private IVendaMesRepository repository;

	@Autowired
	private AuditService auditService;

	@Transactional(readOnly = true)
	public VendaMesView obter(int ano, int mes) {
		return VendaMesView.of(repository.findByAnoAndMesAndAtivoTrue(ano, mes).orElseGet(() -> {
			VendaMesModel novo = new VendaMesModel();
			novo.setAno(ano);
			novo.setMes(mes);
			novo.setValorVendas(BigDecimal.ZERO);
			return novo;
		}));
	}

	@Transactional
	public VendaMesView salvar(int ano, int mes, BigDecimal valor, UsuarioModel usuario) {
		BigDecimal valorNorm = NumeroUtil.money(valor);
		if (valorNorm.signum() < 0) {
			throw new BusinessException("O valor de vendas não pode ser negativo.", HttpStatus.BAD_REQUEST);
		}
		VendaMesModel m = repository.findByAnoAndMesAndAtivoTrue(ano, mes).orElse(new VendaMesModel());
		boolean novo = m.getId() == null;
		m.setAno(ano);
		m.setMes(mes);
		m.setValorVendas(valorNorm);
		VendaMesModel salvo = repository.save(m);
		if (salvo != null) {
			auditService.registrar(novo ? "CRIACAO" : "ALTERACAO", "VENDA_MES", String.valueOf(salvo.getId()),
					"Vendas do mês " + mes + "/" + ano + " definidas em R$ " + valorNorm, usuario);
		}
		return VendaMesView.of(salvo);
	}
}