package com.estoq.business.cupom;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;

class CupomServiceTest {

	// ------------------------------------------------------------------ parser

	@Test
	void parser_identificaItensQuantidadePreco() {
		CupomParser parser = new CupomParser();
		String texto = "SUPERMERCADO BOM PRECO\n"
				+ "CNPJ 12.345\n"
				+ "ARROZ TIO JOAO 5KG      2,000        39,80\n"
				+ "OLEO DE SOJA 900ML      3,000        22,50\n"
				+ "TOTAL R$ 62,30\n";

		CupomLeituraDTO dto = parser.interpretar(texto);

		assertEquals(2, dto.getItens().size());
		ItemCupomLeituraDTO arroz = dto.getItens().get(0);
		assertNotNull(arroz);
		assertTrue(arroz.getDescricao().contains("ARROZ"));
		assertEquals(new BigDecimal("2.000"), arroz.getQuantidade());
		assertEquals(new BigDecimal("39.80"), arroz.getPrecoUnitario());
		assertEquals("SUPERMERCADO BOM PRECO", dto.getEstabelecimento());
		assertFalse(dto.isBaixaConfianca());
	}

	@Test
	void parser_retornaSemItens_quandoTextoNaoTemLinhasValidas() {
		CupomParser parser = new CupomParser();
		CupomLeituraDTO dto = parser.interpretar("TOTAL R$ 0,00\nOBRIGADO PELA PREFERENCIA");
		assertTrue(dto.getItens().isEmpty());
	}

	@Test
	void parser_extraiDataQuandoPresente() {
		CupomParser parser = new CupomParser();
		CupomLeituraDTO dto = parser.interpretar("06/09/2026\nPAO FRANCES  5,000  3,50\n");
		assertEquals("06/09/2026", dto.getData());
	}

	@Test
	void parser_trataTextoNuloEeVazio() {
		CupomParser parser = new CupomParser();
		assertTrue(parser.interpretar(null).getItens().isEmpty());
		assertTrue(parser.interpretar("").getItens().isEmpty());
	}

	// ------------------------------------------------------------------ service

	@Test
	void serviço_usaQrCodeQuandoDetectaNfc() {
		CupomService service = novoService();
		String xml = "<nfeProc><NFe><infNFe><xNome>SUPERMERCADO TESTE</xNome>"
				+ "<dhEmi>2026-09-06T12:00:00-03:00</dhEmi><det><prod><xProd>LEITE INTEGRAL</xProd>"
				+ "<qCom>4.000</qCom><vUnCom>5.90</vUnCom><vProd>23.60</vProd></prod></det></infNFe></NFe></nfeProc>";

		QrCodeReader qr = mock(QrCodeReader.class);
		when(qr.detectarQrCode(any())).thenReturn("https://www.sefaz.com/consulta/123");
		NfceReader nfce = mock(NfceReader.class);
		when(nfce.obterXml(any())).thenReturn(xml);
		ReflectionTestUtils.setField(service, "qrCodeReader", qr);
		ReflectionTestUtils.setField(service, "nfceReader", nfce);

		CupomLeituraDTO dto = service.lerCupom(imagem(10, 10));

		assertFalse(dto.getItens().isEmpty());
		assertEquals("XML_NFC_E", dto.getFonte());
		assertFalse(dto.isBaixaConfianca());
		assertEquals("06/09/2026", dto.getData());
	}

	@Test
	void serviço_caiParaOcr_quandoNaoHaQrCode() {
		CupomService service = novoService();
		QrCodeReader qr = mock(QrCodeReader.class);
		when(qr.detectarQrCode(any())).thenReturn(null);
		OcrService ocr = mock(OcrService.class);
		when(ocr.lerTexto(any())).thenReturn("CADEIRA 2,000 100,00\n");
		ReflectionTestUtils.setField(service, "qrCodeReader", qr);
		ReflectionTestUtils.setField(service, "ocrService", ocr);

		CupomLeituraDTO dto = service.lerCupom(imagem(10, 10));

		assertFalse(dto.getItens().isEmpty());
		assertEquals("OCR", dto.getFonte());
		assertTrue(dto.isBaixaConfianca());
	}

	@Test
	void serviço_caiParaOcr_quandoNfceFalhaODaErro() {
		CupomService service = novoService();
		QrCodeReader qr = mock(QrCodeReader.class);
		when(qr.detectarQrCode(any())).thenReturn("https://www.sefaz.com/consulta/456");
		NfceReader nfce = mock(NfceReader.class);
		// consulta falha (null) — não deve impedir o OCR
		when(nfce.obterXml(any())).thenReturn(null);
		OcrService ocr = mock(OcrService.class);
		when(ocr.lerTexto(any())).thenReturn("CAFE 1,000 25,00\n");
		ReflectionTestUtils.setField(service, "qrCodeReader", qr);
		ReflectionTestUtils.setField(service, "nfceReader", nfce);
		ReflectionTestUtils.setField(service, "ocrService", ocr);

		CupomLeituraDTO dto = service.lerCupom(imagem(10, 10));

		assertFalse(dto.getItens().isEmpty());
		assertEquals("OCR", dto.getFonte());
	}

	@Test
	void parser_ignoraLinhasDeTributosERodape() {
		CupomParser parser = new CupomParser();
		String texto = "Federal * % is - Estadual : % R$\n"
				+ "PIS/COFINS 2,00 0,10\n"
				+ "ARROZ TIO JOAO 5KG      2,000        39,80\n"
				+ "TOTAL R$ 39,80\n";
		CupomLeituraDTO dto = parser.interpretar(texto);

		assertEquals(1, dto.getItens().size());
		assertEquals("ARROZ TIO JOAO 5KG", dto.getItens().get(0).getDescricao());
	}

	private CupomService novoService() {
		CupomService service = new CupomService();
		ReflectionTestUtils.setField(service, "cupomParser", new CupomParser());
		return service;
	}

	private BufferedImage imagem(int w, int h) {
		return new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
	}
}
