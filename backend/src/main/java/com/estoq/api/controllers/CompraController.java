package com.estoq.api.controllers;

import com.estoq.api.dto.CompraView;
import com.estoq.business.compra.CompraDTO;
import com.estoq.business.compra.CompraMapper;
import com.estoq.business.compra.CompraModel;
import com.estoq.business.compra.CompraService;
import com.estoq.business.compra.ICompraRepository;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/compras")
@RequiredArgsConstructor
public class CompraController extends GenericController<CompraModel, CompraDTO, CompraService, CompraMapper> {

	private final ICompraRepository compraRepository;
	private final IPeriodoRepository periodoRepository;

	@GetMapping("/periodo/{periodoId}")
	public ResponseEntity<List<CompraView>> listarPorPeriodo(@PathVariable UUID periodoId) {
		if (!periodoRepository.existsById(periodoId)) {
			throw new BusinessException("Período não encontrado.", HttpStatus.NOT_FOUND);
		}
		List<CompraView> views = compraRepository
				.findAllByPeriodoAndAtivoTrueOrderByDataCompraAsc(
						periodoRepository.findById(periodoId).orElseThrow())
				.stream().map(this::toView).toList();
		return ResponseEntity.ok(views);
	}

	private CompraView toView(CompraModel c) {
		CompraView v = new CompraView();
		v.setId(c.getId());
		v.setProdutoId(c.getProduto().getId());
		v.setProdutoNome(c.getProduto().getNome());
		v.setUnidade(c.getProduto().getUnidade());
		v.setPeriodoId(c.getPeriodo().getId());
		v.setPeriodoNome(c.getPeriodo().getNome());
		v.setQuantidade(c.getQuantidade());
		v.setPrecoUnitario(c.getPrecoUnitario());
		v.setTotal(NumeroUtil.money(c.getQuantidade().multiply(c.getPrecoUnitario())));
		v.setDataCompra(c.getDataCompra());
		return v;
	}
}