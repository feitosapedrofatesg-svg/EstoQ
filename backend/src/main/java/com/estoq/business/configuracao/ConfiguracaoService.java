package com.estoq.business.configuracao;

import com.estoq.business.auditoria.AuditService;
import com.estoq.business.usuario.UsuarioModel;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.helpers.NumeroUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ConfiguracaoService {

	public static final String META_DESPERDICIO = "meta.desperdicio.percentual";
	private static final BigDecimal META_DESPERDICIO_PADRAO = new BigDecimal("0.10");

	public static final String META_CMV = "meta.cmv.percentual";
	private static final BigDecimal META_CMV_PADRAO = new BigDecimal("0.40");

	@Autowired
	private IConfiguracaoRepository repository;

	@Autowired
	private AuditService auditService;

	@Transactional(readOnly = true)
	public Map<String, String> listar() {
		Map<String, String> out = new LinkedHashMap<>();
		out.put(META_DESPERDICIO, META_DESPERDICIO_PADRAO.toPlainString());
		out.put(META_CMV, META_CMV_PADRAO.toPlainString());
		for (ConfiguracaoModel c : repository.findAll()) {
			if (c.isAtivo()) {
				out.put(c.getChave(), c.getValor());
			}
		}
		return out;
	}

	@Transactional(readOnly = true)
	public BigDecimal obterBigDecimal(String chave, BigDecimal padrao) {
		return repository.findByChaveAndAtivoTrue(chave)
				.map(c -> {
					try {
						return new BigDecimal(c.getValor().replace(",", "."));
					} catch (NumberFormatException ex) {
						return padrao;
					}
				})
				.orElse(padrao);
	}

	@Transactional
	public void salvar(String chave, String valor, UsuarioModel usuario) {
		if (chave == null || chave.isBlank()) {
			throw new BusinessException("Chave de configuração inválida.", HttpStatus.BAD_REQUEST);
		}
		String valorNorm = valor == null ? "" : valor.trim();
		ConfiguracaoModel cfg = repository.findByChaveAndAtivoTrue(chave).orElseGet(() -> {
			ConfiguracaoModel novo = new ConfiguracaoModel();
			novo.setChave(chave);
			return novo;
		});
		if (META_DESPERDICIO.equals(chave) || META_CMV.equals(chave)) {
			BigDecimal pct;
			try {
				pct = new BigDecimal(valorNorm.replace(",", "."));
			} catch (NumberFormatException ex) {
				throw new BusinessException(
						"Meta inválida. Informe um percentual como 10 ou 0,10.",
						HttpStatus.BAD_REQUEST);
			}
			if (pct.signum() < 0 || pct.compareTo(new BigDecimal("100")) > 0) {
				throw new BusinessException("A meta percentual deve estar entre 0 e 100.", HttpStatus.BAD_REQUEST);
			}
			valorNorm = pct.compareTo(new BigDecimal("1")) > 0
					? NumeroUtil.divide(pct, BigDecimal.valueOf(100), 4).toPlainString()
					: pct.toPlainString();
		}
		cfg.setValor(valorNorm);
		repository.save(cfg);
		auditService.registrar("ALTERACAO", "CONFIGURACAO", chave, "Configuração " + chave + " = " + valorNorm, usuario);
	}
}