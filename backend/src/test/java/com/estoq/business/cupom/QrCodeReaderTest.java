package com.estoq.business.cupom;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QrCodeReaderTest {

	@Test
	void detectaQrCode_valido() throws Exception {
		BufferedImage img = gerarQr("https://www.sefaz.rs.gov.br/nfce/qrcode?chave=123456");
		QrCodeReader reader = new QrCodeReader();
		assertEquals("https://www.sefaz.rs.gov.br/nfce/qrcode?chave=123456", reader.detectarQrCode(img));
	}

	@Test
	void naoDetectaQrCode_emImagemVazia() {
		BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
		QrCodeReader reader = new QrCodeReader();
		assertNull(reader.detectarQrCode(img));
	}

	@Test
	void imagemNull_retornaNull() {
		QrCodeReader reader = new QrCodeReader();
		assertNull(reader.detectarQrCode(null));
	}

	private BufferedImage gerarQr(String conteudo) throws Exception {
		Map<EncodeHintType, Object> hints = new HashMap<>();
		hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
		hints.put(EncodeHintType.MARGIN, 1);
		BitMatrix matrix = new MultiFormatWriter().encode(conteudo, BarcodeFormat.QR_CODE, 200, 200, hints);
		return MatrixToImageWriter.toBufferedImage(matrix);
	}
}
