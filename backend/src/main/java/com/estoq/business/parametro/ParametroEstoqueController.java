package com.estoq.business.parametro;

import com.estoq.business.parametro.ParametroEstoqueModel;
import com.estoq.business.parametro.ParametroEstoqueService;
import com.estoq.business.produto.IProdutoRepository;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.business.usuario.UsuarioModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/parametros-estoque")
public class ParametroEstoqueController {

	@Autowired
	private ParametroEstoqueService parametroEstoqueService;

	@Autowired
	private IProdutoRepository produtoRepository;

	@GetMapping("/produto/{produtoId}")
	public ResponseEntity<ParametroEstoqueView> getPorProduto(@PathVariable UUID produtoId) {
		ParametroEstoqueModel par = parametroEstoqueService.getPorProduto(produtoId);
		return ResponseEntity.ok(toView(par));
	}

	@PutMapping("/produto/{produtoId}")
	public ResponseEntity<ParametroEstoqueView> atualizar(@PathVariable UUID produtoId,
			@RequestBody Map<String, Object> body) {
		Integer tempo = body.get("tempoReposicaoDias") == null ? null
				: ((Number) body.get("tempoReposicaoDias")).intValue();
		Integer periodo = body.get("periodoAnaliseDias") == null ? null
				: ((Number) body.get("periodoAnaliseDias")).intValue();
		BigDecimal minimo = body.get("estoqueMinimo") == null ? null
				: new BigDecimal(body.get("estoqueMinimo").toString());
		return ResponseEntity.ok(toView(parametroEstoqueService.atualizar(produtoId, tempo, periodo, minimo)));
	}

	@PostMapping("/produto/{produtoId}/recalcular")
	public ResponseEntity<ParametroEstoqueView> recalcular(@PathVariable UUID produtoId) {
		return ResponseEntity.ok(toView(parametroEstoqueService.recalcular(produtoId)));
	}

	@PostMapping("/resetar-todos")
	public ResponseEntity<Map<String, Object>> resetarTodos(
			@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		int qtd = parametroEstoqueService.resetarTodos(usuario);
		return ResponseEntity.ok(Map.of("atualizados", qtd));
	}

	@GetMapping("/produtos-baixos")
	public ResponseEntity<Map<String, Object>> produtosBaixos() {
		long count = parametroEstoqueService.contarProdutosComEstoqueBaixo();
		return ResponseEntity.ok(Map.of("qtd", count));
	}

	private ParametroEstoqueView toView(ParametroEstoqueModel par) {
		ParametroEstoqueView v = new ParametroEstoqueView();
		ProdutoModel p = par.getProduto();
		v.setId(par.getId());
		v.setProdutoId(p != null ? p.getId() : null);
		v.setProdutoNome(p != null ? p.getNome() : null);
		v.setUnidadeMedida(p != null && p.getUnidadeMedida() != null ? p.getUnidadeMedida().name() : null);
		v.setEstoqueMinimo(par.getEstoqueMinimo());
		v.setEstoqueMedio(par.getEstoqueMedio());
		v.setEstoqueMaximo(par.getEstoqueMaximo());
		v.setConsumoMedioDiario(par.getConsumoMedioDiario());
		v.setTempoReposicaoDias(par.getTempoReposicaoDias());
		v.setPeriodoAnaliseDias(par.getPeriodoAnaliseDias());
		v.setDataAtualizacao(par.getDataAtualizacao());
		v.setSaldoAtual(parametroEstoqueService.obterSaldoProduto(produtoId(p)));
		return v;
	}

	private UUID produtoId(ProdutoModel p) {
		return p != null ? p.getId() : null;
	}
}