package com.estoq.business.cupom;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testa o OCR espacial (tabela do cupom) com coordenadas de um cupom real.
 * Fixture com o TSV do tesseract é o fallback sancionado (o binário/PNG não é
 * comitado; as coordenadas são fixas). O caso real tem 7 produtos; a garantia
 * central é NEVER virar produto lixo fiscal/rodapé.
 */
class CupomTableParserTest {

	private static final String RES = "src/test/resources/cupom/";

	// ------------------------------------------------------------- fixture real

	/** O TSV foi gerado com o mesmo pré-processamento+parâmetros do runtime. */
	@Test
	void cupomReal_grayContraste2x_extraiProdutosSemLixo() throws Exception {
		CupomLeituraDTO dto = parseFixture("cupom_2x_gray_ac.tsv");

		assertItensValidos(dto);
		assertTrue(dto.getItens().size() >= 3,
				"esperava >= 3 itens reais, veio " + dto.getItens().size());
		assertContemProduto(dto, "ENERG");
		assertContemProduto(dto, "CEBILA"); // CEBOLA AMARELA (caixa com degradê)
	}

	@Test
	void cupomReal_plano2x_extraiProdutosSemLixo() throws Exception {
		CupomLeituraDTO dto = parseFixture("cupom_2x.tsv");

		assertItensValidos(dto);
		assertTrue(dto.getItens().size() >= 3,
				"esperava >= 3 itens reais, veio " + dto.getItens().size());
		assertContemProduto(dto, "ENERG");
		assertContemProduto(dto, "CEBOLA");
	}

	// ------------------------------------------------------------ lixo não-produto

	@Test
	void fragmentoFiscal_soNumeros_naoViraProduto() {
		CupomParser parser = new CupomParser();
		// linhas de tributos "(0,26 10,79 10,00)" ficam fora das colunas de valores;
		// e uma linha regra de itens nunca tem descrição pendente para essas sobrinhas
		StringBuilder tsv = new StringBuilder(tsvHeader());
		tsv.append(linha(1, palavra(399, 700, "DESCRICAO"), palavra(865, 700, "QTDE"),
				palavra(1170, 700, "VL.UNIT"), palavra(1390, 700, "TOTAL")));
		tsv.append(linha(2, palavra(600, 820, "(0,26"), palavra(665, 820, "10,79"),
				palavra(760, 820, "10,00)")));
		tsv.append(linha(3, palavra(400, 900, "FEDERAL"), palavra(550, 900, "0,26"),
				palavra(760, 900, "10,00")));

		CupomLeituraDTO dto = parser.interpretarTabela(leitura(tsv.toString()));
		assertTrue(dto.getItens().isEmpty(), "fragmentos fiscais viraram produto");
	}

	@Test
	void cabecalhoErodape_naoViramProduto() {
		CupomParser parser = new CupomParser();
		StringBuilder tsv = new StringBuilder(tsvHeader());
		tsv.append(linha(1, palavra(399, 700, "DESCRICAO"), palavra(865, 700, "QTDE"),
				palavra(1170, 700, "VL.UNIT"), palavra(1390, 700, "TOTAL")));
		tsv.append(linha(2, palavra(90, 780, "7891000111112"), palavra(400, 780, "ARROZ"),
				palavra(560, 780, "BRANCO"), palavra(870, 780, "2,000"), palavra(995, 780, "KG"),
				palavra(1175, 780, "29,90"), palavra(1390, 780, "59,80")));
		tsv.append(linha(3, palavra(330, 900, "Qtde."), palavra(430, 900, "total"),
				palavra(560, 900, "de"), palavra(640, 900, "itens:"), palavra(1200, 900, "1")));
		tsv.append(linha(4, palavra(330, 980, "Valor"), palavra(570, 980, "R$"),
				palavra(1200, 980, "69,48")));
		tsv.append(linha(5, palavra(300, 1060, "FORMA"), palavra(500, 1060, "DE"),
				palavra(650, 1060, "PAGAMENTO")));

		CupomLeituraDTO dto = parser.interpretarTabela(leitura(tsv.toString()));

		assertItensValidos(dto);
		assertEquals(1, dto.getItens().size());
		ItemCupomLeituraDTO item = dto.getItens().get(0);
		assertEquals("ARROZ BRANCO", item.getDescricao());
		assertEquals(new BigDecimal("2.000"), item.getQuantidade());
		assertEquals(new BigDecimal("29.90"), item.getPrecoUnitario());
		assertEquals("7891000111112", item.getCodigo());
		assertNotNull(item.getConfianca());
		assertTrue(item.getConfianca() >= 0.70);
	}

