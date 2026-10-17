package com.joshuamc1.sicam.core;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Generador de códigos de barras y códigos QR para productos
 */
public class BarcodeGenerator {

    private static final int BARCODE_WIDTH = 300;
    private static final int BARCODE_HEIGHT = 100;
    private static final int QR_WIDTH = 300;
    private static final int QR_HEIGHT = 300;
    private static final int TEXT_HEIGHT = 40;

    /**
     * Genera un código aleatorio de 13 dígitos (EAN-13 compatible)
     */
    public static String generateRandomCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();

        // Generar 12 dígitos aleatorios
        for (int i = 0; i < 12; i++) {
            code.append(random.nextInt(10));
        }

        // Calcular dígito verificador EAN-13
        int checksum = calculateEAN13Checksum(code.toString());
        code.append(checksum);

        return code.toString();
    }

    /**
     * Calcula el dígito verificador para código EAN-13
     */
    private static int calculateEAN13Checksum(String code) {
        int sum = 0;
        for (int i = 0; i < code.length(); i++) {
            int digit = Character.getNumericValue(code.charAt(i));
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        int checksum = (10 - (sum % 10)) % 10;
        return checksum;
    }

    /**
     * Genera un código de barras Code128 y lo guarda en un archivo
     */
    public static void generateBarcode(String code, File outputFile) throws WriterException, IOException {
        Code128Writer barcodeWriter = new Code128Writer();
        BitMatrix bitMatrix = barcodeWriter.encode(code, BarcodeFormat.CODE_128, BARCODE_WIDTH, BARCODE_HEIGHT);

        BufferedImage barcodeImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

        // Crear imagen con texto debajo
        BufferedImage finalImage = new BufferedImage(
            BARCODE_WIDTH,
            BARCODE_HEIGHT + TEXT_HEIGHT,
            BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g2d = finalImage.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, BARCODE_WIDTH, BARCODE_HEIGHT + TEXT_HEIGHT);

        // Dibujar código de barras
        g2d.drawImage(barcodeImage, 0, 0, null);

        // Dibujar texto del código
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.PLAIN, 14));
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(code);
        int x = (BARCODE_WIDTH - textWidth) / 2;
        g2d.drawString(code, x, BARCODE_HEIGHT + 25);

        g2d.dispose();

        ImageIO.write(finalImage, "PNG", outputFile);
    }

    /**
     * Genera un código QR con el código del producto y nombre debajo
     */
    public static void generateQRCode(String code, String productName, File outputFile) throws WriterException, IOException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, 1);

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(code, BarcodeFormat.QR_CODE, QR_WIDTH, QR_HEIGHT, hints);

        BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

        // Calcular altura necesaria para el texto
        int textAreaHeight = 60;

        // Crear imagen final con espacio para texto
        BufferedImage finalImage = new BufferedImage(
            QR_WIDTH,
            QR_HEIGHT + textAreaHeight,
            BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g2d = finalImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, QR_WIDTH, QR_HEIGHT + textAreaHeight);

        // Dibujar código QR
        g2d.drawImage(qrImage, 0, 0, null);

        // Dibujar código debajo del QR
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        FontMetrics fmCode = g2d.getFontMetrics();
        int codeWidth = fmCode.stringWidth(code);
        int xCode = (QR_WIDTH - codeWidth) / 2;
        g2d.drawString(code, xCode, QR_HEIGHT + 20);

        // Dibujar nombre del producto
        g2d.setFont(new Font("Arial", Font.PLAIN, 11));
        FontMetrics fmName = g2d.getFontMetrics();

        // Truncar nombre si es muy largo
        String displayName = productName;
        if (fmName.stringWidth(displayName) > QR_WIDTH - 10) {
            while (fmName.stringWidth(displayName + "...") > QR_WIDTH - 10 && displayName.length() > 0) {
                displayName = displayName.substring(0, displayName.length() - 1);
            }
            displayName += "...";
        }

        int nameWidth = fmName.stringWidth(displayName);
        int xName = (QR_WIDTH - nameWidth) / 2;
        g2d.drawString(displayName, xName, QR_HEIGHT + 40);

        g2d.dispose();

        ImageIO.write(finalImage, "PNG", outputFile);
    }

    /**
     * Valida si un código tiene un formato válido
     */
    public static boolean isValidCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }

        // Permitir códigos alfanuméricos de 3 a 100 caracteres
        return code.matches("^[A-Za-z0-9-_]{3,100}$");
    }
}

