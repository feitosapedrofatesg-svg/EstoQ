package com.estoq.business.produto;

import com.estoq.business.movimentacao.MovimentacaoView;
import com.estoq.business.relatorio.CMVReportDTO;
import com.estoq.business.relatorio.RelatorioService;
import com.estoq.business.categoria.CategoriaModel;
import com.estoq.business.categoria.ICategoriaRepository;
import com.estoq.business.movimentacao.MovimentacaoService;
import com.estoq.business.produto.IProdutoRepository;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.business.produto.UnidadeMedida;
import com.estoq.business.usuario.UsuarioModel;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.helpers.NumeroUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/integracao")
public class IntegracaoController {

	@Autowired
	private MovimentacaoService movimentacaoService;

	@Autowired
	private ProdutoImportadorHelper importadorHelper;

	@Autowired
	private RelatorioService relatorioService;

	@PostMapping("/importar-produtos")
	public ResponseEntity<Map<String, Object>> importarProdutos(@RequestParam("arquivo") MultipartFile arquivo,
			@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		List<Map<String, String>> linhas = lerCsv(arquivo);
		int importadas = 0;
		int ignoradas = 0;
		List<String> erros = new ArrayList<>();
		for (Map<String, String> linha : linhas) {
			try {
				String nome = valor(linha, "produto");
				String categoria = valor(linha, "categoria");
				String unidade = valor(linha, "unidade");
				BigDecimal quantidade = decimal(valor(linha, "quantidade"));
				BigDecimal valorTotal = decimal(valor(linha, "valor"));
				LocalDate dataValidade = data(valor(linha, "datavalidade"));

				MovimentacaoView view = movimentacaoService.registrarEntrada(
						importadorHelper.criarProduto(nome, categoria, unidade).getId(), usuario, quantidade,
						valorTotal, null, dataValidade, "Importação de planilha");
				if (view != null) {
					importadas++;
				}
			} catch (RuntimeException ex) {
				ignoradas++;
				erros.add(ex.getMessage());
			}
		}
		Map<String, Object> resultado = new HashMap<>();
		resultado.put("importadas", importadas);
		resultado.put("ignoradas", ignoradas);
		resultado.put("erros", erros.stream().limit(20).toList());
		return ResponseEntity.ok(resultado);
	}

	@PostMapping("/importar-movimentacoes")
	public ResponseEntity<Map<String, Object>> importarMovimentacoes(@RequestParam("arquivo") MultipartFile arquivo,
			@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		List<Map<String, String>> linhas = lerCsv(arquivo);
		int importadas = 0;
		int ignoradas = 0;
		List<String> erros = new ArrayList<>();
		for (Map<String, String> linha : linhas) {
			try {
				String nome = valor(linha, "produto");
				String tipo = valor(linha, "tipo").toUpperCase();
				BigDecimal quantidade = decimal(valor(linha, "quantidade"));
				BigDecimal valor = decimal(valor(linha, "valor"));
				var produto = importadorHelper.criarProduto(nome, valor(linha, "categoria"), valor(linha, "unidade"));
				if ("ENTRADA".equals(tipo)) {
					movimentacaoService.registrarEntrada(produto.getId(), usuario, quantidade, valor, null, null,
							"Importação de planilha");
				} else if ("CONSUMO".equals(tipo)) {
					movimentacaoService.registrarConsumo(produto.getId(), usuario, quantidade, null,
							"Importação de planilha");
				} else {
					throw new BusinessException("Tipo inválido: " + tipo + " (use ENTRADA ou CONSUMO).",
							HttpStatus.BAD_REQUEST);
				}
				importadas++;
			} catch (RuntimeException ex) {
				ignoradas++;
				erros.add(ex.getMessage());
			}
		}
		Map<String, Object> resultado = new HashMap<>();
		resultado.put("importadas", importadas);
		resultado.put("ignoradas", ignoradas);
		resultado.put("erros", erros.stream().limit(20).toList());
		return ResponseEntity.ok(resultado);
	}