	@Test
	void valoresMatematicos_invalidos_abaixamConfianca() {
		CupomParser parser = new CupomParser();
		StringBuilder tsv = new StringBuilder(tsvHeader());
		tsv.append(linha(1, palavra(400, 700, "ARROZ"), palavra(560, 700, "BRANCO"),
				palavra(870, 700, "2,000"), palavra(995, 700, "KG"),
				palavra(1175, 700, "29,90"), palavra(1390, 700, "999,99")));
		CupomLeituraDTO dto = parser.interpretarTabela(leitura(tsv.toString()));
		assertFalse(dto.getItens().isEmpty());
		// 2,000 × 29,90 = 59,80 ≠ 999,99 → penaliza a confiança
		assertTrue(dto.getItens().get(0).getConfianca() < 0.90);
	}

	// --------------------------------------------------------------- helpers

	private CupomLeituraDTO parseFixture(String arquivo) throws Exception {
		String tsv = new String(Files.readAllBytes(Paths.get(RES + arquivo)), StandardCharsets.UTF_8);
		return new CupomParser().interpretarTabela(leitura(tsv));
	}

	private OcrService.LeituraEspacial leitura(String tsv) {
		return new OcrService.LeituraEspacial(1800, 3200, new OcrService().interpretarTsv(tsv));
	}

	private void assertItensValidos(CupomLeituraDTO dto) {
		assertNotNull(dto);
		assertFalse(dto.getItens().isEmpty());
		for (ItemCupomLeituraDTO i : dto.getItens()) {
			String desc = i.getDescricao() == null ? "" : i.getDescricao().toUpperCase();
			assertFalse(desc.isBlank(), "item sem descrição");
			assertNotNull(i.getQuantidade());
			assertTrue(i.getQuantidade().signum() > 0, "item com qtd <= 0: " + desc);
			assertNotNull(i.getPrecoUnitario());
			assertTrue(i.getPrecoUnitario().signum() > 0, "item sem preço: " + desc);
			assertTrue(i.getConfianca() != null && i.getConfianca() > 0, "item sem confiança: " + desc);
			// lixo fiscal/rodapé jamais pode aparecer como descrição de produto
			for (String lixo : new String[] { "FEDERAL", "ESTADUAL", "MUNICIPAL", "TRIBUT",
					"PAGAMENTO", "VALOR TOTAL", "DE ITENS", "CONSUMIDOR", "CONSULTE",
					"QR", "PROTOCOLO", "OBRIGADO", "VOLTE", "RODAPE", "TOTAL" }) {
				assertFalse(desc.contains(lixo), "item contaminado por lixo \"" + lixo + "\": " + desc);
			}
		}
	}

	private void assertContemProduto(CupomLeituraDTO dto, String trecho) {
		assertTrue(dto.getItens().stream().anyMatch(
				i -> i.getDescricao() != null && i.getDescricao().toUpperCase().contains(trecho)),
				"esperava um item contendo \"" + trecho + "\"");
	}

	private String tsvHeader() {
		return "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext\n";
	}

	/**
	 * Monta uma linha TSV de nível 5 com os tokens ({@code texto, x, conf}).
	 */
	private String linha(int lineNum, String... tokens) {
		StringBuilder sb = new StringBuilder();
		int word = 1;
		for (String t : tokens) {
			String[] partes = t.split("\\|"); // texto | x | y | conf
			sb.append("5\t1\t1\t1\t").append(lineNum).append('\t').append(word++)
					.append('\t').append(partes[1]).append('\t').append(partes[2])
					.append("\t40\t40\t90\t").append(partes[0]).append('\n');
		}
		return sb.toString();
	}

	private String palavra(String texto, int x, String conf) {
		return texto + "|" + x + "|700|" + conf;
	}

	private String palavra(int x, int y, String texto) {
		return texto + "|" + x + "|" + y + "|90";
	}
}