package com.estoq.integracao.recibo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Converte o texto bruto extraído do cupom (OCR) em itens de compra.
 *
 * <p>Um cupom de mercado (NFC-e/SAT) tem muitas variações de layout. Este parser é
 * "best-effort": ignora cabeçalho/rodapé/totais/formas de pagamento e tenta extrair,
 * linha a linha, a descrição do produto e um preço (e quando detectável, a quantidade).
 */
@Service
public class ParserReciboService {

	// dd/mm/aaaa ou dd/mm/aa (também aceita ponto como separador comum no OCR)
	private static final Pattern DATA_PATTERN =
			Pattern.compile("\\b(\\d{1,2})[/.](\\d{1,2})[/.](\\d{2,4})\\b");

	// Linhas irrelevantes: totais, pagamento, cabeçalho, rodapé, impostos, códigos
	private static final List<Pattern> TRASH = List.of(
			Pattern.compile("(?i).*(total|subtotal|desconto|troco|recebido).*"),
			Pattern.compile("(?i).*(dinheiro|carta\\s?o|d\\u00e9bito|d\\u00e9bito|cr\\u00e9dito|credito|pix|tef|parcelad).*"),
			Pattern.compile("(?i).*(cpf|cnpj|inscri\\u00e7\\u00e3o|ie:|consumidor|c\\u00f3digo de barras|protocolo).*"),
			Pattern.compile("(?i).*(imposto|icms|iss|nfe|nfce|sat|cupom|via|venda).*"),
			Pattern.compile("(?i).*(cancelad|estorno|estornad|nula|cortesia).*"),
			Pattern.compile("(?i).*(cupom|item|qtd|qtde|quantidade|valor|pre\\u00e7o).*"));

	/** Resultado do parse de um texto de recibo. */
	public static class ParseResult {
		private final LocalDate data;
		private final List<ItemReciboDTO> itens;

		ParseResult(LocalDate data, List<ItemReciboDTO> itens) {
			this.data = data;
			this.itens = itens;
		}

		public LocalDate getData() {
			return data;
		}

		public List<ItemReciboDTO> getItens() {
			return itens;
		}
	}

	public ParseResult parsear(String texto) {
		if (texto == null || texto.isBlank()) {
			return new ParseResult(null, new ArrayList<>());
		}
		LocalDate data = extrairData(texto);

		List<ItemReciboDTO> itens = new ArrayList<>();
		for (String linha : texto.split("\\R")) {
			String l = linha.replaceAll("[|\\-\\_]+", " ").replaceAll("\\s+", " ").trim();
			if (l.isEmpty() || ehLixo(l)) {
				continue;
			}
			ItemReciboDTO item = parsearLinha(l);
			if (item != null) {
				itens.add(item);
			}
		}
		return new ParseResult(data, itens);
	}

	private LocalDate extrairData(String texto) {
		Matcher m = DATA_PATTERN.matcher(texto);
		if (m.find()) {
			int dia = Integer.parseInt(m.group(1));
			int mes = Integer.parseInt(m.group(2));
			int ano = Integer.parseInt(m.group(3));
			if (ano < 100) {
				ano += 2000;
			}
			try {
				return LocalDate.of(ano, mes, dia);
			} catch (Exception e) {
				return null;
			}
		}
		return null;
	}

	private boolean ehLixo(String linha) {
		for (Pattern p : TRASH) {
			if (p.matcher(linha).matches()) {
				return true;
			}
		}
		// linha com apenas números/código (ex.: código de barras, número de itens)
		if (linha.matches("[\\d\\s.,;]+")) {
			return true;
		}
		return false;
	}

	private ItemReciboDTO parsearLinha(String linha) {
		// tenta padrão "qtd X preço" no fim: DESCRICAO 2 X 4,50  (quantidade x unitário)
		ItemReciboDTO item = parsearComMultiplicador(linha);
		if (item != null) {
			return item;
		}
		// senão, trata o último número da linha como preço unitário
		return parsearSomentePreco(linha);
	}

	private ItemReciboDTO parsearComMultiplicador(String linha) {
		Matcher m = Pattern.compile("^(.*?)\\s*(\\d+(?:[.,]\\d+)?)\\s*[xX]\\s*(\\d+(?:[.,]\\d+)?)\\s*$").matcher(linha);
		if (!m.matches()) {
			return null;
		}
		String desc = limparDescricao(m.group(1));
		BigDecimal a = numero(m.group(2));
		BigDecimal b = numero(m.group(3));
		if (desc.isEmpty() || a == null || b == null) {
			return null;
		}
		// convenção comum de mercado: DESCRICAO <qtd> X <preco unitario>
		BigDecimal quantidade = a;
		BigDecimal preco = b;
		// se "a" é um preço pequeno (0<x<10) e b é preço, mantém; senão tenta inverter
		if (a.compareTo(BigDecimal.ONE) < 0) {
			// qtd fracionária (ex.: 0,450 KG X 12,90)
			quantidade = a;
			preco = b;
		}
		ItemReciboDTO item = new ItemReciboDTO();
		item.setDescricao(desc);
		item.setQuantidade(quantidade);
		item.setPrecoUnitario(ajustarPreco(preco));
		return item;
	}

	private ItemReciboDTO parsearSomentePreco(String linha) {
		List<BigDecimal> numeros = extrairNumeros(linha);
		if (numeros.isEmpty()) {
			return null;
		}
		// remove o nome do produto (todo o texto)
		BigDecimal preco = numeros.get(numeros.size() - 1);
		// descrição é o texto sem o(s) número(s)
		String desc = limparDescricao(linha);
		if (desc.isEmpty()) {
			return null;
		}
		ItemReciboDTO item = new ItemReciboDTO();
		item.setDescricao(desc);
		item.setQuantidade(BigDecimal.ONE);
		item.setPrecoUnitario(ajustarPreco(preco));
		return item;
	}

	private List<BigDecimal> extrairNumeros(String linha) {
		List<BigDecimal> nums = new ArrayList<>();
		Matcher m = Pattern.compile("(\\d+(?:[.,]\\d+)?)").matcher(linha);
		while (m.find()) {
			BigDecimal n = numero(m.group(1));
			if (n != null) {
				nums.add(n);
			}
		}
		return nums;
	}

	private String limparDescricao(String linha) {
		String semNumeros = linha.replaceAll("(\\d+(?:[.,]\\d+)?(?:\\s*[xX]\\s*\\d+(?:[.,]\\d+)?)?)", " ")
				.replaceAll("(?i)(kg|g|l|un|cx|pct|ml|x|xi|meia|½|1/2)", " ")
				.replaceAll("\\s+", " ")
				.trim();
		return semNumeros.trim();
	}

	private BigDecimal ajustarPreco(BigDecimal preco) {
		// evita preços absurdos (ex.: OCR trocando vírgula/ponto)
		if (preco == null) {
			return null;
		}
		return preco.signum() <= 0 ? null : preco.setScale(2, java.math.RoundingMode.HALF_UP);
	}

	/**
	 * Interpreta número no formato brasileiro (1.234,56) ou internacional (1234.56).
	 */
	private BigDecimal numero(String txt) {
		if (txt == null || txt.isBlank()) {
			return null;
		}
		String limpo = txt.replaceAll("[^0-9.,\\-]", "");
		if (limpo.isEmpty() || limpo.equals("-")) {
			return null;
		}
		boolean formatoBr = limpo.contains(",");
		if (formatoBr) {
			// no formato brasileiro o ponto é separador de milhar e a vírgula é decimal
			limpo = limpo.replace(".", "").replace(",", ".");
		}
		try {
			return new BigDecimal(limpo);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
