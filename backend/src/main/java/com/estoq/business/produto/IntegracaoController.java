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
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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

	/**
	 * Cria um produto a partir de um item de cupom/nota, ou devolve o produto já
	 * existente com o mesmo nome (busca case-insensitive). Usado na confirmação de
	 * entradas por cupom quando o item ainda não está no catálogo.
	 */
	@PostMapping("/criar-produto-cupom")
	public ResponseEntity<Map<String, Object>> criarProdutoCupom(@RequestBody Map<String, String> body,
			@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		String nome = body.get("nome");
		String unidade = body.get("unidadeMedida");
		ProdutoModel produto = importadorHelper.criarProduto(nome, null, unidade);
		Map<String, Object> resultado = new HashMap<>();
		resultado.put("id", produto.getId());
		resultado.put("nome", produto.getNome());
		resultado.put("unidadeMedida", produto.getUnidadeMedida() != null ? produto.getUnidadeMedida().name() : null);
		return ResponseEntity.ok(resultado);
	}

	@PostMapping("/importar-produtos")
	public ResponseEntity<Map<String, Object>> importarProdutos(@RequestParam("arquivo") MultipartFile arquivo,
			@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		List<Map<String, String>> linhas = lerLinhas(arquivo);
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
						valorTotal, null, null, dataValidade, "Importação de planilha");
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
		List<Map<String, String>> linhas = lerLinhas(arquivo);
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
							null, "Importação de planilha");
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
		StringBuilder sb = new StringBuilder();
		sb.append("EstoQ — Relatório de Estoque\n");
		sb.append("Gerado em ").append(agora()).append("\n\n");
		sb.append("Produto;Categoria;Unidade;Saldo;EstoqueMinimo;CustoMedio(R$);ValorEstoque(R$);%doValor;Situacao\n");

		BigDecimal totalSaldo = BigDecimal.ZERO;
		BigDecimal totalMinimo = BigDecimal.ZERO;
		BigDecimal totalValor = BigDecimal.ZERO;
		long semEstoque = 0;
		long paraRepor = 0;
		List<LinhaEstoque> linhas = new ArrayList<>();
		for (var p : importadorHelper.listarProdutos()) {
			BigDecimal saldo = NumeroUtil.s(p.getSaldoAtual());
			BigDecimal minimo = NumeroUtil.s(p.getEstoqueMinimo());
			BigDecimal custo = NumeroUtil.money(movimentacaoService.custoMedioProduto(p.getId()));
			BigDecimal valor = NumeroUtil.money(NumeroUtil.multiplica(saldo, custo));
			String situacao;
			if (saldo.signum() <= 0) {
				situacao = "SEM ESTOQUE";
				semEstoque++;
			} else if (minimo.signum() > 0 && saldo.compareTo(minimo) < 0) {
				situacao = "REPOR";
				paraRepor++;
			} else {
				situacao = "OK";
			}
			linhas.add(new LinhaEstoque(p.getNome(),
					p.getCategoria() != null ? p.getCategoria().getNome() : "",
					p.getUnidadeMedida() != null ? p.getUnidadeMedida().name() : "",
					saldo, minimo, custo, valor, situacao));
			totalSaldo = totalSaldo.add(saldo);
			totalMinimo = totalMinimo.add(minimo);
			totalValor = totalValor.add(valor);
		}
		for (var l : linhas) {
			sb.append(escapar(l.produto())).append(';')
					.append(escapar(l.categoria())).append(';')
					.append(l.unidade()).append(';')
					.append(num(l.saldo())).append(';').append(num(l.minimo())).append(';')
					.append(num(l.custo())).append(';').append(num(l.valor())).append(';')
					.append(pct(NumeroUtil.percentual(l.valor(), totalValor))).append(';')
					.append(escapar(l.situacao())).append('\n');
		}

		sb.append("TOTAL;;").append(num(totalSaldo)).append(';').append(num(totalMinimo)).append(";;")
				.append(num(totalValor)).append(';').append(pct(NumeroUtil.percentual(totalValor, totalValor)))
				.append(";\n");
		sb.append("\nResumo: ").append(linhas.size()).append(" produto(s) | Sem estoque: ").append(semEstoque)
				.append(" | Para repor: ").append(paraRepor)
				.append(" | Valor total do estoque (R$): ").append(num(totalValor)).append('\n');
		sb.append("Legenda: OK = saldo >= estoque minimo; REPOR = saldo < estoque minimo; SEM ESTOQUE = saldo <= 0.\n");
		response.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
	}

	@GetMapping("/planilha/estoque")
	public Map<String, Object> planilhaEstoque() {
		List<Map<String, Object>> linhas = new ArrayList<>();
		BigDecimal totalSaldo = BigDecimal.ZERO;
		BigDecimal totalMinimo = BigDecimal.ZERO;
		BigDecimal totalValor = BigDecimal.ZERO;
		long semEstoque = 0;
		long paraRepor = 0;
		for (var p : importadorHelper.listarProdutos()) {
			BigDecimal saldo = NumeroUtil.s(p.getSaldoAtual());
			BigDecimal minimo = NumeroUtil.s(p.getEstoqueMinimo());
			BigDecimal custo = NumeroUtil.money(movimentacaoService.custoMedioProduto(p.getId()));
			BigDecimal valor = NumeroUtil.money(NumeroUtil.multiplica(saldo, custo));
			String situacao;
			if (saldo.signum() <= 0) {
				situacao = "SEM ESTOQUE";
				semEstoque++;
			} else if (minimo.signum() > 0 && saldo.compareTo(minimo) < 0) {
				situacao = "REPOR";
				paraRepor++;
			} else {
				situacao = "OK";
			}
			linhas.add(Map.of(
					"produto", p.getNome(),
					"categoria", p.getCategoria() != null ? p.getCategoria().getNome() : "",
					"unidade", p.getUnidadeMedida() != null ? p.getUnidadeMedida().name() : "",
					"saldo", saldo,
					"estoqueMinimo", minimo,
					"custoMedio", custo,
					"valorEstoque", valor,
					"situacao", situacao));
			totalSaldo = totalSaldo.add(saldo);
			totalMinimo = totalMinimo.add(minimo);
			totalValor = totalValor.add(valor);
		}
		Map<String, Object> out = new HashMap<>();
		out.put("linhas", linhas);
		out.put("totalSaldo", totalSaldo);
		out.put("totalMinimo", totalMinimo);
		out.put("totalValor", totalValor);
		out.put("totalProdutos", linhas.size());
		out.put("semEstoque", semEstoque);
		out.put("paraRepor", paraRepor);
		out.put("geradoEm", agora());
		return out;
	}

	@GetMapping("/exportar/cmv")
	public void exportarCmv(@RequestParam(required = false) LocalDate inicio, @RequestParam(required = false) LocalDate fim,
			@RequestParam(required = false) BigDecimal vendas,
			HttpServletResponse response) throws IOException {
		CMVReportDTO rel = relatorioService.cmv(inicio, fim, vendas);
		response.setContentType("text/csv; charset=UTF-8");
		response.setHeader("Content-Disposition", "attachment; filename=cmv.csv");
		StringBuilder sb = new StringBuilder();
		sb.append("EstoQ — Relatório CMV (Custo de Mercadorias Vendidas)\n");
		sb.append("Periodo: ").append(rel.getDataInicio()).append(" a ").append(rel.getDataFim()).append('\n');
		sb.append("Formula: CMV = Estoque Inicial + Entradas - Estoque Final\n");
		sb.append("Nota: desperdicio real = o que saiu do estoque sem ser lançado como consumo\n");
		sb.append("Gerado em ").append(agora()).append("\n\n");
		sb.append("Produto;Categoria;Unidade;EstInicialQtd;EstInicialValor(R$);EntradasQtd;EntradasValor(R$);"
				+ "EstFinalQtd;EstFinalValor(R$);ConsumoQtd;ConsumoValor(R$);DesperdicioQtd;DesperdicioValor(R$);"
				+ "CMV(R$);Desp%doCMV;%doCMVTotal\n");

		BigDecimal s1 = BigDecimal.ZERO, s2 = BigDecimal.ZERO, s3 = BigDecimal.ZERO, s4 = BigDecimal.ZERO,
				s5 = BigDecimal.ZERO, s6 = BigDecimal.ZERO, s7 = BigDecimal.ZERO, s8 = BigDecimal.ZERO,
				s9 = BigDecimal.ZERO, s10 = BigDecimal.ZERO, s11 = BigDecimal.ZERO, s12 = BigDecimal.ZERO,
				s13 = BigDecimal.ZERO, s14 = BigDecimal.ZERO;
		for (var item : rel.getItens()) {
			BigDecimal eiQtd = NumeroUtil.s(item.getEstoqueInicialQtd());
			BigDecimal eiVal = NumeroUtil.money(item.getEstoqueInicialValor());
			BigDecimal enQtd = NumeroUtil.s(item.getEntradasQtd());
			BigDecimal enVal = NumeroUtil.money(item.getEntradasValor());
			BigDecimal efQtd = NumeroUtil.s(item.getEstoqueFinalQtd());
			BigDecimal efVal = NumeroUtil.money(item.getEstoqueFinalValor());
			BigDecimal coQtd = NumeroUtil.s(item.getConsumoQtd());
			BigDecimal coVal = NumeroUtil.money(item.getConsumoValor());
			BigDecimal deQtd = NumeroUtil.s(item.getDesperdicioQtd());
			BigDecimal deVal = NumeroUtil.money(item.getDesperdicioValor());
			BigDecimal cmvVal = NumeroUtil.money(item.getTotalValor());
			sb.append(escapar(item.getProdutoNome())).append(';')
					.append(escapar(item.getCategoriaNome() != null ? item.getCategoriaNome() : "")).append(';')
					.append(item.getUnidadeMedida() != null ? item.getUnidadeMedida() : "").append(';')
					.append(num(eiQtd)).append(';').append(num(eiVal)).append(';')
					.append(num(enQtd)).append(';').append(num(enVal)).append(';')
					.append(num(efQtd)).append(';').append(num(efVal)).append(';')
					.append(num(coQtd)).append(';').append(num(coVal)).append(';')
					.append(num(deQtd)).append(';').append(num(deVal)).append(';')
					.append(num(cmvVal)).append(';')
					.append(pct(NumeroUtil.percentual(deVal, cmvVal))).append(';')
					.append(pct(NumeroUtil.percentual(cmvVal, rel.getTotalGeral()))).append('\n');
			s1 = s1.add(eiQtd);
			s2 = s2.add(eiVal);
			s3 = s3.add(enQtd);
			s4 = s4.add(enVal);
			s5 = s5.add(efQtd);
			s6 = s6.add(efVal);
			s7 = s7.add(coQtd);
			s8 = s8.add(coVal);
			s9 = s9.add(deQtd);
			s10 = s10.add(deVal);
			s11 = s11.add(cmvVal);
			s12 = s12.add(NumeroUtil.percentual(deVal, cmvVal));
			s13 = s13.add(NumeroUtil.percentual(cmvVal, rel.getTotalGeral()));
			s14 = s14.add(BigDecimal.ONE);
		}

		sb.append("TOTAL;;").append(num(s1)).append(';').append(num(s2)).append(';').append(num(s3)).append(';')
				.append(num(s4)).append(';').append(num(s5)).append(';').append(num(s6)).append(';').append(num(s7))
				.append(';').append(num(s8)).append(';').append(num(s9)).append(';').append(num(s10)).append(';')
				.append(num(s11)).append(';')
				.append(pct(NumeroUtil.percentual(rel.getTotalDesperdicio(), rel.getTotalGeral()))).append(';')
				.append(pct(BigDecimal.ONE)).append('\n');

		sb.append("\n== RESUMO DO PER\u00cdODO ==\n");
		sb.append("Total consumo (R$): ").append(num(rel.getTotalConsumo())).append('\n');
		sb.append("Total desperdicio (R$): ").append(num(rel.getTotalDesperdicio())).append('\n');
		sb.append("CMV total (R$): ").append(num(rel.getTotalGeral())).append('\n');
		sb.append("Desperdicio % do CMV: ")
				.append(pct(NumeroUtil.percentual(rel.getTotalDesperdicio(), rel.getTotalGeral()))).append('\n');
		sb.append("Vendas (R$): ").append(num(rel.getVendas())).append('\n');
		sb.append("Percentual de CMV: ").append(pct(NumeroUtil.s(rel.getCmv()))).append('\n');
		sb.append("Meta de CMV: ").append(pct(NumeroUtil.s(rel.getMetaCmv()))).append('\n');
		sb.append("Avaliacao: ").append(escapar(rel.getMensagem())).append('\n');
		sb.append("\nLegenda: Desp%doCMV = desperdicio / CMV do produto; %doCMVTotal = produto / CMV geral do periodo.\n");
		response.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
	}

	// ------------------------------------------------------------ Modelo de importação

	@GetMapping("/modelo-entrada")
	public void modeloEntrada(HttpServletResponse response) throws IOException {
		try (XSSFWorkbook wb = new XSSFWorkbook()) {
			// ----- aba Entradas (cabeçalho na PRIMEIRA linha — obrigatório para importação) -----
			Sheet aba = wb.createSheet("Entradas");
			int[] larguras = { 36, 26, 12, 12, 14, 14 };
			for (int i = 0; i < larguras.length; i++) {
				aba.setColumnWidth(i, larguras[i] * 256);
			}

			CellStyle estiloHeader = wb.createCellStyle();
			estiloHeader.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
			estiloHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			Font fonteHeader = wb.createFont();
			fonteHeader.setBold(true);
			fonteHeader.setColor(IndexedColors.WHITE.getIndex());
			estiloHeader.setFont(fonteHeader);
			estiloHeader.setAlignment(HorizontalAlignment.CENTER);
			estiloHeader.setVerticalAlignment(VerticalAlignment.CENTER);
			estiloHeader.setBorderBottom(BorderStyle.MEDIUM);

			CellStyle estiloTexto = wb.createCellStyle();
			estiloTexto.setWrapText(true);
			estiloTexto.setBorderBottom(BorderStyle.THIN);

			CellStyle estiloNum = wb.createCellStyle();
			estiloNum.setBorderBottom(BorderStyle.THIN);
			estiloNum.setAlignment(HorizontalAlignment.RIGHT);

			CellStyle estiloMoeda = wb.createCellStyle();
			estiloMoeda.setBorderBottom(BorderStyle.THIN);
			estiloMoeda.setAlignment(HorizontalAlignment.RIGHT);
			estiloMoeda.setDataFormat(wb.createDataFormat().getFormat("R$ #,##0.00"));

			CellStyle estiloData = wb.createCellStyle();
			estiloData.setBorderBottom(BorderStyle.THIN);
			estiloData.setAlignment(HorizontalAlignment.CENTER);
			estiloData.setDataFormat(wb.createDataFormat().getFormat("dd/mm/yyyy"));

			String[] colunas = { "Produto", "Categoria", "Unidade", "Quantidade", "Valor", "DataValidade" };
			Row header = aba.createRow(0);
			for (int c = 0; c < colunas.length; c++) {
				Cell cel = header.createCell(c);
				cel.setCellValue(colunas[c]);
				cel.setCellStyle(estiloHeader);
			}

			String[][] itens = {
					{ "ARROZ TIO JOÃO 5KG", "Grãos e Cereais", "UN", "10", "39,90", "12/12/2026" },
					{ "LEITE INTEGRAL 1L", "Laticínios", "UN", "24", "5,90", "" },
					{ "CASTANHA DO PARÁ PST", "Secos e Mercearia", "PCT", "3", "28,00", "" },
					{ "TOMATE PELADO ITALIANO", "Conservas e Enlatados", "CX", "2", "18,50", "30/06/2027" },
					{ "FARINHA DE TRIGO 1KG", "Panificação", "UN", "5", "7,50", "15/01/2027" },
					{ "ÓLEO DE SOJA 900ML", "Óleos e Gorduras", "UN", "4", "8,90", "" } };
			for (int r = 0; r < itens.length; r++) {
				Row row = aba.createRow(1 + r);
				String[] it = itens[r];
				for (int c = 0; c < it.length; c++) {
					Cell cel = row.createCell(c);
					if (c == 3 || c == 4) {
						cel.setCellValue(Double.parseDouble(it[c].replace(',', '.')));
						cel.setCellStyle(c == 4 ? estiloMoeda : estiloNum);
					} else if (c == 5 && !it[c].isEmpty()) {
						cel.setCellValue(java.time.LocalDate.parse(it[c],
								DateTimeFormatter.ofPattern("dd/MM/yyyy")));
						cel.setCellStyle(estiloData);
					} else {
						cel.setCellValue(it[c]);
						cel.setCellStyle(estiloTexto);
					}
				}
			}

			// ----- aba Como usar (título, dicas e explicações) -----
			Sheet como = wb.createSheet("Como usar");
			como.setColumnWidth(0, 4 * 256);
			como.setColumnWidth(1, 110 * 256);

			CellStyle estiloTitulo = wb.createCellStyle();
			estiloTitulo.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
			estiloTitulo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			Font fonteTitulo = wb.createFont();
			fonteTitulo.setBold(true);
			fonteTitulo.setColor(IndexedColors.WHITE.getIndex());
			fonteTitulo.setFontHeightInPoints((short) 13);
			estiloTitulo.setFont(fonteTitulo);
			estiloTitulo.setVerticalAlignment(VerticalAlignment.CENTER);

			CellStyle estiloCorpo = wb.createCellStyle();
			estiloCorpo.setWrapText(true);

			String[] textoComo = {
					"MODELO — IMPORTAR ENTRADAS",
					"Como usar:",
					"1. Preencha as linhas da aba “Entradas”. A primeira linha (cabeçalho) deve ficar como está.",
					"2. Colunas obrigatórias: Produto, Unidade e Quantidade.",
					"3. Categoria e DataValidade são opcionais — pode deixar em branco.",
					"4. Valor (R$): é o total pago pela linha (quantidade × preço unitário). Ex.: 10 × 3,99 = 39,90.",
					"5. Unidades: UN, KG, G, L, ML, CX, PCT, PRCA… (as mesmas usadas no cadastro).",
					"6. Data no formato dd/MM/aaaa (ex.: 12/12/2026).",
					"7. Produtos que ainda não existem no catálogo são criados automaticamente.",
					"8. Depois de salvar, acesse Entrada de itens → “Importar planilha” e envie o arquivo.",
					"9. Também aceita .csv com o mesmo cabeçalho, separado por ponto e vírgula.",
					"",
					"Dica: deixe em branco o que não tiver (categoria, validade, valor). Linhas com quantidade",
					"zerada ou vazia são ignoradas durante a importação." };
			for (int r = 0; r < textoComo.length; r++) {
				Row row = como.createRow(r);
				Cell cel = row.createCell(1);
				cel.setCellValue(textoComo[r]);
				if (r == 0) {
					row.setHeightInPoints(22);
					cel.setCellStyle(estiloTitulo);
				} else {
					cel.setCellStyle(estiloCorpo);
				}
			}

			response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
			response.setHeader("Content-Disposition",
					"attachment; filename=" + java.net.URLEncoder.encode("modelo-entrada-estoq.xlsx", StandardCharsets.UTF_8));
			wb.write(response.getOutputStream());
		}
	}

	// ------------------------------------------------------------ CSV helpers

	private List<Map<String, String>> lerLinhas(MultipartFile arquivo) {
		String nome = arquivo.getOriginalFilename() == null ? "" : arquivo.getOriginalFilename().toLowerCase();
		if (nome.endsWith(".xlsx") || nome.endsWith(".xls")) {
			return lerXlsx(arquivo);
		}
		return lerCsv(arquivo);
	}

	private List<Map<String, String>> lerXlsx(MultipartFile arquivo) {
		List<Map<String, String>> linhas = new ArrayList<>();
		try (Workbook wb = new XSSFWorkbook(arquivo.getInputStream())) {
			Sheet sheet = wb.getSheetAt(0);
			Iterator<Row> rit = sheet.rowIterator();
			if (!rit.hasNext()) {
				throw new BusinessException("Planilha vazia.", HttpStatus.BAD_REQUEST);
			}
			Row header = rit.next();
			int ncol = Math.max(header.getPhysicalNumberOfCells(), header.getLastCellNum());
			String[] colunas = new String[ncol];
			for (int i = 0; i < ncol; i++) {
				colunas[i] = cellText(header.getCell(i)).toLowerCase().trim();
			}
			while (rit.hasNext()) {
				Row r = rit.next();
				Map<String, String> map = new HashMap<>();
				boolean vazia = true;
				for (int i = 0; i < ncol; i++) {
					String v = cellText(r.getCell(i)).trim();
					map.put(colunas[i], v);
					if (!v.isEmpty()) {
						vazia = false;
					}
				}
				if (!vazia) {
					linhas.add(map);
				}
			}
		} catch (IOException ex) {
			throw new BusinessException("Não foi possível ler a planilha: " + ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
		return linhas;
	}

	private String cellText(Cell cell) {
		if (cell == null) {
			return "";
		}
		switch (cell.getCellType()) {
		case NUMERIC:
			if (DateUtil.isCellDateFormatted(cell)) {
				try {
					return cell.getLocalDateTimeCellValue().toLocalDate()
							.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
				} catch (RuntimeException ex) {
					return "";
				}
			}
			BigDecimal num = BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
			return num.toPlainString().replace('.', ',');
		case STRING:
			return cell.getStringCellValue();
		case BOOLEAN:
			return String.valueOf(cell.getBooleanCellValue());
		default:
			return "";
		}
	}

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
						java.time.format.ResolverStyle.SMART).parse(v, LocalDate::from);
			} catch (RuntimeException ignored) {
				// tenta o próximo padrão
			}
		}
		return null;
	}

	private String escapar(String v) {
		return "\"" + (v == null ? "" : v.replace("\"", "\"\"")) + "\"";
	}

	private String num(BigDecimal v) {
		if (v == null) {
			return "";
		}
		return v.stripTrailingZeros().toPlainString();
	}

	private String pct(BigDecimal frac) {
		return num(NumeroUtil.money(NumeroUtil.multiplica(NumeroUtil.s(frac), BigDecimal.valueOf(100)))) + "%";
	}

	private record LinhaEstoque(String produto, String categoria, String unidade, BigDecimal saldo,
			BigDecimal minimo, BigDecimal custo, BigDecimal valor, String situacao) {
	}

	private String agora() {
		return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
	}
}