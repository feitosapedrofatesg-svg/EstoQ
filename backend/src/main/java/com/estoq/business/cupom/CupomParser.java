package com.estoq.business.cupom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
