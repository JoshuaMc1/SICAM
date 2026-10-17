package com.joshuamc1.sicam.utils;

import com.itextpdf.io.exceptions.IOException;
import com.joshuamc1.sicam.models.Transaction;
import com.joshuamc1.sicam.models.dto.LoanReceiptDTO;
import com.joshuamc1.sicam.services.LoanReceiptService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.awt.print.PrinterJob;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Diálogo para seleccionar opciones de impresión de recibos
 */
public class PrintReceiptDialog {
    private static final Logger logger = LoggerFactory.getLogger(PrintReceiptDialog.class);
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");

    /**
     * Muestra el diálogo para imprimir el recibo de préstamo
     * @param transaction Transacción del préstamo
     * @param ownerStage Stage padre
     */
    public static void showPrintDialog(Transaction transaction, Stage ownerStage) {
        if (transaction == null) {
            showErrorAlert("Error", "No se puede imprimir un recibo sin transacción");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Emitir Recibo de Préstamo");
        dialog.setHeaderText("Seleccione cómo desea emitir el recibo");
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (ownerStage != null) {
            dialog.initOwner(ownerStage);
        }

        // Contenido del diálogo
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // Información del préstamo
        Label infoLabel = new Label("Código de préstamo: " + transaction.getString("loan_code"));
        infoLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        content.getChildren().add(infoLabel);

        // Separador
        Separator separator = new Separator();
        content.getChildren().add(separator);

        // Radio buttons para seleccionar tipo de salida
        ToggleGroup outputGroup = new ToggleGroup();

        RadioButton thermalPrinterRadio = new RadioButton("Imprimir en impresora térmica");
        thermalPrinterRadio.setToggleGroup(outputGroup);
        thermalPrinterRadio.setSelected(true);

        RadioButton normalPrinterRadio = new RadioButton("Imprimir en impresora normal (PDF)");
        normalPrinterRadio.setToggleGroup(outputGroup);

        RadioButton pdfRadio = new RadioButton("Guardar como PDF");
        pdfRadio.setToggleGroup(outputGroup);

        content.getChildren().addAll(thermalPrinterRadio, normalPrinterRadio, pdfRadio);

        // Panel para opciones de impresora térmica
        VBox thermalPrinterOptions = new VBox(10);
        thermalPrinterOptions.setPadding(new Insets(10, 0, 0, 25));

        Label thermalPrinterLabel = new Label("Seleccione impresora térmica:");
        ComboBox<String> thermalPrinterCombo = new ComboBox<>();

        // Cargar impresoras disponibles
        List<String> printers = LoanReceiptService.getAvailablePrinters();
        if (printers.isEmpty()) {
            thermalPrinterCombo.getItems().add("No hay impresoras disponibles");
            thermalPrinterCombo.setDisable(true);
        } else {
            thermalPrinterCombo.getItems().addAll(printers);
            thermalPrinterCombo.getSelectionModel().selectFirst();
        }

        // Campo para número de copias (térmica)
        Label thermalCopiesLabel = new Label("Número de copias:");
        Spinner<Integer> thermalCopiesSpinner = new Spinner<>(1, 5, 2);
        thermalCopiesSpinner.setEditable(true);
        thermalCopiesSpinner.setPrefWidth(80);

        thermalPrinterOptions.getChildren().addAll(thermalPrinterLabel, thermalPrinterCombo,
                                                     thermalCopiesLabel, thermalCopiesSpinner);

        // Panel para opciones de impresora normal
        VBox normalPrinterOptions = new VBox(10);
        normalPrinterOptions.setPadding(new Insets(10, 0, 0, 25));
        normalPrinterOptions.setVisible(false);
        normalPrinterOptions.setManaged(false);

        Label normalPrinterLabel = new Label("Seleccione impresora:");
        ComboBox<String> normalPrinterCombo = new ComboBox<>();

        // Usar las mismas impresoras disponibles
        if (printers.isEmpty()) {
            normalPrinterCombo.getItems().add("No hay impresoras disponibles");
            normalPrinterCombo.setDisable(true);
        } else {
            normalPrinterCombo.getItems().addAll(printers);
            normalPrinterCombo.getSelectionModel().selectFirst();
        }

        normalPrinterOptions.getChildren().addAll(normalPrinterLabel, normalPrinterCombo);

        // Panel para opciones de PDF
        VBox pdfOptions = new VBox(10);
        pdfOptions.setPadding(new Insets(10, 0, 0, 25));
        pdfOptions.setVisible(false);
        pdfOptions.setManaged(false);

        Label pdfLabel = new Label("Ubicación del archivo PDF:");
        TextField pdfPathField = new TextField();
        pdfPathField.setEditable(false);
        pdfPathField.setPromptText("Seleccione ubicación...");

        Button browseButton = new Button("Examinar...");
        browseButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Guardar recibo como PDF");
            fileChooser.setInitialFileName("recibo_" + transaction.getString("loan_code") + "_" +
                                          dateFormat.format(new Date()) + ".pdf");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
            );

            File file = fileChooser.showSaveDialog(dialog.getOwner());
            if (file != null) {
                pdfPathField.setText(file.getAbsolutePath());
            }
        });

        HBox pdfPathBox = new HBox(10);
        pdfPathBox.getChildren().addAll(pdfPathField, browseButton);
        HBox.setHgrow(pdfPathField, javafx.scene.layout.Priority.ALWAYS);

        pdfOptions.getChildren().addAll(pdfLabel, pdfPathBox);

        content.getChildren().addAll(thermalPrinterOptions, normalPrinterOptions, pdfOptions);

        // Listeners para cambiar entre opciones
        thermalPrinterRadio.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            thermalPrinterOptions.setVisible(isSelected);
            thermalPrinterOptions.setManaged(isSelected);
        });

        normalPrinterRadio.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            normalPrinterOptions.setVisible(isSelected);
            normalPrinterOptions.setManaged(isSelected);
        });

        pdfRadio.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            pdfOptions.setVisible(isSelected);
            pdfOptions.setManaged(isSelected);
        });

        dialog.getDialogPane().setContent(content);

        // Botones
        ButtonType printButtonType = new ButtonType("Emitir", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(printButtonType, cancelButtonType);

        // Validación y procesamiento
        Button printButton = (Button) dialog.getDialogPane().lookupButton(printButtonType);
        printButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (thermalPrinterRadio.isSelected()) {
                // Validar impresora térmica seleccionada
                if (thermalPrinterCombo.getValue() == null || thermalPrinterCombo.isDisable()) {
                    showErrorAlert("Error", "Debe seleccionar una impresora térmica válida");
                    event.consume();
                    return;
                }

                // Imprimir en impresora térmica
                String printerName = thermalPrinterCombo.getValue();
                int copies = thermalCopiesSpinner.getValue();

                // Mostrar diálogo de progreso
                showProgressAndPrint(transaction, printerName, copies);

            } else if (normalPrinterRadio.isSelected()) {
                // Validar impresora normal seleccionada
                if (normalPrinterCombo.getValue() == null || normalPrinterCombo.isDisable()) {
                    showErrorAlert("Error", "Debe seleccionar una impresora válida");
                    event.consume();
                    return;
                }

                // Imprimir PDF en impresora normal
                String printerName = normalPrinterCombo.getValue();
                showProgressAndPrintPdf(transaction, printerName);

            } else if (pdfRadio.isSelected()) {
                // Validar ruta de archivo
                if (pdfPathField.getText() == null || pdfPathField.getText().isEmpty()) {
                    showErrorAlert("Error", "Debe seleccionar una ubicación para guardar el PDF");
                    event.consume();
                    return;
                }

                // Generar PDF
                String pdfPath = pdfPathField.getText();
                showProgressAndGeneratePdf(transaction, pdfPath);
            }
        });

        dialog.showAndWait();
    }

    /**
     * Muestra un diálogo de progreso mientras se imprime
     * Usa un Stage personalizado en lugar de Alert para mayor confiabilidad
     */
    private static void showProgressAndPrint(Transaction transaction, String printerName, int copies) {
        // Convertir la transacción a DTO en el hilo principal (donde hay conexión a BD)
        LoanReceiptDTO receiptDTO = LoanReceiptService.createReceiptDTO(transaction);

        // Crear Stage personalizado para el progreso
        Stage progressStage = new Stage();
        progressStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        progressStage.setTitle("Imprimiendo");
        progressStage.setResizable(false);

        // Contenido del diálogo de progreso
        VBox progressBox = new VBox(15);
        progressBox.setPadding(new Insets(20));
        progressBox.setAlignment(javafx.geometry.Pos.CENTER);
        progressBox.setStyle("-fx-background-color: white;");

        javafx.scene.control.ProgressIndicator progressIndicator = new javafx.scene.control.ProgressIndicator();
        progressIndicator.setPrefSize(50, 50);

        javafx.scene.control.Label messageLabel = new javafx.scene.control.Label("Imprimiendo recibo...");
        messageLabel.setStyle("-fx-font-size: 14px;");

        javafx.scene.control.Label detailLabel = new javafx.scene.control.Label("Por favor espere mientras se imprime el recibo.");
        detailLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");
        detailLabel.setWrapText(true);
        detailLabel.setMaxWidth(300);

        progressBox.getChildren().addAll(progressIndicator, messageLabel, detailLabel);

        javafx.scene.Scene scene = new javafx.scene.Scene(progressBox, 350, 150);
        progressStage.setScene(scene);

        // Ejecutar impresión en un hilo separado usando el DTO (no requiere conexión a BD)
        Thread printThread = new Thread(() -> {
            boolean success = false;
            String errorMessage = null;

            try {
                logger.info("Iniciando impresión en: {}", printerName);
                // Usar el método con DTO que no accede a la base de datos
                LoanReceiptService.printReceiptFromDTO(receiptDTO, printerName, copies);
                success = true;
                logger.info("Impresión completada exitosamente");
            } catch (Exception e) {
                logger.error("Error al imprimir recibo", e);
                errorMessage = e.getMessage();
            }

            final boolean finalSuccess = success;
            final String finalError = errorMessage;

            // Cerrar el Stage y mostrar resultado
            Platform.runLater(() -> {
                try {
                    progressStage.close();
                    logger.info("Stage de progreso cerrado");
                } catch (Exception e) {
                    logger.error("Error al cerrar Stage de progreso", e);
                }

                if (finalSuccess) {
                    showSuccessAlert("Éxito", "El recibo se ha impreso correctamente.\n" +
                                            "Copias impresas: " + copies);
                } else {
                    showErrorAlert("Error de Impresión",
                                  "No se pudo imprimir el recibo:\n" + finalError);
                }
            });
        });
        printThread.setDaemon(true);
        printThread.start();

        // Mostrar el Stage (no bloqueante)
        progressStage.show();
    }

    /**
     * Muestra un diálogo de progreso mientras se genera e imprime el PDF en impresora normal
     */
    private static void showProgressAndPrintPdf(Transaction transaction, String printerName) {
        // Crear DTO en el hilo principal (donde hay conexión a BD)
        com.joshuamc1.sicam.models.dto.LoanReceiptDTO receiptDTO;
        try {
            receiptDTO = LoanReceiptService.createReceiptDTO(transaction);
        } catch (Exception e) {
            logger.error("Error al crear DTO del recibo", e);
            showErrorAlert("Error", "No se pudo preparar los datos del recibo:\n" + e.getMessage());
            return;
        }

        // Crear Stage personalizado para el progreso
        Stage progressStage = new Stage();
        progressStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        progressStage.setTitle("Imprimiendo PDF");
        progressStage.setResizable(false);

        // Contenido del diálogo de progreso
        VBox progressBox = new VBox(15);
        progressBox.setPadding(new Insets(20));
        progressBox.setAlignment(javafx.geometry.Pos.CENTER);
        progressBox.setStyle("-fx-background-color: white;");

        javafx.scene.control.ProgressIndicator progressIndicator = new javafx.scene.control.ProgressIndicator();
        progressIndicator.setPrefSize(50, 50);

        javafx.scene.control.Label messageLabel = new javafx.scene.control.Label("Generando e imprimiendo recibo...");
        messageLabel.setStyle("-fx-font-size: 14px;");

        javafx.scene.control.Label detailLabel = new javafx.scene.control.Label("Por favor espere mientras se genera e imprime el PDF.");
        detailLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");
        detailLabel.setWrapText(true);
        detailLabel.setMaxWidth(300);

        progressBox.getChildren().addAll(progressIndicator, messageLabel, detailLabel);

        javafx.scene.Scene scene = new javafx.scene.Scene(progressBox, 350, 150);
        progressStage.setScene(scene);

        // Ejecutar generación e impresión en un hilo separado
        Thread printThread = new Thread(() -> {
            boolean success = false;
            String errorMessage = null;
            String tempPdfPath = null;

            try {
                // Crear archivo temporal para el PDF
                tempPdfPath = System.getProperty("java.io.tmpdir") + File.separator +
                             "recibo_" + transaction.getString("loan_code") + "_temp.pdf";

                logger.info("Generando PDF temporal: {}", tempPdfPath);
                LoanReceiptService.generatePdfReceiptFromDTO(receiptDTO, tempPdfPath);

                logger.info("Enviando PDF a impresora: {}", printerName);
                // Imprimir el PDF usando el servicio del sistema
                printPdfFile(tempPdfPath, printerName);

                success = true;
                logger.info("Impresión de PDF completada exitosamente");
            } catch (Exception e) {
                logger.error("Error al imprimir PDF", e);
                errorMessage = e.getMessage();
            }

            final boolean finalSuccess = success;
            final String finalError = errorMessage;
            final String finalTempPath = tempPdfPath;

            // Cerrar el Stage y mostrar resultado
            Platform.runLater(() -> {
                try {
                    progressStage.close();
                    logger.info("Stage de progreso cerrado");
                } catch (Exception e) {
                    logger.error("Error al cerrar Stage de progreso", e);
                }

                if (finalSuccess) {
                    showSuccessAlert("Éxito", "El recibo se ha impreso correctamente en: " + printerName);

                    // Eliminar archivo temporal en segundo plano
                    if (finalTempPath != null) {
                        new Thread(() -> {
                            try {
                                Thread.sleep(2000); // Esperar 2 segundos
                                new File(finalTempPath).delete();
                                logger.info("Archivo temporal eliminado: {}", finalTempPath);
                            } catch (Exception e) {
                                logger.warn("No se pudo eliminar archivo temporal: {}", finalTempPath);
                            }
                        }).start();
                    }
                } else {
                    showErrorAlert("Error de Impresión",
                                  "No se pudo imprimir el recibo:\n" + finalError);
                }
            });
        });
        printThread.setDaemon(true);
        printThread.start();

        // Mostrar el Stage (no bloqueante)
        progressStage.show();
    }

    /**
     * Imprime un archivo PDF usando el servicio de impresión del sistema
     */
    private static void printPdfFile(String pdfPath, String printerName) throws Exception {
        try {
            // Usar Desktop.print() de Java para imprimir el PDF
            File pdfFile = new File(pdfPath);
            if (!pdfFile.exists()) {
                throw new Exception("El archivo PDF no existe: " + pdfPath);
            }

            // Intentar imprimir usando el sistema operativo
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                if (desktop.isSupported(java.awt.Desktop.Action.PRINT)) {
                    printPdfWithPDFBox(pdfFile.getAbsolutePath(), printerName);
                } else {
                    throw new Exception("La función de impresión no está soportada en este sistema");
                }
            } else {
                throw new Exception("Desktop no está soportado en este sistema");
            }
        } catch (Exception e) {
            logger.error("Error al imprimir PDF", e);
            throw new Exception("Error al imprimir el archivo PDF: " + e.getMessage(), e);
        }
    }

    public static void printPdfWithPDFBox(String pdfPath, String printerName) throws Exception {
        File file = new File(pdfPath);
        if (!file.exists()) {
            throw new IOException("El archivo PDF no existe: " + pdfPath);
        }

        // Cargar PDF
        PDDocument document = PDDocument.load(file);

        // Obtener impresoras del sistema
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        PrintService selectedService = null;

        for (PrintService service : services) {
            if (service.getName().equalsIgnoreCase(printerName)) {
                selectedService = service;
                break;
            }
        }

        if (selectedService == null) {
            document.close();
            throw new Exception("Impresora no encontrada: " + printerName);
        }

        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(selectedService);
        job.setPageable(new PDFPageable(document));

        job.print();

        document.close();
    }

    /**
     * Muestra un diálogo de progreso mientras se genera el PDF
     * Usa un Stage personalizado en lugar de Alert para mayor confiabilidad
     */
    private static void showProgressAndGeneratePdf(Transaction transaction, String pdfPath) {
        // Crear DTO en el hilo principal (donde hay conexión a BD)
        com.joshuamc1.sicam.models.dto.LoanReceiptDTO receiptDTO;
        try {
            receiptDTO = LoanReceiptService.createReceiptDTO(transaction);
        } catch (Exception e) {
            logger.error("Error al crear DTO del recibo", e);
            showErrorAlert("Error", "No se pudo preparar los datos del recibo:\n" + e.getMessage());
            return;
        }

        // Crear Stage personalizado para el progreso
        Stage progressStage = new Stage();
        progressStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        progressStage.setTitle("Generando PDF");
        progressStage.setResizable(false);

        // Contenido del diálogo de progreso
        VBox progressBox = new VBox(15);
        progressBox.setPadding(new Insets(20));
        progressBox.setAlignment(javafx.geometry.Pos.CENTER);
        progressBox.setStyle("-fx-background-color: white;");

        javafx.scene.control.ProgressIndicator progressIndicator = new javafx.scene.control.ProgressIndicator();
        progressIndicator.setPrefSize(50, 50);

        javafx.scene.control.Label messageLabel = new javafx.scene.control.Label("Generando recibo en PDF...");
        messageLabel.setStyle("-fx-font-size: 14px;");

        javafx.scene.control.Label detailLabel = new javafx.scene.control.Label("Por favor espere mientras se genera el archivo PDF.");
        detailLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");
        detailLabel.setWrapText(true);
        detailLabel.setMaxWidth(300);

        progressBox.getChildren().addAll(progressIndicator, messageLabel, detailLabel);

        javafx.scene.Scene scene = new javafx.scene.Scene(progressBox, 350, 150);
        progressStage.setScene(scene);

        // Ejecutar generación en un hilo separado
        Thread pdfThread = new Thread(() -> {
            boolean success = false;
            String errorMessage = null;

            try {
                logger.info("Iniciando generación de PDF: {}", pdfPath);
                LoanReceiptService.generatePdfReceiptFromDTO(receiptDTO, pdfPath);
                success = true;
                logger.info("PDF generado exitosamente");
            } catch (Exception e) {
                logger.error("Error al generar PDF", e);
                errorMessage = e.getMessage();
            }

            final boolean finalSuccess = success;
            final String finalError = errorMessage;

            // Cerrar el Stage y mostrar resultado
            Platform.runLater(() -> {
                try {
                    progressStage.close();
                    logger.info("Stage de progreso cerrado");
                } catch (Exception e) {
                    logger.error("Error al cerrar Stage de progreso", e);
                }

                if (finalSuccess) {
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("Éxito");
                    successAlert.setHeaderText("PDF generado correctamente");
                    successAlert.setContentText("El recibo se ha guardado en:\n" + pdfPath);

                    ButtonType openButton = new ButtonType("Abrir ubicación");
                    ButtonType closeButton = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);
                    successAlert.getButtonTypes().setAll(openButton, closeButton);

                    Optional<ButtonType> result = successAlert.showAndWait();
                    if (result.isPresent() && result.get() == openButton) {
                        try {
                            File pdfFile = new File(pdfPath);
                            java.awt.Desktop.getDesktop().open(pdfFile.getParentFile());
                        } catch (Exception e) {
                            logger.error("Error al abrir ubicación del PDF", e);
                        }
                    }
                } else {
                    showErrorAlert("Error al Generar PDF",
                                  "No se pudo generar el recibo en PDF:\n" + finalError);
                }
            });
        });
        pdfThread.setDaemon(true);
        pdfThread.start();

        // Mostrar el Stage (no bloqueante)
        progressStage.show();
    }

    /**
     * Muestra un diálogo de error
     */
    private static void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Muestra un diálogo de éxito
     */
    private static void showSuccessAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