	@GetMapping("/exportar/estoque")
	public void exportarEstoque(HttpServletResponse response) throws IOException {
		response.setContentType("text/csv; charset=UTF-8");
		response.setHeader("Content-Disposition", "attachment; filename=estoque.csv");
		StringBuilder sb = new StringBuilder("Produto;Categoria;Unidade;Saldo;EstoqueMinimo\n");
		for (var p : importadorHelper.listarProdutos()) {
			sb.append(escapar(p.getNome())).append(';')
					.append(escapar(p.getCategoria() != null ? p.getCategoria().getNome() : "")).append(';')
					.append(p.getUnidadeMedida() != null ? p.getUnidadeMedida().name() : "").append(';')
					.append(NumeroUtil.s(p.getSaldoAtual())).append(';')
					.append(NumeroUtil.s(p.getEstoqueMinimo())).append('\n');
		}
		response.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
	}

	@GetMapping("/exportar/cmv")
	public void exportarCmv(@RequestParam(required = false) LocalDate inicio, @RequestParam(required = false) LocalDate fim,
			HttpServletResponse response) throws IOException {
		CMVReportDTO rel = relatorioService.cmv(inicio, fim, null);
		response.setContentType("text/csv; charset=UTF-8");
		response.setHeader("Content-Disposition", "attachment; filename=cmv.csv");
		StringBuilder sb = new StringBuilder("Produto;Categoria;ConsumoQtd;ConsumoValor;DesperdicioQtd;DesperdicioValor;Total\n");
		for (var item : rel.getItens()) {
			sb.append(escapar(item.getProdutoNome())).append(';')
					.append(escapar(item.getCategoriaNome() != null ? item.getCategoriaNome() : "")).append(';')
					.append(item.getConsumoQtd()).append(';')
					.append(item.getConsumoValor()).append(';')
					.append(item.getDesperdicioQtd()).append(';')
					.append(item.getDesperdicioValor()).append(';')
					.append(item.getTotalValor()).append('\n');
		}
		response.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
	}

	// ------------------------------------------------------------ CSV helpers

	private List<Map<String, String>> lerCsv(MultipartFile arquivo) {
		List<Map<String, String>> linhas = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(arquivo.getInputStream(), StandardCharsets.UTF_8))) {
			String header = reader.readLine();
			if (header == null) {
				throw new BusinessException("Arquivo vazio.", HttpStatus.BAD_REQUEST);
			}
			header = header.replace("\uFEFF", "");
			String[] colunas = header.split(";");
			if (colunas.length == 1) {
				colunas = header.split(",");
			}
			String linha;
			while ((linha = reader.readLine()) != null) {
				if (linha.isBlank()) {
					continue;
				}
				String[] valores = linha.split(";");
				if (valores.length == 1) {
					valores = linha.split(",");
				}
				Map<String, String> map = new HashMap<>();
				for (int i = 0; i < colunas.length && i < valores.length; i++) {
					map.put(colunas[i].trim().toLowerCase(), valores[i].trim());
				}
				linhas.add(map);
			}
		} catch (IOException ex) {
			throw new BusinessException("Não foi possível ler o arquivo: " + ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
		return linhas;
	}

	private String valor(Map<String, String> linha, String chave) {
		String v = linha.get(chave);
		return v == null ? "" : v;
	}

	private BigDecimal decimal(String v) {
		if (v == null || v.isBlank()) {
			return BigDecimal.ZERO;
		}
		return new BigDecimal(v.replace('.', '.').replace(',', '.').replace("R$", "").trim());
	}

	private LocalDate data(String v) {
		if (v == null || v.isBlank()) {
			return null;
		}
		for (String padrao : new String[] { "dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd" }) {
			try {
				return java.time.format.DateTimeFormatter.ofPattern(padrao).withResolverStyle(
						java.time.format.ResolverStyle.STRICT).parse(v, LocalDate::from);
			} catch (RuntimeException ignored) {
				// tenta o próximo padrão
			}
		}
		return null;
	}

	private String escapar(String v) {
		return "\"" + (v == null ? "" : v.replace("\"", "\"\"")) + "\"";
	}
}