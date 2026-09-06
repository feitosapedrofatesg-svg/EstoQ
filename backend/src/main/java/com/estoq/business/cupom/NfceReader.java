package com.estoq.business.cupom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consulta a URL da NFC-e fornecida pelo QR Code e tenta obter os dados
 * estruturados (XML). Integração fiscal isolada aqui: se a consulta falhar ou
 * o estado exigir scraping, retornamos {@code null} e o fluxo segue para OCR —
 * uma falha fiscal NUNCA impede a leitura por OCR.
 */
@Component
public class NfceReader {

	private static final Logger log = LoggerFactory.getLogger(NfceReader.class);

	private static final Pattern DET = Pattern.compile("<det[^>]*>([\\s\\S]*?)<\\/det>", Pattern.CASE_INSENSITIVE);

	private static final Pattern TAG = Pattern.compile("<([a-zA-Z0-9_]+)>([\\s\\S]*?)<\\/\\1>");

	private static final int TIMEOUT_MS = 5000;

	/**
	 * Tenta obter o XML da NFC-e a partir da URL do QR Code.
	 *
	 * @return o XML (com <nfeProc>/<det>) ou {@code null} se não for possível.
	 */
	public String obterXml(String urlQrCode) {
		if (urlQrCode == null || urlQrCode.isBlank()) {
			return null;
		}
		String conteudo = baixar(urlQrCode);
		if (conteudo == null || conteudo.isBlank()) {
			log.info("Consulta NFC-e retornou vazio: não é possível extrair XML.");
			return null;
		}
		// a resposta pode conter o XML completo ou apenas a URL de download do XML
		// (padrão de alguns estados). Se não vier com itens, tenta a URL de download.
		if (DET.matcher(conteudo).find()) {
			return conteudo;
		}
		String linkXml = extrairLinkXml(conteudo);
		if (linkXml != null) {
			String xmlDownload = baixar(linkXml);
			if (xmlDownload != null && DET.matcher(xmlDownload).find()) {
				return xmlDownload;
			}
		}
		log.info("NFC-e sem XML estruturado acessível; prosseguindo para OCR.");
		return null;
	}

	private String baixar(String urlStr) {
		try {
			// new URL aceita '|' no query (presente na URL do QR Code NFC-e);
			// URI.create rejeita e lançaria IllegalArgumentException.
			URL url = new URL(urlStr);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(TIMEOUT_MS);
			conn.setReadTimeout(TIMEOUT_MS);
			conn.setInstanceFollowRedirects(true);
			conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; EstoQ)");
			int code = conn.getResponseCode();
			if (code != HttpURLConnection.HTTP_OK) {
				log.info("NFC-e HTTP {} em {}", code, urlStr);
				return null;
			}
			String contentType = conn.getContentType();
			try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				byte[] buf = new byte[4096];
				int n;
				while ((n = in.read(buf)) != -1) {
					out.write(buf, 0, n);
				}
				byte[] bytes = out.toByteArray();
				// tenta decodificar como texto (XML/HTML)
				return new String(bytes, StandardCharsets.UTF_8);
			}
		} catch (IOException | RuntimeException ex) {
			log.info("Falha ao consultar NFC-e {}: {}", urlStr, ex.getMessage());
			return null;
		}
	}

	private String extrairLinkXml(String html) {
		// padrões comuns: href="...XML..." ou referência a/arquivo.xml
		Matcher m = Pattern.compile("(?i)([/a-zA-Z0-9._?=&%\\-]*\\.xml[^\"']*)").matcher(html);
		if (m.find()) {
			String rel = m.group(1);
			if (rel.startsWith("http")) {
				return rel;
			}
			// resolve relativo contra o host (básico)
			return rel;
		}
		return null;
	}
}
