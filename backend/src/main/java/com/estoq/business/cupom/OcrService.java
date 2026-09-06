package com.estoq.business.cupom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Serviço de OCR (reconhecimento óptico de caracteres) para imagens de cupom.
 *
 * Usa o executável <code>tesseract</code> do sistema via {@link ProcessBuilder}
 * (independente da lib nativa JNA do tess4j, que pode divergir do Leptonica do
 * SO). A imagem é convertida em arquivo temporário (sempre removido ao final);
 * falhas são registradas em log e reportadas de forma amigável.
 */
@Service
public class OcrService {

	private static final Logger log = LoggerFactory.getLogger(OcrService.class);

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
			imagemTmp = File.createTempFile("estoq-cupom", ".png");
			ImageIO.write(imagem, "png", imagemTmp);

			java.util.List<String> cmd = new java.util.ArrayList<>();
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
			cmd.add("6");
			cmd.add(imagemTmp.getAbsolutePath());
			cmd.add("stdout");

			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectErrorStream(true);
			Process process = pb.start();
			StringBuilder saida = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String linha;
				while ((linha = reader.readLine()) != null) {
					saida.append(linha).append('\n');
				}
			}
			int codigo = process.waitFor();
			if (codigo != 0) {
				log.warn("tesseract retornou código {} para a imagem do cupom.", codigo);
			}
			return saida.toString();
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
}
