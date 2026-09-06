package com.estoq.business.cupom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta o texto bruto retornado pelo OCR e extrai os itens, o
 * estabelecimento e a data da compra.
 *
 * <p>Uma linha do OCR <strong>não é aceita como produto apenas por conter
 * texto + número</strong>. Cada linha passa por uma classificação com
 * pontuação de confiança: precisa ter estrutura de item de cupom (descrição +
 * quantidade + preço), não pode parecer informação fiscal/total/rodapé e não
 * pode ser lixo de OCR (poucos caracteres úteis, excesso de símbolos, palavras
 * absurdamente curtas). Linhas com confiança abaixo do limiar são descartadas,
 * nunca viram produto "inventado".
 */
@Component
public class CupomParser {

	private static final Logger log = LoggerFactory.getLogger(CupomParser.class);

	/** Decimais no formato brasileiro: 39,80 / 1.234,56 / 2,000 (qtd até 3 casas). */
	private static final Pattern DECIMAL_BR = Pattern.compile("(\\d{1,3}(?:\\.\\d{3})*(?:,\\d{1,3})|\\d{1,2},\\d{1,3})");

	/** Código EAN comum (8 ou 13 dígitos, tipicamente iniciado por 789 no Brasil). */
	private static final Pattern EAN = Pattern.compile("(?:^|[\\s(])(?<ean>(?:789|8\\d)\\d{11}|(?:789|8\\d)\\d{6})");

