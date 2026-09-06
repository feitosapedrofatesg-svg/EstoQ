package com.estoq.business.cupom;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcrServiceTest {

	// ------------------------------------------------------------ escolherMelhor

	@Test
	void escolheMelhor_textoComMaisLinhasCandidatas() {
		OcrService ocr = new OcrService();
		String ruim = "CABECALHO SEM NUMEROS\nQUEBRA";
		String bom = "ARROZ TIO JOAO  2,000  39,80\nOLEO DE SOJA  3,000  22,50\n";
		assertEquals(bom, ocr.escolherMelhor(ruim, bom));
		assertEquals(bom, ocr.escolherMelhor(bom, ruim));
	}

	@Test
	void escolheMelhor_trataNuloEeVazio() {
		OcrService ocr = new OcrService();
		assertEquals("TEXTO", ocr.escolherMelhor(null, "TEXTO"));
		assertEquals("TEXTO", ocr.escolherMelhor("TEXTO", null));
		assertEquals("", ocr.escolherMelhor("", ""));
	}

	// -------------------------------------------------------- contarLinhasCandidatas

	@Test
	void contaLinhas_comUmOuMaisDecimais() {
		OcrService ocr = new OcrService();
		String texto = "SUPERMERCADO\nARROZ  2,000  39,80\nOLEO  3,000  22,50\nTOTAL R$ 105,90\n";
		// linhas relevantes: ARROZ e OLEO (2+ decimais). TOTAL só tem 1 decimal.
		assertEquals(2, ocr.contarLinhasCandidatas(texto));
	}

	@Test
	void contarLinhas_trataNull() {
		OcrService ocr = new OcrService();
		assertEquals(0, ocr.contarLinhasCandidatas(null));
		assertEquals(0, ocr.contarLinhasCandidatas(""));
	}

	// ------------------------------------------------------------------ preparar

	@Test
	void preparar_imagemPequenaAmpliaParaEixoMinimo() {
		OcrService ocr = new OcrService();
		BufferedImage pequena = novaImagem(300, 100);
		BufferedImage preparada = ocr.preparar(pequena);
		assertNotNull(preparada);
		int menor = Math.min(preparada.getWidth(), preparada.getHeight());
		assertTrue(menor >= 900, "eixo menor deve ser ampliado para >= 900, foi " + menor);
	}

	@Test
	void preparar_imagemGrandeNaoAmplia() {
		OcrService ocr = new OcrService();
		BufferedImage grande = novaImagem(1200, 1000);
		BufferedImage preparada = ocr.preparar(grande);
		assertNotNull(preparada);
		assertEquals(1200, preparada.getWidth());
		assertEquals(1000, preparada.getHeight());
	}

	@Test
	void preparar_esticaContrasteESaidaPngValida() throws Exception {
		OcrService ocr = new OcrService();
		// imagem com contraste comprimido (cinza ~40..200) — depois do estiramento
		// o pixel mais escuro vira ~0 e o mais claro ~255. Eixo menor >= 900 para o
		// teste não mudar de dimensão (ampliação).
		BufferedImage img = new BufferedImage(1200, 1000, BufferedImage.TYPE_BYTE_GRAY);
		for (int y = 0; y < 1000; y++) {
			for (int x = 0; x < 600; x++) {
				img.setRGB(x, y, corGray(40));
			}
			for (int x = 600; x < 1200; x++) {
				img.setRGB(x, y, corGray(200));
			}
		}
		BufferedImage preparada = ocr.preparar(img);
		// re-encoda como PNG o que o serviço grava em disco
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(preparada, "png", baos);
		BufferedImage relida = ImageIO.read(new ByteArrayInputStream(baos.toByteArray()));
		assertNotNull(relida);
		assertEquals(1200, relida.getWidth());
		assertEquals(1000, relida.getHeight());
	}

	private BufferedImage novaImagem(int w, int h) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, w, h);
		g.dispose();
		return img;
	}

	private int corGray(int v) {
		return new Color(v, v, v).getRGB();
	}
}