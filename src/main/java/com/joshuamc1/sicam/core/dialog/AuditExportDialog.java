package com.joshuamc1.sicam.core.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.Optional;

public class AuditExportDialog {

    public static class ExportOptions {
        private final boolean exportAll;
        private final LocalDate fromDate;
        private final LocalDate toDate;

        public ExportOptions(boolean exportAll, LocalDate fromDate, LocalDate toDate) {
            this.exportAll = exportAll;
            this.fromDate = fromDate;
            this.toDate = toDate;
        }

        public boolean isExportAll() {
            return exportAll;
        }

        public LocalDate getFromDate() {
            return fromDate;
        }

        public LocalDate getToDate() {
            return toDate;
        }
    }

    public static Optional<ExportOptions> showDialog() {
        Dialog<ExportOptions> dialog = new Dialog<>();
        dialog.setTitle("Opciones de Exportación");
        dialog.setHeaderText("Seleccione qué datos desea exportar");
        dialog.setResizable(false);

        // Botones
        ButtonType exportButtonType = new ButtonType("Exportar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(exportButtonType, ButtonType.CANCEL);

        // Contenido
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // Radio buttons
        ToggleGroup group = new ToggleGroup();
        RadioButton rbAll = new RadioButton("Exportar todos los registros");
        rbAll.setToggleGroup(group);
        rbAll.setSelected(true);
        rbAll.setStyle("-fx-font-size: 14px;");

        RadioButton rbDateRange = new RadioButton("Exportar por rango de fechas");
        rbDateRange.setToggleGroup(group);
        rbDateRange.setStyle("-fx-font-size: 14px;");

        // Date pickers
        GridPane dateGrid = new GridPane();
        dateGrid.setHgap(10);
        dateGrid.setVgap(10);
        dateGrid.setPadding(new Insets(10, 0, 0, 25));

        Label lblFrom = new Label("Desde:");
        lblFrom.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        DatePicker dpFrom = new DatePicker(LocalDate.now().minusMonths(1));
        dpFrom.setStyle("-fx-font-size: 14px;");
        dpFrom.setDisable(true);

        Label lblTo = new Label("Hasta:");
        lblTo.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        DatePicker dpTo = new DatePicker(LocalDate.now());
        dpTo.setStyle("-fx-font-size: 14px;");
        dpTo.setDisable(true);

        dateGrid.add(lblFrom, 0, 0);
        dateGrid.add(dpFrom, 1, 0);
        dateGrid.add(lblTo, 0, 1);
        dateGrid.add(dpTo, 1, 1);

        // Habilitar/deshabilitar date pickers según selección
        rbAll.selectedProperty().addListener((obs, oldVal, newVal) -> {
            dpFrom.setDisable(newVal);
            dpTo.setDisable(newVal);
        });

        rbDateRange.selectedProperty().addListener((obs, oldVal, newVal) -> {
            dpFrom.setDisable(!newVal);
            dpTo.setDisable(!newVal);
        });

        content.getChildren().addAll(rbAll, rbDateRange, dateGrid);
        dialog.getDialogPane().setContent(content);

        // Validación y resultado
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == exportButtonType) {
                if (rbAll.isSelected()) {
                    return new ExportOptions(true, null, null);
                } else {
                    // Validar fechas
                    LocalDate from = dpFrom.getValue();
                    LocalDate to = dpTo.getValue();

                    if (from == null || to == null) {
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Fechas requeridas");
                        alert.setHeaderText(null);
                        alert.setContentText("Por favor, seleccione ambas fechas (Desde y Hasta)");
                        alert.showAndWait();
                        return null;
                    }

                    if (from.isAfter(to)) {
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Fechas inválidas");
                        alert.setHeaderText(null);
                        alert.setContentText("La fecha 'Desde' no puede ser posterior a la fecha 'Hasta'");
                        alert.showAndWait();
                        return null;
                    }

                    return new ExportOptions(false, from, to);
                }
            }
            return null;
        });

        return dialog.showAndWait();
    }
}

