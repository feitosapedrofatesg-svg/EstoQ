package com.estoq.api.controllers;

import com.estoq.api.dto.EstoqueView;
import com.estoq.business.estoque.EstoquePeriodoDTO;
import com.estoq.business.estoque.EstoquePeriodoMapper;
import com.estoq.business.estoque.EstoquePeriodoModel;
import com.estoq.business.estoque.EstoquePeriodoService;
import com.estoq.business.periodo.IPeriodoRepository;
import com.estoq.core.controllers.GenericController;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.helpers.NumeroUtil;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/estoque")
@RequiredArgsConstructor
public class EstoqueController
		extends GenericController<EstoquePeriodoModel, EstoquePeriodoDTO, EstoquePeriodoService, EstoquePeriodoMapper> {

	private final EstoquePeriodoService estoqueService;
	private final IPeriodoRepository periodoRepository;

	@GetMapping("/periodo/{periodoId}")
	public ResponseEntity<List<EstoqueView>> listarPorPeriodo(@PathVariable UUID periodoId) {
		if (!periodoRepository.existsById(periodoId)) {
			throw new BusinessException("Período não encontrado.", HttpStatus.NOT_FOUND);
		}
		List<EstoqueView> views = estoqueService.listarPorPeriodo(periodoId).stream()
				.map(this::toView).toList();
		return ResponseEntity.ok(views);
	}

	@PostMapping("/preparar/{periodoId}")
	public ResponseEntity<java.util.Map<String, Object>> preparar(@PathVariable UUID periodoId) {
		int criados = estoqueService.prepararPeriodo(periodoId);
		return ResponseEntity.ok(java.util.Map.of("criados", criados));
	}

	private EstoqueView toView(EstoquePeriodoModel ep) {
		EstoqueView v = new EstoqueView();
		v.setId(ep.getId());
		v.setPeriodoId(ep.getPeriodo().getId());
		v.setPeriodoNome(ep.getPeriodo().getNome());
		v.setProdutoId(ep.getProduto().getId());
		v.setProdutoNome(ep.getProduto().getNome());
		v.setUnidade(ep.getProduto().getUnidade());
		v.setQuantidadeInicial(ep.getQuantidadeInicial());
		v.setValorUnitarioInicial(ep.getValorUnitarioInicial());
		v.setValorInicial(NumeroUtil.money(
				NumeroUtil.multiplica(ep.getQuantidadeInicial(), ep.getValorUnitarioInicial())));
		v.setQuantidadeFinal(ep.getQuantidadeFinal());
		v.setValorUnitarioFinal(ep.getValorUnitarioFinal());
		v.setValorFinal(NumeroUtil.money(
				NumeroUtil.multiplica(ep.getQuantidadeFinal(), ep.getValorUnitarioFinal())));
		return v;
	}
}