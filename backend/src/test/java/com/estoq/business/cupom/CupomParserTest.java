package com.estoq.business.cupom;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CupomParserTest {

	// --------------------------------------------------------------- aceitáveis

	@Test
	void aceita_quantidadeComUnidade() {
		CupomParser parser = new CupomParser();
		CupomLeituraDTO dto = parser.interpretar("ARROZ 5KG 2 UN 29,90\nOLEO SOJA 900ML 3 UND 7,50\n");

		assertEquals(2, dto.getItens().size());
		ItemCupomLeituraDTO arroz = dto.getItens().get(0);
		assertEquals("ARROZ 5KG", arroz.getDescricao());
		assertEquals(new BigDecimal("2"), arroz.getQuantidade());
		assertEquals(new BigDecimal("29.90"), arroz.getPrecoUnitario());
		assertNotNull(arroz.getConfianca());
	}

	@Test
	void aceita_eanComQuantidadeInteira() {
		CupomParser parser = new CupomParser();
		CupomLeituraDTO dto = parser.interpretar("7891234567890 ARROZ 5KG 2 29,90\n");

		assertEquals(1, dto.getItens().size());
		ItemCupomLeituraDTO item = dto.getItens().get(0);
		assertEquals("ARROZ 5KG", item.getDescricao());
		assertEquals(new BigDecimal("2"), item.getQuantidade());
		assertEquals(new BigDecimal("29.90"), item.getPrecoUnitario());
	}

	@Test
	void aceita_linhaNfcePadrao() {
		CupomParser parser = new CupomParser();
		CupomLeituraDTO dto = parser.interpretar("CADEIRA 2,000 100,00\n");

		assertEquals(1, dto.getItens().size());
		assertEquals("CADEIRA", dto.getItens().get(0).getDescricao());
	}

	@Test
	void aceita_descricaoMaiusculaEFaixaaAteZ() {
		CupomParser parser = new CupomParser();
		CupomLeituraDTO dto = parser.interpretar("QUEIJO MINAS 2,000 45,90\nCOXA DE FRANGO 1,500 12,34\n");

		assertEquals(2, dto.getItens().size());
		assertEquals("QUEIJO MINAS", dto.getItens().get(0).getDescricao());
		assertEquals("COXA DE FRANGO", dto.getItens().get(1).getDescricao());
	}

	// --------------------------------------------------------------- rejeitáveis

	@Test
	void descarta_lixoDeOcr_SemLetras() {
		CupomParser parser = new CupomParser();
		assertNull(parser.classificar("; : ( /( ,      0,08        1,221"));
		assertNull(parser.classificar(") ] ,00) K ; NT   0,88   7,531"));
	}

	@Test
	void descarta_lixoDeOcr_ComLetrasQuebradas() {
		CupomParser parser = new CupomParser();
		assertNull(parser.classificar("Co AOBBIE 5510, 00) UN    1    14,99"));
		assertNull(parser.classificar("= Federa) : % Hs - Estadual : %     10,69     20,66"));
	}

	@Test
	void descarta_TotaisEPagamentos() {
		CupomParser parser = new CupomParser();
		assertNull(parser.classificar("TOTAL R$ 62,30"));
		assertNull(parser.classificar("FORMA DE PAGAMENTO"));
		assertNull(parser.classificar("DINHEIRO 62,30"));
		assertNull(parser.classificar("TROCO 0,00"));
	}

	@Test
	void descarta_ImpostosEIdentificacao() {
		CupomParser parser = new CupomParser();
		assertNull(parser.classificar("ICMS"));
		assertNull(parser.classificar("PIS/COFINS 2,00 0,10"));
		assertNull(parser.classificar("CNPJ 12.345.678/0001-90"));
		assertNull(parser.classificar("PROTOCOLO 12345"));
	}

	@Test
	void descarta_linhaQueEhApenasSecaoDeItens() {
		CupomParser parser = new CupomParser();
		assertNull(parser.classificar("QTD DESC"));
		assertNull(parser.classificar("ITEM VALOR"));
	}

	@Test
	void confianca_ComportaFaixasEsperadas() {
		CupomParser parser = new CupomParser();
		CupomParser.Candidato bom = parser.classificar("ARROZ TIO JOAO 5KG 2,000 39,80");
		CupomParser.Candidato fraco = parser.classificar("; : ( /( , 0,08 1,221");
		assertNotNull(bom);
		assertTrue(bom.confianca >= 0.70);
		assertNull(fraco);
	}

	@Test
	void interpretar_marcaBaixaConfianca_quandoItemTemConfiancaBaixa() {
		CupomParser parser = new CupomParser();
		CupomLeituraDTO dto = parser.interpretar("PAO FRANCES 5,000 3,50\n");
		assertFalse(dto.isBaixaConfianca());
		assertTrue(dto.getItens().get(0).getConfianca() > 0.60);
	}
}