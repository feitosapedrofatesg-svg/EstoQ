package com.estoq.integracao.recibo;

import com.estoq.core.exceptions.BusinessException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lê a foto de um cupom fiscal / NFC-e / SAT e devolve o texto bruto extraído
 * pelo motor de OCR Tesseract. Para máxima compatibilidade entre ambientes
 * (Linux/Windows e versões do SO), o binário nativo <code>tesseract</code> é
 * invocado via linha de comando em vez de depender do wrapper JNA (lept4j), que
 * exige uma versão específica da biblioteca Leptonica do sistema. A instalação
 * nativa do Tesseract no sistema é necessária em tempo de execução (ver
 * documentação de implantação).
 */
@Service
public class LeitorReciboService {

	private static final long TIMEOUT_SEGUNDOS = 60;

	private final OcrProperties propriedades;
	private final GravadorReciboService gravadorService;

	public LeitorReciboService(OcrProperties propriedades, GravadorReciboService gravadorService) {
		this.propriedades = propriedades;
		this.gravadorService = gravadorService;
	}

	public LeitorReciboResultado importar(MultipartFile imagem) {
		String texto = lerTexto(imagem);
		return gravadorService.gravar(texto, new LeitorReciboResultado());
	}

	public String lerTexto(MultipartFile imagem) {
		if (imagem == null || imagem.isEmpty()) {
			throw new BusinessException("Nenhuma imagem enviada.", HttpStatus.BAD_REQUEST);
		}
		Path arquivoTemp = null;
		try {
			arquivoTemp = Files.createTempFile("estoq-recibo-", ".png");
			imagem.transferTo(arquivoTemp.toFile());

			List<String> comando = new ArrayList<>();
			comando.add(propriedades.getTesseractPath());
			comando.add(arquivoTemp.toString());
			comando.add("stdout");
			comando.add("-l");
			comando.add(propriedades.getLanguage());

			ProcessBuilder pb = new ProcessBuilder(comando);
			pb.redirectErrorStream(true);
			Process processo = pb.start();

			StringBuilder saida = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(processo.getInputStream(), StandardCharsets.UTF_8))) {
				String linha;
				while ((linha = reader.readLine()) != null) {
					saida.append(linha).append('\n');
				}
			}
			if (!processo.waitFor(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)) {
				processo.destroyForcibly();
				throw new BusinessException("O OCR demorou demais para responder.", HttpStatus.INTERNAL_SERVER_ERROR);
			}
			if (processo.exitValue() != 0) {
				throw new BusinessException(
						"Falha ao executar o Tesseract. Verifique se ele está instalado e o idioma "
								+ propriedades.getLanguage() + " disponível: " + saida.toString().trim(),
						HttpStatus.INTERNAL_SERVER_ERROR);
			}
			return saida.toString().trim();
		} catch (IOException e) {
			throw new BusinessException(
					"Falha ao executar o OCR em \"" + propriedades.getTesseractPath()
							+ "\". Verifique se o Tesseract está instalado no sistema.",
					e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new BusinessException("OCR interrompido.", HttpStatus.INTERNAL_SERVER_ERROR);
		} finally {
			if (arquivoTemp != null) {
				try {
					Files.deleteIfExists(arquivoTemp);
				} catch (IOException ignored) {
					// arquivo temporário: melhor esforço para não deixar lixo no disco
				}
			}
		}
	}
}
