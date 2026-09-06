package com.estoq.business.cupom;

import com.estoq.business.usuario.UsuarioModel;
import com.estoq.core.exceptions.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Set;

/**
 * Leitura de cupom fiscal por imagem. O controller é fino: só valida a imagem,
 * chama o {@link CupomService} e devolve a prévia. A imagem é processada em
 * memória e descartada — nada de cupom é persistido no banco.
 */
@RestController
@RequestMapping("/api/cupons")
public class CupomController {

	private static final Logger log = LoggerFactory.getLogger(CupomController.class);

	private static final Set<String> MIMES_PERMITIDOS = Set.of(
			"image/jpeg", "image/png", "image/webp", "image/bmp", "image/tiff", "image/gif");

	private static final long TIPO_MAX_BYTES = 10 * 1024 * 1024; // 10MB

	private static final int MAX_LADO = 4000;

	@Autowired
	private CupomService cupomService;

	@PostMapping(value = "/ler", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<CupomLeituraDTO> ler(@RequestParam("arquivo") MultipartFile arquivo,
			@RequestAttribute("usuario_logado") UsuarioModel usuario) {
		validarArquivo(arquivo);

		BufferedImage imagem;
		try {
			imagem = ImageIO.read(arquivo.getInputStream());
		} catch (IOException ex) {
			log.warn("Erro ao ler imagem do cupom: {}", ex.getMessage());
			throw new BusinessException("Não foi possível ler a imagem enviada.", HttpStatus.BAD_REQUEST);
		}
		if (imagem == null) {
			throw new BusinessException("A imagem é inválida ou ilegível. Tente uma foto mais nítida.",
					HttpStatus.BAD_REQUEST);
		}

		// limita a dimensão para evitar consumo excessivo de memória no OCR/QR
		imagem = limitarDimensao(imagem);

		CupomLeituraDTO resultado;
		try {
			resultado = cupomService.lerCupom(imagem);
		} finally {
			imagem.flush();
		}

		if (resultado.getItens().isEmpty()) {
			throw new BusinessException(
					"Não foi possível identificar produtos neste cupom. A imagem está muito escura ou ilegível? Tente uma nova foto.",
					HttpStatus.UNPROCESSABLE_ENTITY);
		}
		return ResponseEntity.ok(resultado);
	}

	private void validarArquivo(MultipartFile arquivo) {
		if (arquivo == null || arquivo.isEmpty()) {
			throw new BusinessException("Envie uma imagem do cupom.", HttpStatus.BAD_REQUEST);
		}
		if (arquivo.getSize() > TIPO_MAX_BYTES) {
			throw new BusinessException("A imagem é muito grande (máx. 10MB).", HttpStatus.BAD_REQUEST);
		}
		String tipo = arquivo.getContentType();
		if (tipo == null || tipo.isBlank() || !MIMES_PERMITIDOS.contains(tipo.toLowerCase())) {
			// alguns clientes enviam application/octet-stream para imagens; tenta pelo nome
			String nome = arquivo.getOriginalFilename() == null ? "" : arquivo.getOriginalFilename().toLowerCase();
			if (!(nome.endsWith(".jpg") || nome.endsWith(".jpeg") || nome.endsWith(".png")
					|| nome.endsWith(".webp") || nome.endsWith(".bmp") || nome.endsWith(".tif")
					|| nome.endsWith(".tiff") || nome.endsWith(".gif"))) {
				throw new BusinessException("Formato de imagem inválido. Envie JPG, PNG ou WebP.",
						HttpStatus.BAD_REQUEST);
			}
		}
	}

	private BufferedImage limitarDimensao(BufferedImage src) {
		int w = src.getWidth();
		int h = src.getHeight();
		if (w <= MAX_LADO && h <= MAX_LADO) {
			return src;
		}
		double escala = (double) MAX_LADO / Math.max(w, h);
		int nw = Math.max(1, (int) Math.round(w * escala));
		int nh = Math.max(1, (int) Math.round(h * escala));
		BufferedImage dest = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = dest.createGraphics();
		try {
			g.drawImage(src, 0, 0, nw, nh, null);
		} finally {
			g.dispose();
		}
		return dest;
	}
}
