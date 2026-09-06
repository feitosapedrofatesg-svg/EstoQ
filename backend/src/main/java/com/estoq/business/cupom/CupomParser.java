package com.estoq.business.cupom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta o texto bruto retornado pelo OCR e extrai os itens, o
 * estabelecimento e a data da compra. Não é um parser universal — cobre os
 * formatos brasileiros comuns de NFC-e/supermercado.
 */
@Component
public class CupomParser {

	private static final Logger log = LoggerFactory.getLogger(CupomParser.class);

	private static final Pattern DECIMAL_BR = Pattern.compile("(\\d{1,3}(?:\\.\\d{3})*(?:,\\d+)|\\d+,\\d+)");

	private static final Pattern DATA_BR = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})");

	private static final Pattern ESTABELECIMENTO = Pattern.compile("(?im)^\\s*[\\p{Lu}][\\p{Lu}0-9À-Ú][\\p{Lu}0-9À-Ú &.'-]{4,}\\s*$");

	public CupomLeituraDTO interpretar(String texto) {
		CupomLeituraDTO dto = new CupomLeituraDTO();
		List<ItemCupomLeituraDTO> itens = new ArrayList<>();
		if (texto == null || texto.isBlank()) {
			dto.setFonte("OCR");
			dto.setBaixaConfianca(true);
			return dto;
		}

		dto.setData(extrairData(texto));
		dto.setEstabelecimento(extrairEstabelecimento(texto));

		String[] linhas = texto.split("\\r?\\n");
		for (String linha : linhas) {
			String t = linha.trim();
			if (t.isEmpty()) {
				continue;
			}
			ItemCupomLeituraDTO item = parseLinhaItem(t);
			if (item != null) {
				itens.add(item);
			}
		}

		dto.setItens(itens);
		dto.setFonte("OCR");
		return dto;
	}

	private ItemCupomLeituraDTO parseLinhaItem(String linha) {
		// ignora linhas que claramente não são itens (cabeçalho, rodapé, tributos)
		if (linha.toLowerCase().matches("(?i)^(total|subtotal|troco|dinheiro|cart[aã]o|pix|cupom|nota|venda|item|qtd|desc|vltotal|cnpj|ie|ccf|coo|data|hora|obrigado|volte|valor|forma|pagto|extrato|sistema|federal|estadual|icms|pis|cofins|tribut|acr[ée]scimo).*")) {
			return null;
		}

		Matcher mDec = DECIMAL_BR.matcher(linha);
		List<String> decimais = new ArrayList<>();
		while (mDec.find()) {
			decimais.add(mDec.group(1));
		}
		// precisa de ao menos 2 números decimais (qtd + preço)
		if (decimais.size() < 2) {
			return null;
		}

		BigDecimal quantidade;
		BigDecimal precoUnitario;
		if (decimais.size() >= 3) {
			quantidade = paraNumero(decimais.get(0));
			precoUnitario = paraNumero(decimais.get(1));
		} else {
			quantidade = paraNumero(decimais.get(0));
			precoUnitario = paraNumero(decimais.get(1));
		}

		if (quantidade == null || quantidade.signum() <= 0 || precoUnitario == null || precoUnitario.signum() <= 0) {
			return null;
		}

		String nome = limparNome(linha);
		if (nome == null || nome.length() < 2) {
			return null;
		}

		BigDecimal precoTotal = precoUnitario.multiply(quantidade);
		return new ItemCupomLeituraDTO(nome, quantidade, precoUnitario, precoTotal);
	}

	private String limparNome(String linha) {
		// remove números decimais (qtd, preço, total) e códigos longos
		String nome = linha.replaceAll("(\\d{1,3}(?:\\.\\d{3})*(?:,\\d+)|\\d+,\\d+)", " ")
				.replaceAll("\\b\\d{6,}\\b", " ")
				.replaceAll("^[A-Za-z]{1,5}\\s*\\d{1,6}\\s+", " ")
				.replaceAll("^[-|•*\\d.\\s]+", "")
				.replaceAll("\\s+[-|•*]+\\s*$", "")
				.replaceAll("\\s{2,}", " ")
				.trim();
		if (nome.matches("^[0-9,.\s]+$")) {
			return null;
		}
		return nome;
	}

	private BigDecimal paraNumero(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		try {
			String limpo = s.replace(".", "").replace(",", ".");
			BigDecimal v = new BigDecimal(limpo);
			return v;
		} catch (NumberFormatException ex) {
			return null;
		}
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
}
