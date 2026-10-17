package com.joshuamc1.sicam.export;

import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.models.dto.AuditDTO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Exportador PDF para auditoría con fuente embebida, multi-página y ajuste de texto.
 * Uso:
 * PdfAuditExporter.export(data, destino, progreso::accept, cancelFlag::get, currentUser)
 */
public final class PdfAuditExporter {

    private PdfAuditExporter() {}

    public static void export(List<AuditDTO> data,
                              File outFile,
                              Consumer<Double> progressCallback,
                              Supplier<Boolean> isCancelled,
                              String currentUser) throws IOException {

        if (data == null) data = List.of();
        try (PDDocument doc = new PDDocument()) {
            PDFont regularFont = loadEmbeddedFont(doc);
            PDFont boldFont = PDType1Font.HELVETICA_BOLD;

            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);

            float margin = 40f;
            float pageWidth = page.getMediaBox().getWidth();
            float y = page.getMediaBox().getHeight() - margin;

            // Logo
            y = drawLogo(doc, cs, pageWidth, y);
            // Título
            cs.setFont(boldFont, 16);
            y = drawCenteredText(cs, boldFont, 16, "Reporte de auditoría", pageWidth, y) - 8f;

            // Línea usuario / fecha
            cs.setFont(regularFont, 10);
            String username = currentUser == null ? "-" : currentUser;
            String fechaReporte = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            cs.beginText(); cs.newLineAtOffset(margin, y); cs.showText("Usuario: " + username); cs.endText();
            String fechaText = "Fecha: " + fechaReporte;
            float fechaWidth = regularFont.getStringWidth(fechaText)/1000*10;
            cs.beginText(); cs.newLineAtOffset(pageWidth - margin - fechaWidth, y); cs.showText(fechaText); cs.endText();
            y -= 24f;

            // Calcular anchos responsivos para Usuario, Accion y Tabla
            float colIdW = 40f;
            float colRegW = 50f;
            float colFechaW = 105f;

            // Calcular anchos mínimos y máximos
            float minUserW = 80f;
            float maxUserW = 150f;
            float minActionW = 60f;
            float maxActionW = 100f;
            float minTableW = 60f;
            float maxTableW = 120f;

            // Calcular ancho necesario basado en contenido
            float colUserW = calculateOptimalWidth(data, AuditDTO::getUsuarioName, regularFont, 9, minUserW, maxUserW);
            float colActionW = calculateOptimalWidth(data, AuditDTO::getAccion, regularFont, 9, minActionW, maxActionW);
            float colTableW = calculateOptimalWidth(data, AuditDTO::getTablaAfectada, regularFont, 9, minTableW, maxTableW);

            float tableWidth = pageWidth - margin * 2;
            float colDetailsW = tableWidth - (colIdW + colUserW + colActionW + colTableW + colRegW + colFechaW);

            // Header
            y = drawHeader(cs, boldFont, margin, y, colIdW, colUserW, colActionW, colTableW, colRegW, colFechaW) - 2f;

            // Data rows
            int fontSize = 9;
            int total = data.size();
            for (int i = 0; i < total; i++) {
                if (isCancelled.get()) {
                    cs.close();
                    return; // cancelado
                }
                AuditDTO r = data.get(i);
                float rowHeight = computeRowHeight(r, regularFont, fontSize, colDetailsW);
                // salto de página si no cabe
                if (y - rowHeight < margin) {
                    cs.close();
                    page = new PDPage(PDRectangle.LETTER);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = page.getMediaBox().getHeight() - margin;
                    y = drawHeader(cs, boldFont, margin, y, colIdW, colUserW, colActionW, colTableW, colRegW, colFechaW) - 2f;
                }

                cs.setFont(regularFont, fontSize);
                float x = margin;
                drawCell(cs, String.valueOf(r.getId()==null?"":r.getId()), x, y); x += colIdW;

                // Usuario - adaptativo
                String uname = r.getUsuarioName() != null ? r.getUsuarioName() : (r.getUsuarioId()==null?"":String.valueOf(r.getUsuarioId()));
                drawCell(cs, smartTruncate(uname, regularFont, fontSize, colUserW - 4), x, y); x += colUserW;

                // Acción - adaptativo
                drawCell(cs, smartTruncate(r.getAccion(), regularFont, fontSize, colActionW - 4), x, y); x += colActionW;

                // Tabla - adaptativo
                drawCell(cs, smartTruncate(r.getTablaAfectada(), regularFont, fontSize, colTableW - 4), x, y); x += colTableW;

                drawCell(cs, r.getRegistroAfectadoId()==null?"":String.valueOf(r.getRegistroAfectadoId()), x, y); x += colRegW;
                drawCell(cs, truncate(r.getFecha(),25), x, y); x += colFechaW;
                drawMultiline(cs, r.getDetalles(), x, y, fontSize, regularFont, colDetailsW);

                y -= rowHeight;
                if (progressCallback != null) progressCallback.accept((i+1d)/total);
            }
            cs.close();
            doc.save(outFile); // guardar resultado
        }
    }

    private static PDFont loadEmbeddedFont(PDDocument doc) {
        // Intenta cargar fuente embebida desde resources /fonts/DejaVuSans.ttf
        try (InputStream is = PdfAuditExporter.class.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            if (is != null) {
                return PDType0Font.load(doc, is, true);
            }
        } catch (Exception ex) {
            Logs.warn(PdfAuditExporter.class, "No se pudo cargar fuente embebida: {}", ex.getMessage());
        }
        return PDType1Font.HELVETICA; // fallback
    }

    private static float drawLogo(PDDocument doc, PDPageContentStream cs, float pageWidth, float y) {
        try (InputStream is = PdfAuditExporter.class.getResourceAsStream("/images/logo.png")) {
            if (is != null) {
                byte[] img = is.readAllBytes();
                PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, img, "logo");
                float w = 70f, h = 70f, x = (pageWidth - w)/2f, imgY = y - h;
                cs.drawImage(pdImage, x, imgY, w, h);
                return imgY - 10f;
            }
        } catch (IOException e) {
            Logs.warn(PdfAuditExporter.class, "Logo no cargado: {}", e.getMessage());
        }
        return y;
    }

    private static float drawCenteredText(PDPageContentStream cs, PDFont font, int size, String text, float pageWidth, float y) throws IOException {
        float textWidth = font.getStringWidth(text)/1000*size;
        float x = (pageWidth - textWidth)/2f;
        cs.beginText(); cs.newLineAtOffset(x, y); cs.showText(text); cs.endText();
        return y - size - 4f;
    }

    private static float drawHeader(PDPageContentStream cs, PDFont bold, float margin, float y,
                                    float colIdW, float colUserW, float colActionW, float colTableW,
                                    float colRegW, float colFechaW) throws IOException {
        cs.setFont(bold, 9);
        float x = margin;
        drawCell(cs, "ID", x, y); x += colIdW;
        drawCell(cs, "Usuario", x, y); x += colUserW;
        drawCell(cs, "Acción", x, y); x += colActionW;
        drawCell(cs, "Tabla", x, y); x += colTableW;
        drawCell(cs, "Reg ID", x, y); x += colRegW;
        drawCell(cs, "Fecha", x, y); x += colFechaW;
        drawCell(cs, "Detalles", x, y);
        return y - 16f;
    }

    private static void drawCell(PDPageContentStream cs, String text, float x, float y) throws IOException {
        cs.beginText(); cs.newLineAtOffset(x + 2, y); cs.showText(text==null?"":sanitize(text)); cs.endText();
    }

    private static void drawMultiline(PDPageContentStream cs, String text, float x, float y, int fontSize, PDFont font,
                                      float maxWidth) throws IOException {
        if (text == null) return;
        List<String> lines = wrapText(text, font, fontSize, maxWidth - 4);
        float lineY = y;
        for (String line : lines) {
            cs.beginText(); cs.newLineAtOffset(x + 2, lineY); cs.showText(sanitize(line)); cs.endText();
            lineY -= fontSize + 2;
        }
    }

    private static float computeRowHeight(AuditDTO r, PDFont font, int fontSize, float colDetailsW) {
        int lines = wrapText(r.getDetalles()==null?"":r.getDetalles(), font, fontSize, colDetailsW - 4).size();
        return Math.max(fontSize + 6, lines * (fontSize + 2));
    }

    private static List<String> wrapText(String text, PDFont font, int fontSize, float maxWidth) {
        List<String> result = new ArrayList<>();
        if (text == null) return result;
        String[] tokens = text.replace('\n',' ').split(" ");
        StringBuilder line = new StringBuilder();
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            String candidate = line.length()==0? token : line + " " + token;
            try {
                float w = font.getStringWidth(candidate)/1000*fontSize;
                if (w <= maxWidth) {
                    line.setLength(0); line.append(candidate);
                } else {
                    if (line.length()>0) result.add(line.toString());
                    // si el token solo excede: dividir por caracteres
                    if (font.getStringWidth(token)/1000*fontSize > maxWidth) {
                        result.addAll(splitLongToken(token, font, fontSize, maxWidth));
                        line.setLength(0);
                    } else {
                        line.setLength(0); line.append(token);
                    }
                }
            } catch (IOException e) {
                // fallback simple
                if (line.length()>0) result.add(line.toString());
                line.setLength(0); line.append(token);
            }
            if (result.size() >= 5) { // límite de líneas por celda
                break;
            }
        }
        if (line.length()>0 && result.size()<5) result.add(line.toString());
        if (result.size()==5 && tokens.length>result.stream().mapToInt(String::length).sum()) {
            // marcar más contenido
            String last = result.get(4);
            if (!last.endsWith("...")) result.set(4, truncate(last, Math.max(3, last.length()-3)) + "...");
        }
        return result;
    }

    private static List<String> splitLongToken(String token, PDFont font, int fontSize, float maxWidth) throws IOException {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char c : token.toCharArray()) {
            current.append(c);
            float w = font.getStringWidth(current.toString())/1000*fontSize;
            if (w > maxWidth) {
                current.setLength(current.length()-1);
                parts.add(current.toString());
                current.setLength(0);
                current.append(c);
            }
        }
        if (current.length()>0) parts.add(current.toString());
        return parts;
    }

    private static float calculateOptimalWidth(List<AuditDTO> data,
                                               java.util.function.Function<AuditDTO, String> getter,
                                               PDFont font,
                                               int fontSize,
                                               float minWidth,
                                               float maxWidth) {
        float maxTextWidth = minWidth;

        // Analizar los primeros 100 registros para determinar el ancho óptimo
        int samplesToCheck = Math.min(100, data.size());
        for (int i = 0; i < samplesToCheck; i++) {
            String text = getter.apply(data.get(i));
            if (text != null && !text.isEmpty()) {
                try {
                    float textWidth = font.getStringWidth(text) / 1000 * fontSize + 6; // +6 para padding
                    if (textWidth > maxTextWidth) {
                        maxTextWidth = textWidth;
                    }
                } catch (IOException e) {
                    // Ignorar errores y continuar
                }
            }
        }

        // Limitar al rango min-max
        return Math.min(maxWidth, Math.max(minWidth, maxTextWidth));
    }

    private static String smartTruncate(String text, PDFont font, int fontSize, float maxWidth) {
        if (text == null || text.isEmpty()) return "";

        try {
            float textWidth = font.getStringWidth(text) / 1000 * fontSize;
            if (textWidth <= maxWidth) {
                // El texto cabe completo
                return text;
            }

            // Necesita truncarse
            int maxChars = (int) (text.length() * maxWidth / textWidth) - 3; // -3 para los puntos
            if (maxChars <= 0) return "...";
            return text.substring(0, maxChars) + "...";

        } catch (IOException e) {
            // Fallback simple por caracteres
            return truncate(text, 20);
        }
    }

    private static String sanitize(String s) { return s.replace("\r"," ").replace("\n"," "); }
    private static String truncate(String s, int max) { if (s==null) return ""; return s.length()<=max? s : s.substring(0, Math.max(0,max-3))+"..."; }
}
