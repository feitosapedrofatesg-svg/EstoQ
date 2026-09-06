package com.estoq.business.cupom;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Detecta QR Code em uma imagem. Retorna o conteúdo decodificado (geralmente
 * a URL de consulta da NFC-e) ou {@code null} quando não há QR legível.
 */
@Component
public class QrCodeReader {

	private static final Logger log = LoggerFactory.getLogger(QrCodeReader.class);

	public String detectarQrCode(BufferedImage imagem) {
		if (imagem == null) {
			return null;
		}
		try {
			Map<DecodeHintType, Object> hints = new HashMap<>();
			hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
			hints.put(DecodeHintType.POSSIBLE_FORMATS, java.util.List.of(com.google.zxing.BarcodeFormat.QR_CODE));
			BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(imagem)));
			Result resultado = new MultiFormatReader().decode(bitmap, hints);
			return resultado != null ? resultado.getText() : null;
		} catch (NotFoundException ex) {
			return null;
		} catch (Exception ex) {
			log.warn("Falha ao decodificar QR Code: {}", ex.getMessage());
			return null;
		}
	}
}
