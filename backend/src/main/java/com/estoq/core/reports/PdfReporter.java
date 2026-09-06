package com.estoq.core.reports;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

/** Gerador de PDF simples para os relatórios do EstoQ (OpenPDF). */
public final class PdfReporter {

	private static final Color CINZA = new Color(0xEE, 0xEE, 0xEE);
	private static final Color TEXTO = new Color(0x22, 0x22, 0x22);

	private PdfReporter() {
	}

	public static byte[] gerarRelatorio(String titulo, String sub, String[] colunas,
			List<String[]> linhas, String[] total)
			throws DocumentException {
		Document doc = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PdfWriter.getInstance(doc, out);
		doc.open();

		Font tituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, TEXTO);
		Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(0x66, 0x66, 0x66));
		Paragraph pTitulo = new Paragraph(titulo, tituloFont);
		pTitulo.setSpacingAfter(2);
		doc.add(pTitulo);
		Paragraph pSub = new Paragraph(sub, subFont);
		pSub.setSpacingAfter(12);
		doc.add(pSub);

		PdfPTable tabela = new PdfPTable(colunas.length);
		tabela.setWidthPercentage(100);
		Font header = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
		Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXTO);
		Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, TEXTO);

		for (String col : colunas) {
			PdfPCell cell = new PdfPCell(new Paragraph(col == null ? "" : col, header));
			cell.setHorizontalAlignment(Element.ALIGN_LEFT);
			cell.setBackgroundColor(new Color(0x33, 0x33, 0x33));
			cell.setPadding(5);
			tabela.addCell(cell);
		}
		for (String[] linha : linhas) {
			for (String valor : linha) {
				PdfPCell cell = new PdfPCell(new Paragraph(valor == null ? "" : valor, normal));
				cell.setPadding(4);
				tabela.addCell(cell);
			}
		}
		if (total != null) {
			for (String valor : total) {
				PdfPCell cell = new PdfPCell(new Paragraph(valor == null ? "" : valor, bold));
				cell.setPadding(4);
				cell.setBackgroundColor(CINZA);
				tabela.addCell(cell);
			}
		}
		doc.add(tabela);
		doc.close();
		return out.toByteArray();
	}
}