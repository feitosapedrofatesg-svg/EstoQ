package com.estoq.business.cupom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orquestra a leitura de um cupom fiscal a partir da imagem:
 * 1) tenta QR Code da NFC-e;
 * 2) se encontrar URL e conseguir XML, extrai itens estruturados;
 * 3) se não, faz OCR do texto e interpreta pelo {@link CupomParser}.
 *
 * Retorna apenas a prévia ({@link CupomLeituraDTO}). Nada é gravado aqui.
 */
@Service
public class CupomService {

	private static final Logger log = LoggerFactory.getLogger(CupomService.class);

	private static final Pattern DET = Pattern.compile("<det[^>]*>([\\s\\S]*?)<\\/det>", Pattern.CASE_INSENSITIVE);

	@Autowired
	private QrCodeReader qrCodeReader;

	@Autowired
	private NfceReader nfceReader;

	@Autowired
	private OcrService ocrService;

	@Autowired
	private CupomParser cupomParser;

	public CupomLeituraDTO lerCupom(BufferedImage imagem) {
		// 1) QR Code
		String urlQr = qrCodeReader.detectarQrCode(imagem);
		if (urlQr != null && !urlQr.isBlank()) {
			log.info("QR Code detectado.");
			// 2) NFC-e (XML estruturado)
			CupomLeituraDTO daNfce = lerDaFonte(urlQr);
			if (daNfce != null && !daNfce.getItens().isEmpty()) {
				return daNfce;
			}
			log.info("QR Code presente mas sem XML aproveitável; segue para OCR.");
		}

		// 3) OCR + parser
		return lerPorOcr(imagem);
	}

	private CupomLeituraDTO lerDaFonte(String urlQr) {
		String xml = nfceReader.obterXml(urlQr);
		if (xml == null) {
			return null;
		}
		CupomLeituraDTO dto = new CupomLeituraDTO();
		dto.setFonte("XML_NFC_E");
		dto.setBaixaConfianca(false);

		Matcher detMatcher = DET.matcher(xml);
		int count = 0;
		while (detMatcher.find()) {
			String bloco = detMatcher.group(1);
			ItemCupomLeituraDTO item = parseDetXml(bloco);
			if (item != null) {
				dto.addItem(item);
				count++;
			}
		}
		dto.setData(extrairCampoXml(xml, "dEmi", "dhEmi"));
		if (dto.getData() != null) {
			// dhEmi costuma vir "2026-09-06T12:00:00-03:00"; normaliza para d/m/a
			dto.setData(normalizarData(xml));
		}
		dto.setEstabelecimento(extrairCampoXml(xml, "xNome"));
		if (count == 0) {
			return null;
		}
		return dto;
	}

	private ItemCupomLeituraDTO parseDetXml(String bloco) {
		String nome = extrairCampoXml(bloco, "xProd");
		if (nome == null) {
			return null;
		}
		BigDecimal quantidade = decimalNf(extrairCampoXml(bloco, "qCom", "qTrib"));
		BigDecimal precoUnitario = decimalNf(extrairCampoXml(bloco, "vUnCom", "vUnTrib"));
		BigDecimal precoTotal = decimalNf(extrairCampoXml(bloco, "vProd"));
		if (quantidade == null || quantidade.signum() <= 0) {
			quantidade = BigDecimal.ONE;
		}
		if (precoTotal == null && precoUnitario != null) {
			precoTotal = precoUnitario.multiply(quantidade);
		}
		if (precoUnitario == null && precoTotal != null && quantidade.signum() > 0) {
			precoUnitario = precoTotal.divide(quantidade, 2, java.math.RoundingMode.HALF_UP);
		}
		return new ItemCupomLeituraDTO(nome.trim(), quantidade, precoUnitario, precoTotal);
	}

	private CupomLeituraDTO lerPorOcr(BufferedImage imagem) {
		String texto = ocrService.lerTexto(imagem);
		CupomLeituraDTO dto = cupomParser.interpretar(texto);
		dto.setBaixaConfianca(true);
		if (dto.getItens().isEmpty()) {
			log.info("OCR não identificou itens no cupom.");
		}
		return dto;
	}

	private String extrairCampoXml(String xml, String... tags) {
		for (String tag : tags) {
			Matcher m = Pattern.compile("<" + tag + ">([\\s\\S]*?)<\\/" + tag + ">", Pattern.CASE_INSENSITIVE)
					.matcher(xml);
			if (m.find()) {
				String v = m.group(1).trim();
				if (!v.isEmpty()) {
					return v;
				}
			}
		}
		return null;
	}

	private BigDecimal decimalNf(String v) {
		if (v == null || v.isBlank()) {
			return null;
		}
		try {
			return new BigDecimal(v.replace(",", ".").trim());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private String normalizarData(String xml) {
		String dh = extrairCampoXml(xml, "dhEmi");
		if (dh != null) {
			Matcher m = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})").matcher(dh);
			if (m.find()) {
				return String.format(Locale.ROOT, "%s/%s/%s", m.group(3), m.group(2), m.group(1));
			}
		}
		String d = extrairCampoXml(xml, "dEmi");
		if (d != null) {
			Matcher m = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})").matcher(d);
			if (m.find()) {
				return String.format(Locale.ROOT, "%s/%s/%s", m.group(3), m.group(2), m.group(1));
			}
		}
		return null;
	}
}
