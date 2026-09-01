package com.estoq.business.consumodiario;

import com.estoq.business.produto.IProdutoRepository;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.business.usuario.Perfil;
import com.estoq.business.usuario.UsuarioModel;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.exceptions.FieldValidationException;
import com.estoq.core.helpers.NumeroUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsumoDiarioService {

	@Autowired
	private IConsumoDiarioRepository repository;

	@Autowired
	private IProdutoRepository produtoRepository;

	@Transactional
	public ConsumoDiarioModel registrar(ConsumoDiarioDTO dto, UsuarioModel usuario) {
		if (dto == null) {
			throw new FieldValidationException("root", "A solicitação está vazia.");
		}
		if (dto.getProdutoId() == null) {
			throw new FieldValidationException("produtoId", "O produto é obrigatório.");
		}
		ProdutoModel produto = produtoRepository.findByIdAndAtivoTrue(dto.getProdutoId())
				.orElseThrow(() -> new BusinessException("Produto não encontrado.", HttpStatus.NOT_FOUND));
		if (dto.getTipo() == null) {
			throw new FieldValidationException("tipo", "Informe o tipo: USADO ou ABERTO.");
		}
		if (dto.getQuantidade() == null || dto.getQuantidade().signum() <= 0) {
			throw new FieldValidationException("quantidade", "A quantidade deve ser maior que zero.");
		}

		ConsumoDiarioModel model = new ConsumoDiarioModel();
		model.setData(dto.getData() != null ? dto.getData() : LocalDate.now());
		model.setProduto(produto);
		model.setTipo(dto.getTipo());
		model.setQuantidade(NumeroUtil.money(dto.getQuantidade()));
		model.setUsuario(usuario);
		model.setDataHoraRegistro(LocalDateTime.now());
		return repository.save(model);
	}

	@Transactional(readOnly = true)
	public List<ConsumoDiarioModel> listar(LocalDate data) {
		return repository.findByDataAndAtivoTrueOrderByDataHoraRegistroAsc(data != null ? data : LocalDate.now());
	}

	@Transactional(readOnly = true)
	public List<ConsumoDiarioModel> listarTudo() {
		return repository.findAllByAtivoTrueOrderByDataHoraRegistroDesc();
	}

	@Transactional
	public void excluir(UUID id, UsuarioModel usuario) {
		ConsumoDiarioModel model = repository.findByIdAndAtivoTrue(id)
				.orElseThrow(() -> new BusinessException("Registro de uso não encontrado.", HttpStatus.NOT_FOUND));
		if (usuario.getPerfil() == Perfil.COZINHA
				&& (!model.getData().equals(LocalDate.now())
						|| !model.getUsuario().getId().equals(usuario.getId()))) {
			throw new BusinessException(
					"A cozinha só pode excluir registros que criou no dia de hoje.", HttpStatus.FORBIDDEN);
		}
		model.setAtivo(false);
		repository.save(model);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> sumario(LocalDate data) {
		List<ConsumoDiarioModel> registros = listar(data);
		Map<String, Object[]> porProduto = new LinkedHashMap<>();
		for (ConsumoDiarioModel r : registros) {
			String chave = r.getProduto().getId() + "::" + r.getTipo().name();
			Object[] item = porProduto.computeIfAbsent(chave, k -> new Object[] {
					r.getProduto().getId(), r.getProduto().getNome(), r.getProduto().getUnidade(),
					r.getTipo().name(), java.math.BigDecimal.ZERO });
			item[4] = NumeroUtil.soma((java.math.BigDecimal) item[4], r.getQuantidade());
		}
		Map<String, Object> resultado = new LinkedHashMap<>();
		resultado.put("data", data);
		resultado.put("totalRegistros", registros.size());
		resultado.put("itens", porProduto.values().stream()
				.map(o -> Map.of(
						"produtoId", o[0], "produtoNome", o[1], "unidade", o[2],
						"tipo", o[3], "quantidade", o[4]))
				.toList());
		return resultado;
	}
}