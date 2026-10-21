package com.joshuamc1.sicam.core.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DateRangeDialog {

    public static class DateRange {
        private final LocalDate from;
        private final LocalDate to;

        public DateRange(LocalDate from, LocalDate to) {
            this.from = from;
            this.to = to;
        }

        public boolean isAllDates() { return from == null && to == null; }

        public LocalDate getFrom() {
            return from;
        }

        public LocalDate getTo() {
            return to;
        }

        public String getFromString() {
            return from == null ? null : from.format(DateTimeFormatter.ISO_DATE);
        }

        public String getToString() {
            return to == null ? null : to.format(DateTimeFormatter.ISO_DATE);
        }
    }

    public static DateRange showDialog(String title) {
        return showDialog(title, false);
    }

    public static DateRange showDialog(String title, boolean allowAllDates) {
        Dialog<DateRange> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("Seleccione el rango de fechas para el reporte");

        // Botones
        ButtonType generateButtonType = new ButtonType("Generar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(generateButtonType, ButtonType.CANCEL);

        // Crear el grid con los campos
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        DatePicker fromDatePicker = new DatePicker(LocalDate.now().minusMonths(1));
        DatePicker toDatePicker = new DatePicker(LocalDate.now());

        fromDatePicker.setPromptText("Fecha desde");
        toDatePicker.setPromptText("Fecha hasta");

        Label lblFrom = new Label("Desde:");
        Label lblTo = new Label("Hasta:");

        lblFrom.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");
        lblTo.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        grid.add(lblFrom, 0, 0);
        grid.add(fromDatePicker, 1, 0);
        grid.add(lblTo, 0, 1);
        grid.add(toDatePicker, 1, 1);

        CheckBox allDates = new CheckBox("Todas las fechas");
        if (allowAllDates) {
            grid.add(allDates, 0, 2, 2, 1);
            fromDatePicker.disableProperty().bind(allDates.selectedProperty());
            toDatePicker.disableProperty().bind(allDates.selectedProperty());
        }
        javafx.scene.Node generate = dialog.getDialogPane().lookupButton(generateButtonType);
        generate.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
            () -> !allDates.isSelected() && (fromDatePicker.getValue() == null || toDatePicker.getValue() == null),
            allDates.selectedProperty(), fromDatePicker.valueProperty(), toDatePicker.valueProperty()));
        dialog.getDialogPane().setContent(grid);

        // Convertir el resultado
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == generateButtonType) {
                if (allowAllDates && allDates.isSelected()) return new DateRange(null, null);
                LocalDate from = fromDatePicker.getValue();
                LocalDate to = toDatePicker.getValue();

                if (from != null && to != null) {
                    // Asegurar que from sea menor o igual a to
                    if (from.isAfter(to)) {
                        LocalDate temp = from;
                        from = to;
                        to = temp;
                    }
                    return new DateRange(from, to);
                }
            }
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }
}