	private static final Pattern DATA_BR = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})");

	private static final Pattern ESTABELECIMENTO = Pattern.compile(
			"(?im)^\\s*[\\p{Lu}][\\p{Lu}0-9À-Ú][\\p{Lu}0-9À-Ú &.'-]{4,}\\s*$");

	/** Limiar a partir do qual a linha é aceita automaticamente como item. */
	private static final double LIMIAR_ACEITAR = 0.70;

	/** Abaixo disso a linha é descartada (lixo/ambiguidade muito alta). */
	private static final double LIMIAR_DESCARTAR = 0.42;

	private static final String LETRAS_VALIDAS = "abcdefghijklmnopqrstuvwxyzàáâãçéêíóôõúü";

	// ---------------------------------------------------------------- palavras fiscais

	/**
	 * Termos/padrões de linha que NÃO representam um item de produto. São checados
	 * em qualquer ponto da linha (tokenizado), resilientes a ruído de OCR.
	 */
	private static final List<String> PALAVRAS_FISCAIS = new ArrayList<>(Arrays.asList(
			// totais e pagamento
			"total", "subtotal", "troco", "dinheiro", "cartao", "credito", "debito", "pix",
			"pagamento", "pago", "desconto", "acrescimo", "acréscimo",
			// tributos / impostos
			"icms", "pis", "cofins", "federal", "estadual", "municipal", "tribut", "iss",
			// identificação e cabeçalho
			"cnpj", "cpf", "ie", "inscricao", "ccf", "coo", "serie", "nfe", "nfce", "cupom",
			"nota", "venda", "chave", "acesso", "protocolo", "qrcode", "qr", "sefaz",
			"data", "hora", "endereco", "telefone", "validade", "obrigado", "volte",
			// cabeçalho de coluna
			"qtd", "desc", "vltotal", "vlr", "item", "un", "und"));

	/**
	 * Datas e horas dentro de uma linha (formato BR e ISO) — nunca são produto.
	 */
	private static final Pattern PADRAO_DATA = Pattern.compile(
			"(?i)\\d{2}/\\d{2}/\\d{2,4}|\\d{4}-\\d{2}-\\d{2}|\\d{1,2}:\\d{2}(:\\d{2})?(\\s?(am|pm))?");

	/**
	 * Chave de acesso NFC-e de 44 dígitos.
	 */
	private static final Pattern PADRAO_CHAVE = Pattern.compile("\\d{44}");

	/** Perc. idêntico a uma linha apenas de cabeçalho de coluna. */
	private static final Pattern PADRAO_QTD_X = Pattern.compile("(?i)\\b(qtd|qtde|quant|item|desc|un|und)\\b");

	// ------------------------------------------------------------------ interpretar

	public CupomLeituraDTO interpretar(String texto) {
		CupomLeituraDTO dto = new CupomLeituraDTO();
		List<ItemCupomLeituraDTO> itens = new ArrayList<>();
		if (texto == null || texto.isBlank()) {
			dto.setFonte("OCR");
			dto.setBaixaConfianca(true);
			return dto;
		}

		log.debug("OCR bruto:\n{}", texto);

		dto.setData(extrairData(texto));
		dto.setEstabelecimento(extrairEstabelecimento(texto));

		boolean encerrouAreaDeItens = false;
		String[] linhas = texto.split("\\r?\\n");
		for (String linha : linhas) {
			String t = linha.trim();
			if (t.isEmpty()) {
				continue;
			}
			// ao encontrar claramente totais/informações fiscais, para de tratar
			// o restante como itens (seção de produtos já terminou)
			if (marcaFimDaSecaoDeItens(t)) {
				encerrouAreaDeItens = true;
				log.debug("Encerrando área de itens ao ver linha: {}", t);
				continue;
			}
			if (encerrouAreaDeItens) {
				continue;
			}

			Candidato cand = classificar(t);
			if (cand == null) {
				log.debug("Linha descartada: \"{}\"", t);
				continue;
			}
			ItemCupomLeituraDTO item = cand.paraItem();
			itens.add(item);
			log.debug("Item aceito ({:.2f}): \"{}\" q={} p={}",
					cand.confianca, item.getDescricao(), item.getQuantidade(), item.getPrecoUnitario());
		}

		dto.setItens(itens);
		dto.setFonte("OCR");
		return dto;
	}

	// ------------------------------------------------------------------ interpretar espacial

	/**
	 * Posições relativas das colunas da tabela do cupom (fração da largura da
	 * imagem usada no OCR). Detectadas a partir de um cupom real:
	 * CÓDIGO ≈ 0,04·L | DESCRIÇÃO ≈ 0,2–0,44·L | QTDE ≈ 0,48·L | UN ≈ 0,55·L |
	 * VL.UNIT ≈ 0,64·L | VL.TOTAL ≈ 0,75·L. Linhas fiscais "coladas" à esquerda da
	 * coluna de quantidade (≈0,3·L) são ignoradas por não caírem nas colunas de
	 * valores.
	 */
	private static final double COL_CODIGO_FIM = 0.20;
	private static final double COL_DESCRICAO_INI = 0.14;
	private static final double COL_DESCRICAO_FIM = 0.56;
	private static final double COL_QTDE_INI = 0.44;
	private static final double COL_QTDE_FIM = 0.55;
	private static final double COL_UNIDADE_INI = 0.50;
	private static final double COL_UNIDADE_FIM = 0.66;
	private static final double COL_VLUNIT_INI = 0.62;
	private static final double COL_VLUNIT_FIM = 0.72;
	private static final double COL_VLTOTAL_INI = 0.72;
	private static final double COL_VLTOTAL_FIM = 0.92;

	/** Número de coluna do cupom: inteiros + decimais (ex.: "3,99", "0,536", "21.07"). */
	private static final Pattern DECIMAL_TABELA = Pattern.compile("\\d{1,3}[.,]\\d{1,3}");

	/** Unidades de medida típicas da coluna UN do cupom (tolerando OCR: "KG"→"Ky"). */
	private static final List<String> UNIDADES = List.of("KG", "LT", "ML", "G", "CX", "PCT", "DUZ", "DZ", "MD", "MT");

	/**
	 * Reconstrói os itens do cupom a partir das coordenadas das palavras no OCR
	 * (TSV do tesseract). Cada linha é lida por coluna — código, descrição,
	 * quantidade, unidade e preços — usando a posição relativa na imagem, e não o
	 * texto corrido. Linhas fiscais e fragmentos que ficam fora das colunas de
	 * valores são naturalmente ignorados.
	 */
	public CupomLeituraDTO interpretarTabela(OcrService.LeituraEspacial leitura) {
		CupomLeituraDTO dto = new CupomLeituraDTO();
		List<ItemCupomLeituraDTO> itens = new ArrayList<>();
		int largura = leitura != null ? leitura.largura() : 0;
		if (leitura == null || largura <= 0 || leitura.linhas().isEmpty()) {
			dto.setFonte("OCR");
			dto.setBaixaConfianca(true);
			return dto;
		}

		String textoBruto = textoCompleto(leitura);
		dto.setData(extrairData(textoBruto));
		dto.setEstabelecimento(extrairEstabelecimento(textoBruto));

		LinhaPendente pendente = null;
		// Encerradores fracos ("consumidor", "pagamento", etc.) só valem DEPOIS que
		// a tabela começou: no topo do cupom aparecem "DOCUMENTO AUXILIAR DE
		// CONSUMIDOR ELETRÔNICO" e cabeçalhos que não podem encerrar a área.
		boolean tabelaIniciada = false;
		for (OcrService.LinhaOcr linha : leitura.linhas()) {
			String textoLinha = textoDa(linha);
			if (marcaFimDaSecaoDeItens(textoLinha)) {
				log.debug("Tabela: encerrando área de itens ao ver linha \"{}\"", textoLinha);
				break;
			}
			if (ehCabecalhoTabela(textoLinha)) {
				log.debug("Tabela: cabeçalho ignorado \"{}\"", textoLinha);
				tabelaIniciada = true;
				pendente = null;
				continue;
			}

			ColunasTabela c = colunasDaLinha(linha, largura);
			if (tabelaIniciada && (ehEncerramentoDeSecao(textoLinha) || ehLinhaDeTotal(textoLinha, c))) {
				log.debug("Tabela: encerrando área de itens ao ver linha \"{}\"", textoLinha);
				break;
			}
			boolean temPreco = c.temPrecos();
			boolean temDesc = !c.palavrasDescricao.isEmpty();
			if (temDesc && temPreco) {
				tabelaIniciada = true;
				pendente = null;
				ItemCupomLeituraDTO item = montarItem(c);
				if (item != null) {
					itens.add(item);
					log.debug("Tabela: item \"{}\" q={} p={} conf={:.2f}",
							item.getDescricao(), item.getQuantidade(), item.getPrecoUnitario(), item.getConfianca());
				}
			} else if (temDesc) {
				// linha apenas com descrição: vira o item pendente para receber os
				// números da próxima linha (tesseract separou descrição e valores).
				// Se havia outra descrição pendente, ela é abandonada (produtos são
				// linhas independentes; nunca cruzamos de um produto para outro).
				pendente = new LinhaPendente(c, linha);
			} else if (temPreco) {
				// números sem descrição: se houver uma descrição pendente logo
				// acima, são os números do item; senão é linha fiscal/rodapé
				if (pendente != null && !pendente.numeros && proximas(pendente, linha)) {
					pendente.receberNumeros(c);
					ItemCupomLeituraDTO item = montarItem(pendente.comoColunas());
					if (item != null) {
						itens.add(item);
						log.debug("Tabela: item \"{}\" q={} p={} conf={:.2f}",
								item.getDescricao(), item.getQuantidade(), item.getPrecoUnitario(), item.getConfianca());
					}
					pendente = null;
				}
				// sem descrição pendente → fragmento fiscal / produto ilegível;
				// nunca vira produto sozinho (pode ser "0,26 10,79 10,00")
			}
		}

		dto.setItens(itens);
		dto.setFonte("OCR");
		return dto;
	}

	private String textoCompleto(OcrService.LeituraEspacial leitura) {
		StringBuilder sb = new StringBuilder();
		for (OcrService.LinhaOcr l : leitura.linhas()) {
			sb.append(textoDa(l)).append('\n');
		}
		return sb.toString();
	}

	private String textoDa(OcrService.LinhaOcr linha) {
		StringBuilder sb = new StringBuilder();
		for (OcrService.PalavraOcr p : linha.palavras()) {
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(p.texto());
		}
		return sb.toString();
	}

	/**
	 * Marca linhas do rodapé fiscal que encerram definitivamente o miolo de itens.
	 * Complementa {@link #marcaFimDaSecaoDeItens} com termos típicos do cupom real.
	 */
	private boolean ehEncerramentoDeSecao(String texto) {
		String minus = texto == null ? "" : texto.toLowerCase(Locale.ROOT);
		return minus.contains("de itens")
				|| minus.contains("valor total")
				|| minus.contains("valor a pagar")
				|| minus.contains("valor pago")
				|| minus.contains("forma de pagamento")
				|| minus.contains("pagamento")
				|| minus.contains("tributos")
				|| minus.contains("federal")
				|| minus.contains("estadual")
				|| minus.contains("municipal")
				|| minus.contains("consumidor")
				|| minus.contains("consulte")
				|| minus.contains("qrcode")
				|| minus.contains("chave de acesso")
				|| minus.contains("obrigado")
				|| minus.contains("volte")
				|| PADRAO_CHAVE.matcher(minus).find();
	}

	private boolean ehLinhaDeTotal(String texto, ColunasTabela c) {
		if (texto == null || texto.isBlank() || c == null) {
			return false;
		}
		// linhas de produto têm quantidade/unidade — só total fiscal aparece só com
		// o dígito final ("VALOR TOTAL R$ 69,48", lido como "Valor tora: R$ ...")
		if (c.quantidade != null || c.unidade != null || !c.temPrecos()) {
			return false;
		}
		String semIniciais = texto.toLowerCase(Locale.ROOT).replaceFirst("^[^a-zà-ú0-9]+", "");
		return semIniciais.startsWith("valor") || semIniciais.startsWith("total")
				|| semIniciais.startsWith("subtotal") || semIniciais.startsWith("troco");
	}

	/**
	 * Cabeçalho de coluna da tabela ("CÓDIGO DESCRIÇÃO QTDE UN VL.UNIT VL.TOTAL"),
	 * resiliente a OCR ("OTE IN UE.UNZT OL TOTA"). Pelo menos dois tokens de
	 * cabeçalho para evitar descartar produto com palavra parecida no texto.
	 */
	private boolean ehCabecalhoTabela(String texto) {
		if (texto == null || texto.isBlank()) {
			return false;
		}
		int achados = 0;
		String[] tokens = texto.toLowerCase(Locale.ROOT).split("[^a-zà-ú0-9]+");
		for (String tok : tokens) {
			if (tok.length() < 2) {
				continue;
			}
			if ("qtd".equals(tok) || "qtde".equals(tok) || "quantidade".equals(tok) || "quant".equals(tok)
					|| "descricao".equals(tok) || "desc".equals(tok) || "un".equals(tok) || "und".equals(tok)
					|| "codigo".equals(tok) || "unit".equals(tok) || tok.startsWith("total") || tok.startsWith("tot")
					|| tok.startsWith("vltotal") || tok.startsWith("vl")) {
				achados++;
			}
		}
		return achados >= 2;
	}

	private boolean proximas(LinhaPendente pendente, OcrService.LinhaOcr linha) {
		int base = Math.max(pendente.altura, 12);
		return linha.topo() - pendente.baixo <= base * 2;
	}

	/**
	 * Lê uma linha do OCR por colunas (posição relativa à largura da imagem).
	 * Números fora das colunas de valores (fragments fiscais à esquerda) são
	 * simplesmente ignorados.
	 */
	private ColunasTabela colunasDaLinha(OcrService.LinhaOcr linha, int largura) {
		ColunasTabela c = new ColunasTabela();
		boolean[] consumidas = new boolean[linha.palavras().size()];
		List<OcrService.PalavraOcr> palavras = linha.palavras();
		for (int i = 0; i < palavras.size(); i++) {
			OcrService.PalavraOcr p = palavras.get(i);
			String tok = p.texto().trim();
			if (tok.isEmpty()) {
				continue;
			}
			double centro = centroDe(p, largura);
			if (DECIMAL_TABELA.matcher(tok).matches()) {
				BigDecimal v = numeroTabela(tok);
				consumidas[i] = true;
				if (v == null || v.signum() <= 0) {
					continue;
				}
				if (centro >= COL_VLTOTAL_INI && centro <= COL_VLTOTAL_FIM) {
					c.precoTotal = v;
				} else if (centro >= COL_VLUNIT_INI && centro < COL_VLUNIT_FIM) {
					c.precoUnitario = v;
				} else if (centro >= COL_QTDE_INI && centro < COL_QTDE_FIM) {
					c.quantidade = v;
				}
				continue;
			}
			if (centro >= COL_UNIDADE_INI && centro <= COL_UNIDADE_FIM && aceitarUnidade(tok)) {
				if (c.unidade == null) {
					c.unidade = normalizarUnidade(tok);
					consumidas[i] = true;
				}
				continue;
			}
			if (centro <= COL_CODIGO_FIM && tok.matches("\\d{6,14}")) {
				if (c.codigo == null) {
					c.codigo = tok;
					consumidas[i] = true;
				}
				continue;
			}
		}

		// descrição: palavras com letras na faixa da coluna de descrição
		for (int i = 0; i < palavras.size(); i++) {
			if (consumidas[i]) {
				continue;
			}
			OcrService.PalavraOcr p = palavras.get(i);
			String tok = p.texto().trim();
			if (tok.isEmpty()) {
				continue;
			}
			double centro = centroDe(p, largura);
			if (centro < COL_DESCRICAO_INI || centro > COL_DESCRICAO_FIM) {
				continue;
			}
			if (!palavraDescricaoValida(tok)) {
				continue;
			}
			c.palavrasDescricao.add(tok);
		}
		c.descricao = String.join(" ", c.palavrasDescricao).replaceAll("\\s+", " ").trim();
		if (!c.descricao.isEmpty()) {
			c.descricao = c.descricao.toUpperCase(Locale.ROOT);
		}
		return c;
	}

	private double centroDe(OcrService.PalavraOcr p, int largura) {
		return (p.left() + p.width() / 2.0) / largura;
	}

	/**
	 * Uma palavra da coluna de descrição precisa se parecer com NOME de produto,
	 * não com fragmento fiscal/lixo de OCR: ao menos 2 letras, maioria de letras,
	 * sem símbolos estruturais (parênteses, vírgulas, dois-pontos), e palavras
	 * curtas (≤3 letras) precisam ser em caixa alta — fragmentos como "(00)",
	 * "ot)", "aM", "ts", "0s:" não passam.
	 */
	private boolean palavraDescricaoValida(String tok) {
		long letras = tok.chars().filter(Character::isLetter).count();
		long digitos = tok.chars().filter(Character::isDigit).count();
		if (letras < 2 || (double) letras / (letras + digitos) < 0.5) {
			return false;
		}
		String s = tok.endsWith(".") ? tok.substring(0, tok.length() - 1) : tok;
		if (!s.replaceAll("[A-Za-zÀ-Úà-ú0-9']", "").isEmpty()) {
			return false;
		}
		String soLetras = tok.replaceAll("[^\\p{L}]", "");
		if (soLetras.length() <= 3 && soLetras.chars().anyMatch(Character::isLowerCase)) {
			return false;
		}
		return true;
	}

	private BigDecimal numeroTabela(String tok) {
		try {
			if (tok.contains(",") && !tok.contains(".")) {
				return new BigDecimal(tok.replace(',', '.'));
			}
			if (tok.contains(".") && !tok.contains(",") && tok.length() - tok.indexOf('.') - 1 <= 3) {
				return new BigDecimal(tok);
			}
		} catch (NumberFormatException ignored) {
			// segue
		}
		return null;
	}

	private boolean aceitarUnidade(String tok) {
		String norm = normalizarUnidade(tok);
		if (norm.isEmpty() || norm.length() > 4) {
			return false;
		}
		if ("UN".equals(norm) || "ON".equals(norm) || "UNI".equals(norm) || "UM".equals(norm)) {
			return true;
		}
		for (String u : UNIDADES) {
			if (correspondenciaFiscal(norm.toLowerCase(Locale.ROOT), u.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return norm.contains("UN") || norm.contains("KG");
	}

	private String normalizarUnidade(String tok) {
		return tok.replaceAll("[^\\p{L}]", "").toUpperCase(Locale.ROOT);
	}

	/**
	 * Monta o item a partir das colunas lidas. Quantidade padrão 1 quando a coluna
	 * não foi reconhecida (comum com "1,000" lido como ruído); preço sempre existe.
	 */
	private ItemCupomLeituraDTO montarItem(ColunasTabela c) {
		if (c.descricao == null || c.descricao.length() < 2) {
			return null;
		}
		if (c.precoUnitario == null && c.precoTotal == null) {
			return null;
		}
		BigDecimal qtd = c.quantidade;
		if (qtd == null || qtd.signum() <= 0 || qtd.compareTo(new BigDecimal("99999")) > 0) {
			qtd = BigDecimal.ONE;
		}
		BigDecimal vlUnit = c.precoUnitario;
		BigDecimal vlTotal = c.precoTotal;
		if (vlUnit != null && vlTotal == null) {
			vlTotal = vlUnit.multiply(qtd);
		}
		if (vlUnit == null && vlTotal != null) {
			vlUnit = vlTotal.divide(qtd, 4, RoundingMode.HALF_UP);
		}
		double conf = calcularConfiancaTabela(c, qtd, vlUnit, vlTotal);
		if (conf < LIMIAR_DESCARTAR) {
			log.debug("Tabela: item descartado (conf {:.2f}): \"{}\"", conf, c.descricao);
			return null;
		}
		ItemCupomLeituraDTO item = new ItemCupomLeituraDTO(c.descricao, qtd, vlUnit, vlTotal);
		item.setCodigo(c.codigo);
		item.setConfianca(conf);
		return item;
	}

	private double calcularConfiancaTabela(ColunasTabela c, BigDecimal qtd, BigDecimal vlUnit, BigDecimal vlTotal) {
		double score = 0.45;
		if (c.codigo != null) {
			score += 0.10;
		}
		if (c.descricao.chars().filter(Character::isLetter).count() >= 5) {
			score += 0.20;
		}
		if (c.unidade != null) {
			score += 0.10;
		}
		if (c.quantidade != null && c.quantidade.signum() > 0) {
			score += 0.10;
		}
		if (vlUnit != null && vlTotal != null) {
			score += matematicaOk(qtd, vlUnit, vlTotal) ? 0.10 : -0.20;
		} else if (vlUnit != null || vlTotal != null) {
			score += 0.05;
		}
		score *= avaliarQualidadeTexto(c.descricao);
		if (contemPalavraFiscal(c.descricao.toLowerCase(Locale.ROOT))) {
			score -= 0.5;
		}
		return Math.max(0.0, Math.min(1.0, score));
	}

	/** Valida qtd × vl.unit ≈ vl.total (tolerância de arredondamento do cupom). */
	private boolean matematicaOk(BigDecimal qtd, BigDecimal vlUnit, BigDecimal vlTotal) {
		if (qtd == null || vlUnit == null || vlTotal == null || qtd.signum() <= 0) {
			return false;
		}
		BigDecimal esperado = vlUnit.multiply(qtd);
		BigDecimal diferenca = esperado.subtract(vlTotal).abs();
		BigDecimal base = vlTotal.abs().max(BigDecimal.ONE);
		try {
			return diferenca.divide(base, 4, RoundingMode.HALF_UP).compareTo(new BigDecimal("0.06")) <= 0;
		} catch (ArithmeticException ex) {
			return false;
		}
	}

	// ------------------------------------------------------------------ classes espaciais

	/** Colunas lidas de uma linha da tabela do OCR espacial. */
	private static final class ColunasTabela {
		String codigo;
		String unidade;
		BigDecimal quantidade;
		BigDecimal precoUnitario;
		BigDecimal precoTotal;
		final List<String> palavrasDescricao = new ArrayList<>();
		String descricao;

		boolean temPrecos() {
			return precoUnitario != null || precoTotal != null || (quantidade != null && quantidade.signum() > 0);
		}
	}

	/** Descrição aguardando os números da linha seguinte (item partido / contínuo). */
	private static final class LinhaPendente {
		final int topo;
		final int baixo;
		final int altura;
		final ColunasTabela colunas = new ColunasTabela();
		boolean numeros;

		LinhaPendente(ColunasTabela c, OcrService.LinhaOcr linha) {
			this.topo = linha.topo();
			this.baixo = linha.palavras().stream()
					.mapToInt(p -> p.top() + p.height())
					.max().orElse(linha.topo() + 20);
			this.altura = linha.palavras().stream()
					.mapToInt(OcrService.PalavraOcr::height)
					.max().orElse(16);
			if (c.codigo != null) {
				colunas.codigo = c.codigo;
			}
			colunas.palavrasDescricao.addAll(c.palavrasDescricao);
			colunas.descricao = c.descricao;
		}

		void anexar(ColunasTabela c) {
			if (colunas.codigo == null) {
				colunas.codigo = c.codigo;
			}
			for (String p : c.palavrasDescricao) {
				if (!colunas.palavrasDescricao.contains(p)) {
					colunas.palavrasDescricao.add(p);
				}
			}
			colunas.descricao = String.join(" ", colunas.palavrasDescricao).replaceAll("\\s+", " ").trim()
					.toUpperCase(Locale.ROOT);
		}

		void receberNumeros(ColunasTabela c) {
			if (c.quantidade != null) {
				colunas.quantidade = c.quantidade;
			}
			if (c.precoUnitario != null) {
				colunas.precoUnitario = c.precoUnitario;
			}
			if (c.precoTotal != null) {
				colunas.precoTotal = c.precoTotal;
			}
			if (c.unidade != null) {
				colunas.unidade = c.unidade;
			}
			numeros = true;
		}

		ColunasTabela comoColunas() {
			return colunas;
		}
	}

	// -------------------------------------------------------------- classificação

	/**
	 * Classifica uma linha e devolve um candidato a item (descrição normalizada +
	 * quantidade + preço + confiança) ou {@code null} se não parecer produto.
	 */
	Candidato classificar(String linha) {
		if (linha == null || linha.isBlank()) {
			return null;
		}
		String t = linha.trim();
		String ean = extrairEan(t);

		// 1) descarta claramente não-produto (fiscal/header/rodapé/mensagem/data)
		if (naoEhProduto(t)) {
			log.debug("Linha descartada (não-produto): \"{}\"", t);
			return null;
		}

		// 2) qualidade do texto: rejeita lixo de OCR antes de qualquer número.
		// Se houver EAN na linha, avalia o texto sem ele (os dígitos do código não
		// devem penalizar a legibilidade do nome do produto).
		double qualidadeTexto = avaliarQualidadeTexto(t);
		if (ean != null) {
			double semEanQual = avaliarQualidadeTexto(t.replace(ean, " "));
			if (semEanQual > qualidadeTexto) {
				qualidadeTexto = semEanQual;
			}
		}

		// 3) multiplicador de penalização por lixo
		double confiancaTexto = 1.0;
		if (qualidadeTexto < 1.0) {
			confiancaTexto = Math.max(0.0, qualidadeTexto);
			if (confiancaTexto <= 0.25) {
				log.debug("Linha descartada (lixo de OCR): \"{}\"", t);
				return null;
			}
		}

		// 4) extrai os números que importam (qtd/preço) — precisa de preço real
		List<BigDecimal> numeros = extrairNumeros(t);
		if (numeros.isEmpty()) {
			return null;
		}

		String semEan = ean == null ? t : t.replace(ean, " ");

		// 5) escolhe quantidade e preço unitário a partir dos números
		PrecoQuantidade pq = interpretarNumeros(numeros, semEan);
		if (pq == null) {
			log.debug("Linha descartada (valores inválidos): \"{}\"", t);
			return null;
		}

		String descricao = limparDescricao(semEan, ean, pq);
		if (descricao == null || descricao.length() < 2) {
			log.debug("Linha descartada (sem descrição útil): \"{}\"", t);
			return null;
		}

		// 6) pontuação de confiança
		double score = calcularConfianca(descricao, pq, qualidadeTexto, t);

		if (score < LIMIAR_DESCARTAR) {
			log.debug("Linha descartada (confiança {:.2f}): \"{}\"", score, t);
			return null;
		}

		BigDecimal precoTotal = pq.precoUnitario.multiply(pq.quantidade);
		return new Candidato(descricao, pq.quantidade, pq.precoUnitario, precoTotal, score);
	}

	// -------------------------------------------------------------- decisão não-produto

	/**
	 * Decide se uma linha claramente NÃO é um item de produto (fiscal, total,
	 * pagamento, cabeçalho, rodapé, mensagem, chave de acesso, etc). Resiliência a
	 * ruído de OCR: usa palavras-token, checando em qualquer parte da linha.
	 */
	private boolean naoEhProduto(String t) {
		String minus = t.toLowerCase(Locale.ROOT);
		if (PADRAO_DATA.matcher(minus).find()) {
			return true;
		}
		if (PADRAO_CHAVE.matcher(minus).find()) {
			return true;
		}
		// linha só com símbolos/números/percentual sem letras de produto
		if (!contemLetrasValidas(minus)) {
			return true;
		}
		// percentual isolado (impostos): "10%" ou "Federal ... %"
		if (containsPercentual(t)) {
			// mas não se houver uma descrição de produto clara + preço real grande
			if (descricaoEhProdutoClara(minus)) {
				return false;
			}
			return true;
		}

		// tokeniza e procura palavras fiscais/cabeçalho
		String[] tokens = minus.split("[^a-zà-ú0-9]+");
		int achados = 0;
		for (String tok : tokens) {
			if (tok.length() < 3) {
				continue;
			}
			for (String p : PALAVRAS_FISCAIS) {
				if (correspondenciaFiscal(tok, p)) {
					achados++;
					break;
				}
			}
		}
		// linha de cabeçalho/total/pagamento normalmente é curta e dominada por
		// esses tokens, ou não tem nenhuma palavra de produto real além deles
		if (achados >= 1) {
			long palavrasReais = 0;
			for (String tok : tokens) {
				if (tok.length() < 3) {
					continue;
				}
				boolean fiscal = false;
				for (String p : PALAVRAS_FISCAIS) {
					if (correspondenciaFiscal(tok, p)) {
						fiscal = true;
						break;
					}
				}
				if (!fiscal) {
					palavrasReais++;
				}
			}
			if (palavrasReais == 0 || tokens.length <= 3) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Corresponde a palavra lida ao token fiscal, tolerando pequeno ruído de OCR:
	 * prefixo exato, ou coincidência de quase todo o token (ex.: "federa" → "federal").
	 */
	private boolean correspondenciaFiscal(String tok, String fiscal) {
		if (fiscal.startsWith(tok) || tok.startsWith(fiscal)) {
			return true;
		}
		int min = Math.min(tok.length(), fiscal.length());
		if (min < 3) {
			return false;
		}
		int pref = 0;
		while (pref < min && tok.charAt(pref) == fiscal.charAt(pref)) {
			pref++;
		}
		return pref >= Math.min(min, fiscal.length() - 1);
	}

	private boolean containsPercentual(String t) {
		Matcher m = Pattern.compile("\\d+(?:[.,]\\d+)?\\s*%").matcher(t);
		return m.find();
	}

	/**
	 * Heurística: uma linha que começa com palavra de produto real (maioria
	 * alfabética, sem símbolos dominantes) provavelmente é item, mesmo com um "%".
	 */
	private boolean descricaoEhProdutoClara(String minus) {
		String semDecimais = minus.replaceAll(DECIMAL_BR.pattern(), " ").trim();
		String semSimbolos = semDecimais.replaceAll("[^a-zà-ú0-9 ]", " ").trim();
		long letras = semSimbolos.chars().filter(Character::isLetter).count();
		long total = semSimbolos.chars().filter(c -> !Character.isWhitespace(c)).count();
		return total > 0 && (double) letras / total >= 0.6 && letras >= 4;
	}

	private boolean contemLetrasValidas(String minus) {
		long letras = minus.chars().filter(Character::isLetter).count();
		return letras >= 3;
	}

	// -------------------------------------------------------------- qualidade do texto

	/**
	 * Avalia a qualidade do texto da linha para detectar lixo de OCR. Retorna 1.0
	 * para texto limpo e valores menores conforme aumentam símbolos, caracteres
	 * fora do esperado e excesso de números sem texto. Palavras só numéricas
	 * (preços/quantidades) e unidades ("5KG", "900ML") não são consideradas lixo.
	 */
	double avaliarQualidadeTexto(String t) {
		int comp = t.length();
		if (comp == 0) {
			return 0.0;
		}
		long letras = 0;
		long digitos = 0;
		long simbolos = 0;
		for (char c : t.toCharArray()) {
			if (Character.isLetter(c)) {
				letras++;
			} else if (Character.isDigit(c)) {
				digitos++;
			} else if (!Character.isWhitespace(c) && !",.()/-%".contains(String.valueOf(c))) {
				simbolos++;
			}
		}
		if (letras == 0) {
			return 0.0;
		}

		double score = 1.0;

		// excesso de símbolos (lixo tipo "; : ( /( ,")
		double razaoSimbolos = (double) simbolos / comp;
		if (razaoSimbolos > 0.12) {
			score -= (razaoSimbolos - 0.12) * 2.5;
		}

		// paralheira: vários números sem texto significativo ao redor
		double razaodigitosLetras = digitos > 0 ? (double) letras / (letras + digitos) : 1.0;
		if ((double) digitos / comp > 0.45 && razaodigitosLetras < 0.35) {
			score -= 0.35;
		}

		// palavras ALFABÉTICAS muito curtas em excesso (ignora só-números/unidades)
		String[] palavras = t.split("[^\\p{L}0-9]+");
		long alfasComLetras = 0;
		long alfasCurtas = 0;
		for (String p : palavras) {
			long letrasPalavra = p.chars().filter(Character::isLetter).count();
			if (letrasPalavra == 0) {
				continue; // só números não importa
			}
			alfasComLetras++;
			if (letrasPalavra <= 1) {
				alfasCurtas++;
			}
		}
		if (alfasComLetras > 0) {
			double razaoCurtas = (double) alfasCurtas / alfasComLetras;
			if (razaoCurtas > 0.4) {
				score -= 0.30;
			}
		}

		// fragmentos estruturais típicos de lixo de OCR: colchetes, parênteses,
		// ponto-e-vírgula, igual e decimal quebrado por espaço ("5510, 00")
		long lixoEstrutural = 0;
		for (char c : t.toCharArray()) {
			if ("()[]{}=|^".indexOf(c) >= 0) {
				lixoEstrutural++;
			}
		}
		if (Pattern.compile(",\\s+\\d").matcher(t).find()) {
			lixoEstrutural += 2;
		}
		if (lixoEstrutural > 0) {
			score -= Math.min(0.6, 0.20 + lixoEstrutural * 0.20);
		}

		// letras fora do alfabeto esperado (OCR ruim / símbolos misturados)
		long validas = 0;
		for (char c : t.toLowerCase(Locale.ROOT).toCharArray()) {
			if (LETRAS_VALIDAS.indexOf(c) >= 0) {
				validas++;
			}
		}
		if (letras > 0 && (double) validas / letras < 0.6) {
			score -= 0.25;
		}

		return Math.max(0.0, Math.min(1.0, score));
	}

	// -------------------------------------------------------------- números / preço

	private List<BigDecimal> extrairNumeros(String t) {
		List<BigDecimal> numeros = new ArrayList<>();
		// ignora o EAN (código de barras) na contagem de números de preço
		String semEan = t.replaceAll(EAN.pattern(), " ");
		Matcher m = DECIMAL_BR.matcher(semEan);
		while (m.find()) {
			String g = m.group(1);
			try {
				BigDecimal v = paraNumero(g);
				if (v != null) {
					numeros.add(v);
				}
			} catch (NumberFormatException ignored) {
				// segue
			}
		}
		return numeros;
	}

	private String extrairEan(String t) {
		Matcher m = EAN.matcher(t);
		if (m.find()) {
			return m.group("ean");
		}
		return null;
	}

	/**
	 * Interpreta os números como (quantidade, preço unitário). Sempre há ao menos
	 * um decimal (o preço). Se houver mais de um, o primeiro é a quantidade (padrão
	 * NFC-e). Se houver só um decimal, procura um inteiro solto no início (formato
	 * com EAN: "789... ARROZ 5KG 2 29,90") e usa como quantidade.
	 */
	private PrecoQuantidade interpretarNumeros(List<BigDecimal> decimais, String linhaSemEan) {
		if (decimais.isEmpty()) {
			return null;
		}
		BigDecimal quantidade;
		BigDecimal precoUnitario;
		if (decimais.size() >= 2) {
			quantidade = decimais.get(0);
			precoUnitario = decimais.get(1);
		} else {
			// um único decimal: preço. Quantidade vem de um inteiro solto se houver.
			BigDecimal pv = decimais.get(0);
			BigDecimal qtd = inteiroQuantidade(linhaSemEan);
			quantidade = (qtd == null) ? BigDecimal.ONE : qtd;
			precoUnitario = pv;
		}
		if (quantidade == null || quantidade.signum() <= 0 || quantidade.compareTo(new BigDecimal("99999")) > 0) {
			return null;
		}
		if (precoUnitario == null || precoUnitario.signum() <= 0) {
			return null;
		}
		return new PrecoQuantidade(quantidade, precoUnitario);
	}

	/**
	 * Procura um inteiro solto (1..4 dígitos) que represente quantidade no formato
	 * "EAN ARROZ 5KG 2 29,90". Evita códigos longos e números dentro de unidades
	 * ("5KG", "900ML", "2025").
	 */
	private BigDecimal inteiroQuantidade(String linha) {
		Matcher m = Pattern.compile("(?<![\\d\\p{L}.])(\\d{1,4})(?![\\d\\p{L}.,])").matcher(linha);
		while (m.find()) {
			String g = m.group(1);
			if (g.length() >= 4) {
				continue; // provavelmente código, não quantidade
			}
			try {
				BigDecimal v = paraNumero(g);
				if (v != null && v.signum() > 0) {
					return v;
				}
			} catch (NumberFormatException ignored) {
				// segue
			}
		}
		return null;
	}

	// -------------------------------------------------------------- descrição

	private String limparDescricao(String linha, String ean, PrecoQuantidade pq) {
		String d = linha;
		if (ean != null && !ean.isEmpty()) {
			d = d.replace(ean, " ");
		}
		// remove decimais (qtd/preço/total)
		d = d.replaceAll(DECIMAL_BR.pattern(), " ");
		// remove códigos longos
		d = d.replaceAll("\\b\\d{6,}\\b", " ");
		// remove a quantidade inteira solta quando foi usada (formato "EAN x 2 29,90")
		d = removeQuantidadeInteira(pq, d);
		// remove códigos de item curtos no início (ex.: "123 ARROZ 5KG")
		d = d.replaceAll("^\\s*\\d{1,5}\\s+", " ");
		// remove % (impostos)
		d = d.replaceAll("%", " ");
		// remove símbolos de ruído
		d = d.replaceAll("[:;=()*/|•\\\\<>#@!?^&·]+", " ");
		// remove quantidade com unidade "3 UN x 29,90" — fica só "OLEO SOJA 900ML"
		d = d.replaceAll("(?i)\\b[0-9]+\\s*(un|und|unid)\\b", " ").replaceAll("\\s+", " ").trim();
		// remove unidade órfã no fim ("900ML UN" → "900ML")
		d = d.replaceAll("(?i)\\s+(un|und|unid)\\s*$", "").trim();
		d = d.replaceAll("\\s+", " ").trim();
		// remove artefatos residuais: só símbolos ou só dígitos
		if (d.isEmpty()) {
			return null;
		}
		// a descrição precisa ter ao menos uma letra (desconsidera caixa)
		if (!d.matches("(?s).*[a-zA-ZÀ-Úà-ú].*")) {
			return null;
		}
		// normaliza capitalização (mantém nomes em maiúsculas como no cupom)
		return d.toUpperCase(Locale.ROOT);
	}

	/**
	 * Remove da descrição uma quantidade inteira solta ("ARROZ 5KG 2 29,90" → o "2").
	 * Nunca remove um dígito dentro de palavra (ex.: "5KG").
	 */
	private String removeQuantidadeInteira(PrecoQuantidade pq, String d) {
		if (pq == null || pq.quantidade == null || pq.quantidade.stripTrailingZeros().scale() > 0) {
			return d;
		}
		String qtd = pq.quantidade.stripTrailingZeros().toPlainString();
		if (!qtd.matches("\\d{1,4}")) {
			return d;
		}
		return d.replaceAll("(?<![A-Za-z0-9])" + qtd + "(?![A-Za-z0-9,])", " ").replaceAll("\\s+", " ").trim();
	}

	// -------------------------------------------------------------- confiança

	private double calcularConfianca(String descricao, PrecoQuantidade pq, double qualidadeTexto, String linha) {
		double score = 0.5;
		// + bons sinais de produto
		if (descricao.chars().filter(Character::isLetter).count() >= 5) {
			score += 0.15;
		}
		if (primeiraPalavraParecidaProduto(descricao)) {
			score += 0.10;
		}
		if (pq.quantidade.signum() >= 0 && pq.precoUnitario.signum() > 0) {
			score += 0.15;
		}
		// - qualidade do texto (lixo de OCR)
		score *= qualidadeTexto;
		// - linha com percentual forte indica imposto
		if (containsPercentual(linha) && !descricaoEhProdutoClara(linha.toLowerCase(Locale.ROOT))) {
			score -= 0.35;
		}
		// - palavras fiscais presentes no restante
		if (contemPalavraFiscal(descricao.toLowerCase(Locale.ROOT))) {
			score -= 0.3;
		}
		return Math.max(0.0, Math.min(1.0, score));
	}

	private boolean primeiraPalavraParecidaProduto(String desc) {
		String[] p = desc.split("\\s+");
		if (p.length == 0) {
			return false;
		}
		String prim = p[0].toLowerCase(Locale.ROOT);
		return prim.length() >= 3 && !PALAVRAS_FISCAIS.contains(prim);
	}

	private boolean contemPalavraFiscal(String minus) {
		String[] tokens = minus.split("[^a-zà-ú0-9]+");
		for (String tok : tokens) {
			for (String f : PALAVRAS_FISCAIS) {
				if (tok.startsWith(f)) {
					return true;
				}
			}
		}
		return false;
	}

	// -------------------------------------------------------------- seções

	/**
	 * Detecta o início de total/pagamento/tributos/rodapé que encerra a região de
	 * itens do cupom.
	 */
	private boolean marcaFimDaSecaoDeItens(String t) {
		// Linhas inequívocas de fechamento: totais, formas de pagamento, aviso final.
		// (Tributos como PIS/ICMS são descartados individualmente por classificar;
		// não encerram a área porque podem aparecer em qualquer ordem no OCR.)
		String minus = t.toLowerCase(Locale.ROOT);
		return minus.startsWith("total")
				|| minus.startsWith("subtotal")
				|| minus.startsWith("forma de pag")
				|| minus.startsWith("dinheiro")
				|| minus.startsWith("pagamento")
				|| minus.startsWith("troco")
				|| minus.startsWith("desconto")
				|| minus.startsWith("informa")
				|| minus.startsWith("obrigado")
				|| minus.startsWith("volte")
				|| minus.contains("chave de acesso");
	}

	// -------------------------------------------------------------- auxiliares

	private BigDecimal paraNumero(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		String limpo = s.replace(".", "").replace(",", ".");
		return new BigDecimal(limpo);
	}

	private String extrairData(String texto) {
		Matcher m = DATA_BR.matcher(texto);
		if (m.find()) {
			return m.group(1);
		}
		return null;
	}

	private String extrairEstabelecimento(String texto) {
		for (String linha : texto.split("\\r?\\n")) {
			String t = linha.trim();
			if (t.length() < 5 || t.length() > 80) {
				continue;
			}
			if (ESTABELECIMENTO.matcher(t).matches() && !t.toUpperCase().startsWith("CNPJ")
					&& !t.equalsIgnoreCase("SUPERMERCADO")) {
				return t;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------- classes

	private static final class PrecoQuantidade {
		final BigDecimal quantidade;
		final BigDecimal precoUnitario;

		PrecoQuantidade(BigDecimal quantidade, BigDecimal precoUnitario) {
			this.quantidade = quantidade;
			this.precoUnitario = precoUnitario;
		}
	}

	static final class Candidato {
		final String descricao;
		final BigDecimal quantidade;
		final BigDecimal precoUnitario;
		final BigDecimal precoTotal;
		final double confianca;

		Candidato(String descricao, BigDecimal quantidade, BigDecimal precoUnitario,
				BigDecimal precoTotal, double confianca) {
			this.descricao = descricao;
			this.quantidade = quantidade;
			this.precoUnitario = precoUnitario;
			this.precoTotal = precoTotal;
			this.confianca = confianca;
		}

		ItemCupomLeituraDTO paraItem() {
			ItemCupomLeituraDTO item = new ItemCupomLeituraDTO(descricao, quantidade, precoUnitario, precoTotal);
			item.setConfianca(confianca);
			return item;
		}
	}
}
