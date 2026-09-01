package com.estoq.integracao.recibo;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuração do motor de OCR (Tesseract) usado na leitura de recibos.
 * Valores mapeados a partir de estoq.ocr.* no application.properties.
 */
@Data
@Component
@ConfigurationProperties(prefix = "estoq.ocr")
public class OcrProperties {

	private String tesseractPath = "/usr/bin/tesseract";

	private String dataPath = "/usr/share/tesseract-ocr/5/tessdata";

	private String language = "por";
}
