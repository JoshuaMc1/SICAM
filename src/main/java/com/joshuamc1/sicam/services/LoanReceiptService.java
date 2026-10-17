package com.joshuamc1.sicam.services;

import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.EscPosConst;
import com.github.anastaciocintra.escpos.Style;
import com.github.anastaciocintra.escpos.barcode.BarCode;
import com.github.anastaciocintra.output.PrinterOutputStream;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.joshuamc1.sicam.core.DatabaseManager;
import com.joshuamc1.sicam.models.*;
import com.joshuamc1.sicam.models.dto.LoanReceiptDTO;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Servicio para imprimir recibos de préstamos en impresoras térmicas o generar PDF
 */
public class LoanReceiptService {
    private static final Logger logger = LoggerFactory.getLogger(LoanReceiptService.class);
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    /**
     * Normaliza el texto para impresoras térmicas
     * Convierte caracteres especiales a su equivalente ASCII
     */
    private static @NotNull String normalizeText(String text) {
        if (text == null) return "";

        // Reemplazar caracteres especiales del español
        return text
            .replace("á", "a").replace("Á", "A")
            .replace("é", "e").replace("É", "E")
            .replace("í", "i").replace("Í", "I")
            .replace("ó", "o").replace("Ó", "O")
            .replace("ú", "u").replace("Ú", "U")
            .replace("ñ", "n").replace("Ñ", "N")
            .replace("ü", "u").replace("Ü", "U")
            // Caracteres problemáticos
            .replace("°", "o")
            .replace("¿", "?")
            .replace("¡", "!");
    }

