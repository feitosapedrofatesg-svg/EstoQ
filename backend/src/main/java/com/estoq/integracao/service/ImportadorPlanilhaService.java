package com.estoq.integracao.service;

import com.estoq.business.compra.CompraModel;
import com.estoq.business.compra.ICompraRepository;
import com.estoq.business.estoque.EstoquePeriodoModel;
import com.estoq.business.estoque.IEstoquePeriodoRepository;
import com.estoq.business.periodo.IPeriodoRepository;
import com.estoq.business.periodo.PeriodoModel;
import com.estoq.business.periodo.PeriodoStatus;
import com.estoq.business.produto.IProdutoRepository;
import com.estoq.business.produto.ProdutoModel;
import com.estoq.business.produto.ProdutoService;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.integracao.dto.ResultadoImportacao;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Importa a planilha legada "Planilha de cálculo de CMV Real" (.xls/.xlsx) para dentro do estoQ.
 * Estrutura das abas CMV SEMANA 01..04:
 *   B: produto | C: unidade | D: estoque inicial (qtde) | E: R$ unid inicial
 *   G: compras (qtde) | H: R$ unid compra | J: estoque final (qtde) | K: R$ unid final
 */
@Service
public class ImportadorPlanilhaService {

	private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);
	private static final Pattern DATA_PATTERN =
			Pattern.compile("(\\d{1,2})\\s*/\\s*(\\d{1,2})\\s*/\\s*(\\d{4}).*?(\\d{1,2})\\s*/\\s*(\\d{1,2})\\s*/\\s*(\\d{4})");

	private static final DataFormatter FORMATTER = new DataFormatter();

	@Autowired
	private IProdutoRepository produtoRepository;

	@Autowired
	private IPeriodoRepository periodoRepository;

	@Autowired
	private ICompraRepository compraRepository;

	@Autowired
	private IEstoquePeriodoRepository estoqueRepository;

	@Autowired
	private ProdutoService produtoService;

	@Transactional
	public ResultadoImportacao importar(MultipartFile arquivo) {
		if (arquivo == null || arquivo.isEmpty()) {
			throw new BusinessException("Nenhum arquivo enviado.", HttpStatus.BAD_REQUEST);
		}
		ResultadoImportacao resultado = new ResultadoImportacao();

		try (InputStream in = arquivo.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
			for (int i = 0; i < wb.getNumberOfSheets(); i++) {
				Sheet sheet = wb.getSheetAt(i);
				String nomeAba = sheet.getSheetName();
				if (!nomeAba.startsWith("CMV")) {
					continue;
				}
				importarAbaCMV(sheet, resultado);
			}
		} catch (Exception e) {
			throw new BusinessException(
					"Falha ao ler a planilha. Envie o arquivo no formato .xls ou .xlsx.", e);
		}
		resultado.setMensagem("Importação concluída com sucesso.");
		return resultado;
	}

	private void importarAbaCMV(Sheet sheet, ResultadoImportacao resultado) {
		String dataLinha = valorTexto(sheet, 3, 2);
		List<LocalDate> datas = extrairDatas(dataLinha);
		if (datas.size() < 2) {
			return;
		}
		PeriodoModel periodo = busyPeriodo(datas.get(0), datas.get(1));
		resultado.setPeriodos(resultado.getPeriodos() + 1);

		ProdutoModel reconhecido;
		for (int r = 6; r <= sheet.getLastRowNum(); r++) {
			String nome = valorTexto(sheet, r, 1).trim();
			if (nome.isEmpty()) {
				continue;
			}

			String unidade = valorTexto(sheet, r, 2).trim();
			BigDecimal qtdInicial = numero(sheet, r, 3);
			BigDecimal precoInicial = numero(sheet, r, 4);
			BigDecimal qtdCompra = numero(sheet, r, 6);
			BigDecimal precoCompra = numero(sheet, r, 7);
			BigDecimal qtdFinal = numero(sheet, r, 9);
			BigDecimal precoFinal = numero(sheet, r, 10);

			reconhecido = obterProduto(nome, unidade, resultado);
			PeriodoModel periodoAtual = periodo;

			// Compras
			if (qtdCompra != null && qtdCompra.signum() > 0 && precoCompra != null) {
				salvarCompra(periodoAtual, reconhecido, qtdCompra, precoCompra);
				resultado.setCompras(resultado.getCompras() + 1);
			}

			// Estoque inicial/final
			boolean temInicial = qtdInicial != null && qtdInicial.signum() > 0;
			boolean temFinal = qtdFinal != null && qtdFinal.signum() > 0;
			if (temInicial || temFinal) {
				salvarEstoque(periodoAtual, reconhecido,
						temInicial ? qtdInicial : BigDecimal.ZERO, precoInicial,
						temFinal ? qtdFinal : BigDecimal.ZERO, precoFinal);
				resultado.setEstoques(resultado.getEstoques() + 1);
			}
		}
	}

	private PeriodoModel busyPeriodo(LocalDate inicio, LocalDate fim) {
		PeriodoModel existente = periodoRepository
				.findAllByAtivoTrueOrderByDataInicioAsc().stream()
				.filter(p -> p.getDataInicio().equals(inicio))
				.findFirst().orElse(null);
		if (existente != null) {
			return existente;
		}
		PeriodoModel periodo = new PeriodoModel();
		periodo.setNome("Semana " + inicio.format(BR) + " a " + fim.format(BR));
		periodo.setDataInicio(inicio);
		periodo.setDataFim(fim);
		periodo.setStatus(PeriodoStatus.ABERTO);
		return periodoRepository.save(periodo);
	}

	private ProdutoModel obterProduto(String nome, String unidade, ResultadoImportacao resultado) {
		String nomeLimpo = normalizar(nome);
		return produtoRepository.findByNomeIgnoreCase(nomeLimpo).orElseGet(() -> {
			ProdutoModel p = new ProdutoModel();
			p.setNome(nomeLimpo);
			p.setUnidade(unidade.isEmpty() ? "und" : unidade);
			p.setCategoria("Importado");
			p.setEstoqueMinimo(BigDecimal.valueOf(7));
			p.setAtivo(true);
			ProdutoModel salvo = produtoService.insert(p);
			resultado.setProdutosCriados(resultado.getProdutosCriados() + 1);
			return salvo;
		});
	}

	private void salvarCompra(PeriodoModel periodo, ProdutoModel produto,
			BigDecimal qtd, BigDecimal preco) {
		CompraModel compra = new CompraModel();
		compra.setPeriodo(periodo);
		compra.setProduto(produto);
		compra.setQuantidade(qtd);
		compra.setPrecoUnitario(preco.setScale(2, java.math.RoundingMode.HALF_UP));
		compra.setDataCompra(periodo.getDataInicio());
		compraRepository.save(compra);
	}

	private void salvarEstoque(PeriodoModel periodo, ProdutoModel produto,
			BigDecimal qtdInicial, BigDecimal precoInicial,
			BigDecimal qtdFinal, BigDecimal precoFinal) {
		estoqueRepository.findByPeriodoIdAndProdutoId(periodo.getId(), produto.getId())
				.ifPresentOrElse(ep -> {
					if (ep.getQuantidadeInicial() == null || ep.getQuantidadeInicial().signum() == 0) {
						ep.setQuantidadeInicial(qtdInicial);
					}
					if (ep.getQuantidadeFinal() == null || ep.getQuantidadeFinal().signum() == 0) {
						ep.setQuantidadeFinal(qtdFinal);
						if (precoFinal != null) {
							ep.setValorUnitarioFinal(precoFinal.setScale(2, java.math.RoundingMode.HALF_UP));
						}
					}
					estoqueRepository.save(ep);
				}, () -> {
					EstoquePeriodoModel ep = new EstoquePeriodoModel();
					ep.setPeriodo(periodo);
					ep.setProduto(produto);
					ep.setQuantidadeInicial(qtdInicial);
					ep.setQuantidadeFinal(qtdFinal);
					if (precoInicial != null) {
						ep.setValorUnitarioInicial(precoInicial.setScale(2, java.math.RoundingMode.HALF_UP));
					}
					if (precoFinal != null) {
						ep.setValorUnitarioFinal(precoFinal.setScale(2, java.math.RoundingMode.HALF_UP));
					}
					estoqueRepository.save(ep);
				});
	}

	private List<LocalDate> extrairDatas(String texto) {
		List<LocalDate> datas = new ArrayList<>();
		if (texto == null) {
			return datas;
		}
		Matcher m = DATA_PATTERN.matcher(texto);
		if (m.find()) {
			datas.add(LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1))));
			datas.add(LocalDate.of(Integer.parseInt(m.group(6)), Integer.parseInt(m.group(5)), Integer.parseInt(m.group(4))));
		}
		return datas;
	}

	private String normalizar(String nome) {
		return nome.replaceAll("\\s+", " ").trim();
	}

	private String valorTexto(Sheet sheet, int row, int col) {
		Row r = sheet.getRow(row);
		if (r == null) {
			return "";
		}
		Cell c = r.getCell(col);
		if (c == null) {
			return "";
		}
		return FORMATTER.formatCellValue(c).trim();
	}

	private BigDecimal numero(Sheet sheet, int row, int col) {
		String txt = valorTexto(sheet, row, col);
		if (txt.isEmpty()) {
			return null;
		}
		try {
			boolean formatoBr = txt.contains("R$") || txt.contains(",");
			String limpo = txt.replaceAll("[^0-9.,\\-]", "");
			if (formatoBr) {
				// no formato brasileiro o ponto é separador de milhar e a vírgula é decimal
				limpo = limpo.replace(".", "").replace(",", ".");
			}
			if (limpo.isEmpty() || limpo.equals("-")) {
				return null;
			}
			return new BigDecimal(limpo);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}