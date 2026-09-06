package com.estoq.business.cupom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço de OCR (reconhecimento óptico de caracteres) para imagens de cupom.
 *
 * Usa o executável <code>tesseract</code> do sistema via {@link ProcessBuilder}
 * (independente da lib nativa JNA do tess4j, que pode divergir do Leptonica do
 * SO). Antes do reconhecimento a imagem é preparada (tons de cinza, contraste
 * e ampliação quando pequena) para melhorar a leitura de fotos de cupom
 * térmico; o texto é obtido com dois modos de segmentação (--psm 6 e --psm 4)
 * e o melhor resultado é escolhido. O arquivo temporário é sempre removido e
 * falhas são registradas em log e reportadas de forma amigável.
 */
@Service
public class OcrService {

	private static final Logger log = LoggerFactory.getLogger(OcrService.class);

	/** Eixo menor mínimo em px aceitável para o OCR; abaixo disso amplia a imagem. */
	private static final int EIXO_MENOR_MINIMO = 900;

	private static final int LADO_MAX_POS_RECONHECIMENTO = 3600;

	private static final Pattern DECIMAL_BR = Pattern.compile("\\d+,\\d+");

	@Value("${estoq.ocr.tesseract-path:/usr/bin/tesseract}")
	private String tesseractPath;

	@Value("${estoq.ocr.data-path:}")
	private String dataPath;

	@Value("${estoq.ocr.language:por}")
	private String language;

	public String lerTexto(BufferedImage imagem) {
		if (imagem == null) {
			return "";
		}
		File imagemTmp = null;
		try {
			BufferedImage preparada = preparar(imagem);
			imagemTmp = File.createTempFile("estoq-cupom", ".png");
			ImageIO.write(preparada, "png", imagemTmp);

			String textoPsm6 = executar(imagemTmp, "6");
			String textoPsm4 = executar(imagemTmp, "4");
			return escolherMelhor(textoPsm6, textoPsm4);
		} catch (Exception ex) {
			log.warn("Falha no OCR do cupom ({}): {}", tesseractPath, ex.getMessage());
			return "";
		} finally {
			if (imagemTmp != null) {
				boolean removido = imagemTmp.delete();
				if (!removido) {
					imagemTmp.deleteOnExit();
				}
			}
		}
	}

	/**
	 * Prepara a foto do cupom para o OCR: converte para tons de cinza, estica o
	 * contraste (fotos de cupom térmico costumam ter fundo escurecido/baixo
	 * contraste) e amplia imagens pequenas. Visível para permitir teste unitário.
	 */
	BufferedImage preparar(BufferedImage src) {
		BufferedImage cinza = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g1 = cinza.createGraphics();
		try {
			g1.drawImage(src, 0, 0, null);
		} finally {
			g1.dispose();
		}
		esticarContraste(cinza);
		return ampliarSeNecessario(cinza);
	}

	/**
	 * Escolhe entre duas saídas do tesseract a que tem mais linhas prováveis de
	 * item (2+ decimais brasileiros). Empate: texto mais longo. Visível para teste.
	 */
	String escolherMelhor(String a, String b) {
		long nA = contarLinhasCandidatas(a);
		long nB = contarLinhasCandidatas(b);
		if (nB > nA) {
			return b;
		}
		if (nA > nB) {
			return a;
		}
		int lA = a == null ? 0 : a.length();
		int lB = b == null ? 0 : b.length();
		return lB > lA ? b : a;
	}

	/** Heurística simples: linhas com pelo menos 2 decimais (qt + preço). Visível para teste. */
	long contarLinhasCandidatas(String texto) {
		if (texto == null || texto.isBlank()) {
			return 0;
		}
		long n = 0;
		for (String linha : texto.split("\\r?\\n")) {
			String t = linha.trim();
			if (t.isEmpty()) {
				continue;
			}
			Matcher m = DECIMAL_BR.matcher(t);
			int decimais = 0;
			while (m.find()) {
				decimais++;
			}
			if (decimais >= 2) {
				n++;
			}
		}
		return n;
	}

	private String executar(File imagemTmp, String psm) {
		List<String> cmd = new ArrayList<>();
		cmd.add(tesseractPath);
		if (dataPath != null && !dataPath.isBlank()) {
			File dir = new File(dataPath);
			if (dir.exists() && dir.isDirectory()) {
				cmd.add("--tessdata-dir");
				cmd.add(dataPath);
			}
		}
		cmd.add("-l");
		cmd.add(language == null || language.isBlank() ? "por" : language);
		cmd.add("--psm");
		cmd.add(psm);
		cmd.add(imagemTmp.getAbsolutePath());
		cmd.add("stdout");

		ProcessBuilder pb = new ProcessBuilder(cmd);
		// NÃO unifica stderr no texto reconhecido: o tesseract emite avisos
		// ("Estimating resolution as ...") para stderr que poluiriam a leitura.
		pb.redirectErrorStream(false);
		try {
			Process process = pb.start();
			StringBuilder saida = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String linha;
				while ((linha = reader.readLine()) != null) {
					saida.append(linha).append('\n');
				}
			}
			// drena o stderr para o processo não bloquear por excesso de saída
			try (BufferedReader err = new BufferedReader(
					new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
				String e;
				while ((e = err.readLine()) != null) {
					log.debug("tesseract stderr: {}", e);
				}
			}
			int codigo = process.waitFor();
			if (codigo != 0) {
				log.warn("tesseract retornou código {} para a imagem do cupom (psm {}).", codigo, psm);
			}
			return saida.toString();
		} catch (Exception ex) {
			log.warn("Falha no OCR do cupom ({}): {}", tesseractPath, ex.getMessage());
			return "";
		}
	}

	private void esticarContraste(BufferedImage cinza) {
		int w = cinza.getWidth();
		int h = cinza.getHeight();
		int[] pixels = cinza.getRaster().getPixels(0, 0, w, h, (int[]) null);
		int min = 255;
		int max = 0;
		for (int v : pixels) {
			if (v < min) {
				min = v;
			}
			if (v > max) {
				max = v;
			}
		}
		int faixa = max - min;
		if (faixa < 10) {
			return; // já é praticamente binário/nítido
		}
		for (int i = 0; i < pixels.length; i++) {
			int v = (pixels[i] - min) * 255 / faixa;
			pixels[i] = v < 0 ? 0 : (v > 255 ? 255 : v);
		}
		cinza.getRaster().setPixels(0, 0, w, h, pixels);
	}

	private BufferedImage ampliarSeNecessario(BufferedImage cinza) {
		int w = cinza.getWidth();
		int h = cinza.getHeight();
		int menor = Math.min(w, h);
		if (menor >= EIXO_MENOR_MINIMO) {
			return cinza;
		}
		double escala = (double) EIXO_MENOR_MINIMO / menor;
		int nw = Math.max(1, (int) Math.round(w * escala));
		int nh = Math.max(1, (int) Math.round(h * escala));
		int maior = Math.max(nw, nh);
		if (maior > LADO_MAX_POS_RECONHECIMENTO) {
			escala *= (double) LADO_MAX_POS_RECONHECIMENTO / maior;
			nw = Math.max(1, (int) Math.round(w * escala));
			nh = Math.max(1, (int) Math.round(h * escala));
		}
		BufferedImage ampliada = new BufferedImage(nw, nh, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g = ampliada.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(cinza, 0, 0, nw, nh, null);
		} finally {
			g.dispose();
		}
		return ampliada;
	}
}