    /**
     * Genera un código de barras CODE128 como imagen
     * @param barcodeText Texto a codificar en el código de barras
     * @param width Ancho de la imagen en píxeles
     * @param height Alto de la imagen en píxeles
     * @return BufferedImage con el código de barras
     */
    private static @Nullable BufferedImage generateBarcodeImage(String barcodeText, int width, int height) {
        try {
            Code128Writer barcodeWriter = new Code128Writer();
            BitMatrix bitMatrix = barcodeWriter.encode(barcodeText, BarcodeFormat.CODE_128, width, height);
            return MatrixToImageWriter.toBufferedImage(bitMatrix);
        } catch (Exception e) {
            logger.error("Error generando código de barras: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Obtiene la lista de impresoras disponibles en el sistema
     * @return Lista de nombres de impresoras
     */
    public static @NotNull List<String> getAvailablePrinters() {
        List<String> printers = new ArrayList<>();
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
        for (PrintService printService : printServices) {
            printers.add(printService.getName());
        }

        System.out.println("Impresoras disponibles: " + printers);

        return printers;
    }

    /**
     * Convierte una transacción a DTO con todos los datos necesarios
     * Este método debe llamarse en el hilo principal donde hay conexión a BD
     * @param transaction Transacción del préstamo
     * @return DTO con todos los datos del recibo
     */
    public static @NotNull LoanReceiptDTO createReceiptDTO(@NotNull Transaction transaction) {
        LoanReceiptDTO dto = new LoanReceiptDTO();

        // Datos básicos de la transacción
        dto.setLoanCode(transaction.getString("loan_code"));
        dto.setCreatedAt(transaction.getTimestamp("created_at"));
        dto.setExpectedReturnDate(transaction.getDate("expected_return_date"));
        dto.setNotes(transaction.getString("notes"));

        // Usuario que realizó el préstamo
        User user = transaction.parent(User.class);
        if (user != null) {
            dto.setUserName(user.getString("name"));
            dto.setUserUsername(user.getString("username"));
        }

        // Persona que recibe el préstamo
        Person person = transaction.parent(Person.class);
        if (person != null) {
            dto.setPersonFirstName(person.getString("first_name"));
            dto.setPersonLastName(person.getString("last_name"));
            dto.setPersonDni(person.getString("dni"));
            dto.setPersonPhone(person.getString("phone"));
        }

        // Items del préstamo
        List<TransactionItem> items = transaction.getAll(TransactionItem.class);
        List<LoanReceiptDTO.LoanReceiptItemDTO> itemDTOs = new ArrayList<>();

        for (TransactionItem item : items) {
            Product product = item.parent(Product.class);
            if (product != null) {
                LoanReceiptDTO.LoanReceiptItemDTO itemDTO = new LoanReceiptDTO.LoanReceiptItemDTO(
                    product.getString("name"),
                    product.getString("code"),
                    item.getDouble("quantity"),
                    product.getString("unit"),
                    item.getString("notes")
                );
                itemDTOs.add(itemDTO);
            }
        }

        dto.setItems(itemDTOs);

        return dto;
    }

    /**
     * Imprime el recibo de préstamo en una impresora térmica
     * @param transaction Transacción del préstamo
     * @param printerName Nombre de la impresora
     * @param copies Número de copias a imprimir
     * @throws Exception Si ocurre un error durante la impresión
     */
    public static void printReceipt(Transaction transaction, String printerName, int copies) throws Exception {
        if (transaction == null) {
            throw new IllegalArgumentException("La transacción no puede ser nula");
        }

        PrintService printService = findPrintService(printerName);
        if (printService == null) {
            throw new Exception("No se encontró la impresora: " + printerName);
        }

        for (int i = 0; i < copies; i++) {
            try (PrinterOutputStream printerOutputStream = new PrinterOutputStream(printService);
                 EscPos escpos = new EscPos(printerOutputStream)) {

                printReceiptContent(escpos, transaction, i == 0 ? "ORIGINAL" : "COPIA");

                escpos.feed(5);
                escpos.cut(EscPos.CutMode.FULL);

            } catch (IOException e) {
                logger.error("Error al imprimir recibo", e);
                throw new Exception("Error al imprimir: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Imprime el recibo de préstamo en una impresora térmica usando un DTO
     * Este método es seguro para usar en hilos secundarios ya que no accede a la BD
     * @param receiptDTO DTO con los datos del recibo
     * @param printerName Nombre de la impresora
     * @param copies Número de copias a imprimir
     * @throws Exception Si ocurre un error durante la impresión
     */
    public static void printReceiptFromDTO(LoanReceiptDTO receiptDTO, String printerName, int copies) throws Exception {
        if (receiptDTO == null) {
            throw new IllegalArgumentException("El DTO no puede ser nulo");
        }

        PrintService printService = findPrintService(printerName);
        if (printService == null) {
            throw new Exception("No se encontró la impresora: " + printerName);
        }

        for (int i = 0; i < copies; i++) {
            try (PrinterOutputStream printerOutputStream = new PrinterOutputStream(printService);
                 EscPos escpos = new EscPos(printerOutputStream)) {

                printReceiptContentFromDTO(escpos, receiptDTO, i == 0 ? "ORIGINAL" : "COPIA");

                escpos.feed(5);
                escpos.cut(EscPos.CutMode.FULL);

            } catch (IOException e) {
                logger.error("Error al imprimir recibo", e);
                throw new Exception("Error al imprimir: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Imprime el contenido del recibo en el dispositivo ESC/POS
     */
    private static void printReceiptContent(@NotNull EscPos escpos, @NotNull Transaction transaction, String copyType) throws IOException {
        // Estilos mejorados
        Style titleStyle = new Style()
                .setFontSize(Style.FontSize._2, Style.FontSize._2)
                .setJustification(EscPosConst.Justification.Center)
                .setBold(true);

        Style subtitleStyle = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._2)
                .setJustification(EscPosConst.Justification.Center)
                .setBold(true);

        Style boldStyle = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setBold(true);

        Style normalStyle = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setBold(false);

        Style centerStyle = new Style()
                .setJustification(EscPosConst.Justification.Center);

        Style smallCenterStyle = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setJustification(EscPosConst.Justification.Center);

        // Encabezado compacto
        escpos.writeLF(titleStyle, "SICAM");
        escpos.writeLF(subtitleStyle, "RECIBO DE PRESTAMO");
        escpos.writeLF(smallCenterStyle, "[ " + copyType + " ]");
        escpos.feed(1);

        // Código del préstamo
        String loanCode = transaction.getString("loan_code");
        escpos.writeLF(centerStyle, "Codigo: " + loanCode);

        // Código de barras centrado
        try {
            BarCode barcode = new BarCode();
            barcode.setJustification(EscPosConst.Justification.Center);
            escpos.write(barcode, loanCode);
            escpos.feed(1);
        } catch (Exception e) {
            logger.warn("No se pudo generar código de barras: " + e.getMessage());
        }

        // Fecha y hora compactas
        Date createdAt = transaction.getTimestamp("created_at");
        escpos.writeLF(normalStyle, dateFormat.format(createdAt) + " " + timeFormat.format(createdAt));

        // Fecha de devolución
        Date expectedReturnDate = transaction.getDate("expected_return_date");
        if (expectedReturnDate != null) {
            escpos.writeLF(boldStyle, "Devolver: " + dateFormat.format(expectedReturnDate));
        }
        escpos.feed(1);

        // Usuario y Persona en formato compacto
        User user = transaction.parent(User.class);
        if (user != null) {
            escpos.writeLF(normalStyle, "Entrego: " + user.getString("name"));
        }

        Person person = transaction.parent(Person.class);
        if (person != null) {
            String fullName = person.getString("first_name") + " " + person.getString("last_name");
            escpos.writeLF(boldStyle, "Recibe: " + fullName.toUpperCase());
            escpos.writeLF(normalStyle, "DNI: " + person.getString("dni"));
            String phone = person.getString("phone");
            if (phone != null && !phone.isEmpty()) {
                escpos.writeLF(normalStyle, "Tel: " + phone);
            }
        }
        escpos.feed(1);

        // Artículos en formato compacto
        List<TransactionItem> items = transaction.getAll(TransactionItem.class);
        escpos.writeLF(boldStyle, "ARTICULOS (" + items.size() + "):");

        int itemNumber = 1;
        double totalItems = 0;
        for (TransactionItem item : items) {
            Product product = item.parent(Product.class);
            if (product != null) {
                double quantity = item.getDouble("quantity");
                totalItems += quantity;

                String unit = product.getString("unit") != null ? product.getString("unit") : "u";
                String quantityStr = String.format("%.2f", quantity).replaceAll("\\.?0+$", "");

                // Formato compacto: [1] Nombre (Cod: XXX) - 5 unidades
                escpos.writeLF(normalStyle,
                    "[" + itemNumber + "] " + product.getString("name") +
                    " (Cod:" + product.getString("code") + ") - " +
                    quantityStr + " " + unit);

                itemNumber++;
            }
        }
        escpos.feed(1);

        // Resumen compacto
        String totalItemsStr = String.format("%.2f", totalItems).replaceAll("\\.?0+$", "");
        escpos.writeLF(boldStyle, "Total: " + items.size() + " art. / " + totalItemsStr + " unid.");
        escpos.feed(1);

        // Notas generales (solo del préstamo)
        String notes = transaction.getString("notes");
        if (notes != null && !notes.isEmpty()) {
            escpos.writeLF(boldStyle, "Notas:");

            String[] noteLines = notes.split("\n");
            for (String line : noteLines) {
                if (line.length() > 32) {
                    int pos = 0;
                    while (pos < line.length()) {
                        int end = Math.min(pos + 32, line.length());
                        escpos.writeLF(normalStyle, line.substring(pos, end));
                        pos = end;
                    }
                } else {
                    escpos.writeLF(normalStyle, line);
                }
            }
            escpos.feed(1);
        }

        // Pie de página compacto
        escpos.writeLF(smallCenterStyle, "Conserve este recibo");
        escpos.feed(2);
        escpos.writeLF(centerStyle, "_______________________________________");
        escpos.writeLF(centerStyle, "Firma");
    }

    /**
     * Imprime el contenido del recibo en el dispositivo ESC/POS usando DTO
     * Este método no accede a la base de datos
     */
    private static void printReceiptContentFromDTO(@NotNull EscPos escpos, @NotNull LoanReceiptDTO dto, String copyType) throws IOException {
        // Nota: No configuramos charset porque escpos-coffee tiene soporte limitado
        // En su lugar, usamos normalización de texto para máxima compatibilidad

        // Estilos mejorados
        Style titleStyle = new Style()
                .setFontSize(Style.FontSize._2, Style.FontSize._2)
                .setJustification(EscPosConst.Justification.Center)
                .setBold(true);

        Style subtitleStyle = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._2)
                .setJustification(EscPosConst.Justification.Center)
                .setBold(true);

        Style boldStyle = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setBold(true);

        Style normalStyle = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setBold(false);

        Style centerStyle = new Style()
                .setJustification(EscPosConst.Justification.Center);

        Style smallCenterStyle = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setJustification(EscPosConst.Justification.Center);

        // Encabezado compacto
        escpos.writeLF(titleStyle, normalizeText("SICAM"));
        escpos.writeLF(subtitleStyle, normalizeText("RECIBO DE PRESTAMO"));
        escpos.writeLF(smallCenterStyle, normalizeText("[ " + copyType + " ]"));
        escpos.feed(1);

        // Código del préstamo
        String loanCode = dto.getLoanCode();
        escpos.writeLF(centerStyle, normalizeText("Codigo: ") + loanCode);

        // Código de barras centrado
        try {
            BarCode barcode = new BarCode();
            barcode.setJustification(EscPosConst.Justification.Center);
            escpos.write(barcode, loanCode);
        } catch (Exception e) {
            logger.warn("No se pudo generar código de barras: " + e.getMessage());
        }
        escpos.feed(1);

        // Fecha y hora compactas
        Date createdAt = dto.getCreatedAt();
        escpos.writeLF(normalStyle, dateFormat.format(createdAt) + " " + timeFormat.format(createdAt));

        // Fecha de devolución
        Date expectedReturnDate = dto.getExpectedReturnDate();
        if (expectedReturnDate != null) {
            escpos.writeLF(boldStyle, normalizeText("Devolver: ") + dateFormat.format(expectedReturnDate));
        }
        escpos.feed(1);

        // Usuario y Persona en formato compacto
        if (dto.getUserName() != null) {
            escpos.writeLF(normalStyle, normalizeText("Entrego: " + dto.getUserName()));
        }

        if (dto.getPersonFirstName() != null) {
            String fullName = dto.getPersonFirstName() + " " + dto.getPersonLastName();
            escpos.writeLF(boldStyle, normalizeText("Recibe: " + fullName.toUpperCase()));
            escpos.writeLF(normalStyle, "DNI: " + dto.getPersonDni());
            String phone = dto.getPersonPhone();
            if (phone != null && !phone.isEmpty()) {
                escpos.writeLF(normalStyle, "Tel: " + phone);
            }
        }
        escpos.feed(1);

        // Artículos en formato compacto
        List<LoanReceiptDTO.LoanReceiptItemDTO> items = dto.getItems();
        escpos.writeLF(boldStyle, normalizeText("ARTICULOS (" + items.size() + "):"));

        int itemNumber = 1;
        double totalItems = 0;
        for (LoanReceiptDTO.LoanReceiptItemDTO item : items) {
            double quantity = item.getQuantity();
            totalItems += quantity;

            String unit = item.getUnit() != null ? normalizeText(item.getUnit()) : "u";
            String quantityStr = String.format("%.2f", quantity).replaceAll("\\.?0+$", "");

            // Formato compacto: [1] Nombre (Cod: XXX) - 5 unidades
            escpos.writeLF(normalStyle,
                "[" + itemNumber + "] " + normalizeText(item.getProductName()) +
                " (Cod:" + item.getProductCode() + ") - " +
                quantityStr + " " + unit);

            itemNumber++;
        }
        escpos.feed(1);

        // Resumen compacto
        String totalItemsStr = String.format("%.2f", totalItems).replaceAll("\\.?0+$", "");
        escpos.writeLF(boldStyle, normalizeText("Total: " + items.size() + " art. / " + totalItemsStr + " unid."));
        escpos.feed(1);

        // Notas generales (solo del préstamo, no de productos individuales)
        String notes = dto.getNotes();
        if (notes != null && !notes.isEmpty()) {
            escpos.writeLF(boldStyle, normalizeText("Notas:"));

            String[] noteLines = notes.split("\n");
            for (String line : noteLines) {
                String normalizedLine = normalizeText(line);
                if (normalizedLine.length() > 32) {
                    int pos = 0;
                    while (pos < normalizedLine.length()) {
                        int end = Math.min(pos + 32, normalizedLine.length());
                        escpos.writeLF(normalStyle, normalizedLine.substring(pos, end));
                        pos = end;
                    }
                } else {
                    escpos.writeLF(normalStyle, normalizedLine);
                }
            }
            escpos.feed(1);
        }

        // Pie de página compacto
        escpos.writeLF(smallCenterStyle, normalizeText("Conserve este recibo"));
        escpos.feed(2);
        escpos.writeLF(centerStyle, "_______________________________________");
        escpos.writeLF(centerStyle, normalizeText("Firma"));
    }

    /**
     * Genera un PDF del recibo de préstamo
     * @param transaction Transacción del préstamo
     * @param outputPath Ruta donde guardar el PDF
     * @throws Exception Si ocurre un error durante la generación
     */
    public static void generatePdfReceipt(Transaction transaction, String outputPath) throws Exception {
        if (transaction == null) {
            throw new IllegalArgumentException("La transacción no puede ser nula");
        }

        try {
            // Crear DTO dentro de una conexión garantizada para evitar errores "no connection on this thread"
            LoanReceiptDTO dto = DatabaseManager.callInConnection(() -> createReceiptDTO(transaction));

            // Delegar la generación a la versión que no accede a la BD
            generatePdfReceiptFromDTO(dto, outputPath);

            logger.info("PDF generado exitosamente en: {}", outputPath);
        } catch (Exception e) {
            logger.error("Error al generar PDF", e);
            throw new Exception("Error al generar PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Genera un PDF del recibo de préstamo usando un DTO
     * Este método es seguro para usar en hilos secundarios ya que no accede a la BD
     * @param receiptDTO DTO con los datos del recibo
     * @param outputPath Ruta donde guardar el PDF
     * @throws Exception Si ocurre un error durante la generación
     */
    public static void generatePdfReceiptFromDTO(LoanReceiptDTO receiptDTO, String outputPath) throws Exception {
        if (receiptDTO == null) {
            throw new IllegalArgumentException("El DTO no puede ser nulo");
        }

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(outputPath));
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {

            // Configuración del documento con márgenes más amplios
            document.setMargins(40, 40, 40, 40);

            // ============ ENCABEZADO MEJORADO Y PROFESIONAL ============

            // Tabla para el encabezado (3 columnas: Logo - Título - Info)
            Table headerTable = new Table(UnitValue.createPercentArray(new float[]{1, 3, 1}));
            headerTable.setWidth(UnitValue.createPercentValue(100));
            headerTable.setBorder(Border.NO_BORDER);

            // Columna 1: Logo
            Cell logoCell = new Cell();
            logoCell.setBorder(Border.NO_BORDER);
            try {
                InputStream logoStream = LoanReceiptService.class.getResourceAsStream("/images/logo.png");
                if (logoStream != null) {
                    byte[] logoBytes = logoStream.readAllBytes();
                    Image logo = new Image(ImageDataFactory.create(logoBytes));
                    logo.setWidth(80);
                    logo.setHeight(80);
                    logoCell.add(logo);
                }
            } catch (Exception e) {
                logger.warn("No se pudo cargar el logo para PDF", e);
            }
            headerTable.addCell(logoCell);

            // Columna 2: Título central
            Cell titleCell = new Cell();
            titleCell.setBorder(Border.NO_BORDER);
            titleCell.setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE);

            Paragraph mainTitle = new Paragraph("SICAM")
                    .setFontSize(22)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.BLACK);

            Paragraph headerSubtitle = new Paragraph("Sistema de Gestión de Préstamos")
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(new com.itextpdf.kernel.colors.DeviceRgb(100, 100, 100))
                    .setMarginTop(5);

            titleCell.add(mainTitle);
            titleCell.add(headerSubtitle);
            headerTable.addCell(titleCell);

            // Columna 3: Información de fecha/hora
            Cell infoCell = new Cell();
            infoCell.setBorder(Border.NO_BORDER);
            infoCell.setTextAlignment(TextAlignment.RIGHT);
            infoCell.setFontSize(8);

            Paragraph dateInfo = new Paragraph("Fecha de emisión:")
                    .setFontSize(8)
                    .setBold()
                    .setMarginBottom(2);
            Paragraph dateValue = new Paragraph(dateFormat.format(receiptDTO.getCreatedAt()))
                    .setFontSize(9)
                    .setMarginBottom(5);

            Paragraph timeInfo = new Paragraph("Hora:")
                    .setFontSize(8)
                    .setBold()
                    .setMarginBottom(2);
            Paragraph timeValue = new Paragraph(timeFormat.format(receiptDTO.getCreatedAt()))
                    .setFontSize(9);

            infoCell.add(dateInfo);
            infoCell.add(dateValue);
            infoCell.add(timeInfo);
            infoCell.add(timeValue);
            headerTable.addCell(infoCell);

            document.add(headerTable);

            // Línea decorativa superior
            com.itextpdf.layout.element.LineSeparator separator = new com.itextpdf.layout.element.LineSeparator(
                new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(2f));
            separator.setMarginTop(10);
            separator.setMarginBottom(5);
            document.add(separator);

            // Título del recibo destacado con fondo de color
            Paragraph receiptTitle = new Paragraph("RECIBO DE PRÉSTAMO")
                    .setFontSize(18)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.WHITE)
                    .setBackgroundColor(new com.itextpdf.kernel.colors.DeviceRgb(41, 128, 185))
                    .setPadding(10)
                    .setMarginTop(5)
                    .setMarginBottom(5);
            document.add(receiptTitle);

            // Código del préstamo destacado
            Paragraph loanCodeParagraph = new Paragraph()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(15);

            loanCodeParagraph.add(new com.itextpdf.layout.element.Text("Código de Préstamo: ")
                    .setFontSize(11)
                    .setFontColor(new com.itextpdf.kernel.colors.DeviceRgb(100, 100, 100)));

            loanCodeParagraph.add(new com.itextpdf.layout.element.Text(receiptDTO.getLoanCode())
                    .setFontSize(14)
                    .setBold()
                    .setFontColor(new com.itextpdf.kernel.colors.DeviceRgb(41, 128, 185)));

            document.add(loanCodeParagraph);

            // Agregar código de barras CODE128 (más compacto)
            try {
                BufferedImage barcodeImage = generateBarcodeImage(receiptDTO.getLoanCode(), 200, 50);
                if (barcodeImage != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(barcodeImage, "png", baos);
                    Image barcode = new Image(ImageDataFactory.create(baos.toByteArray()));
                    barcode.setHorizontalAlignment(HorizontalAlignment.CENTER);
                    barcode.setWidth(180);
                    barcode.setMarginTop(5);
                    barcode.setMarginBottom(10);
                    document.add(barcode);
                }
            } catch (Exception e) {
                logger.warn("No se pudo generar código de barras en PDF: " + e.getMessage());
            }

            // Línea decorativa inferior (más delgada y con menos margen)
            com.itextpdf.layout.element.LineSeparator separator2 = new com.itextpdf.layout.element.LineSeparator(
                new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(1f));
            separator2.setMarginTop(5);
            separator2.setMarginBottom(10);
            document.add(separator2);

            // Información del préstamo (reorganizada en formato más compacto)
            Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}));
            infoTable.setWidth(UnitValue.createPercentValue(100));


            // Usuario
            if (receiptDTO.getUserName() != null) {
                addTableRow(infoTable, "Realizado por:", receiptDTO.getUserName(), false);
                addTableRow(infoTable, "Usuario:", receiptDTO.getUserUsername(), false);
            }

            // Persona
            if (receiptDTO.getPersonFirstName() != null) {
                addTableRow(infoTable, "Prestado a:",
                           receiptDTO.getPersonFirstName() + " " + receiptDTO.getPersonLastName(), true);
                addTableRow(infoTable, "DNI:", receiptDTO.getPersonDni(), false);
                String phone = receiptDTO.getPersonPhone();
                if (phone != null && !phone.isEmpty()) {
                    addTableRow(infoTable, "Teléfono:", phone, false);
                }
            }

            // Fecha de devolución
            Date expectedReturnDate = receiptDTO.getExpectedReturnDate();
            if (expectedReturnDate != null) {
                addTableRow(infoTable, "Devolución esperada:", dateFormat.format(expectedReturnDate), true);
            }

            document.add(infoTable);

            // Tabla de artículos (con márgenes optimizados)
            Paragraph itemsTitle = new Paragraph("ARTÍCULOS PRESTADOS")
                    .setFontSize(14)
                    .setBold()
                    .setMarginTop(10)
                    .setMarginBottom(5);
            document.add(itemsTitle);

            Table itemsTable = new Table(UnitValue.createPercentArray(new float[]{1, 3, 2, 1, 2}));
            itemsTable.setWidth(UnitValue.createPercentValue(100));

            // Encabezados
            itemsTable.addHeaderCell(createHeaderCell("#"));
            itemsTable.addHeaderCell(createHeaderCell("Artículo"));
            itemsTable.addHeaderCell(createHeaderCell("Código"));
            itemsTable.addHeaderCell(createHeaderCell("Cantidad"));
            itemsTable.addHeaderCell(createHeaderCell("Unidad"));

            // Items
            List<LoanReceiptDTO.LoanReceiptItemDTO> items = receiptDTO.getItems();
            int itemNumber = 1;
            for (LoanReceiptDTO.LoanReceiptItemDTO item : items) {
                itemsTable.addCell(createCell(String.valueOf(itemNumber)));
                itemsTable.addCell(createCell(item.getProductName()));
                itemsTable.addCell(createCell(item.getProductCode()));
                itemsTable.addCell(createCell(String.valueOf(item.getQuantity())));
                itemsTable.addCell(createCell(item.getUnit() != null ? item.getUnit() : "unidad(es)"));
                itemNumber++;
            }

            document.add(itemsTable);

            // Notas (con menos espacio)
            String notes = receiptDTO.getNotes();
            if (notes != null && !notes.isEmpty()) {
                Paragraph notesTitle = new Paragraph("NOTAS:")
                        .setFontSize(12)
                        .setBold()
                        .setMarginTop(10);
                document.add(notesTitle);
                Paragraph notesContent = new Paragraph(notes)
                        .setMarginBottom(5);
                document.add(notesContent);
            }

            // Pie de página (más compacto)
            Paragraph totalItems = new Paragraph("Total de artículos: " + items.size())
                    .setTextAlignment(TextAlignment.CENTER)
                    .setBold()
                    .setMarginTop(15);
            document.add(totalItems);

            Paragraph signature = new Paragraph("Firma: _____________________________")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(20);
            document.add(signature);


            logger.info("PDF generado exitosamente en: {}", outputPath);
        } catch (Exception e) {
            logger.error("Error al generar PDF", e);
            throw new Exception("Error al generar PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Busca un servicio de impresión por nombre
     */
    private static @Nullable PrintService findPrintService(String printerName) {
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
        for (PrintService printService : printServices) {
            if (printService.getName().equalsIgnoreCase(printerName)) {
                return printService;
            }
        }
        return null;
    }

    /**
     * Agrega una fila a la tabla de información
     */
    private static void addTableRow(@NotNull Table table, String label, String value, boolean bold) {
        Cell labelCell = new Cell().add(new Paragraph(label).setBold());
        labelCell.setBorder(Border.NO_BORDER);
        table.addCell(labelCell);

        Paragraph valueParagraph = new Paragraph(value != null ? value : "");
        if (bold) {
            valueParagraph.setBold();
        }
        Cell valueCell = new Cell().add(valueParagraph);
        valueCell.setBorder(Border.NO_BORDER);
        table.addCell(valueCell);
    }

    /**
     * Crea una celda de encabezado para tablas
     */
    private static Cell createHeaderCell(String content) {
        return new Cell()
                .add(new Paragraph(content).setBold())
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setTextAlignment(TextAlignment.CENTER);
    }

    /**
     * Crea una celda normal para tablas
     */
    private static Cell createCell(String content) {
        return new Cell()
                .add(new Paragraph(content != null ? content : ""))
                .setTextAlignment(TextAlignment.LEFT);
    }
}

