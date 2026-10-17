package com.joshuamc1.sicam.core.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

public class MonthYearDialog {

    public static class MonthYearSelection {
        private final String yearMonth;

        public MonthYearSelection(String yearMonth) {
            this.yearMonth = yearMonth;
        }

        public String getYearMonth() {
            return yearMonth;
        }

        public String getFormatted() {
            YearMonth ym = YearMonth.parse(yearMonth);
            return ym.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        }
    }

    public static MonthYearSelection showDialog(String title) {
        Dialog<MonthYearSelection> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("Seleccione mes y año para el dashboard");

        ButtonType selectButtonType = new ButtonType("Generar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(selectButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // ComboBox para mes
        ComboBox<Integer> monthCombo = new ComboBox<>();
        monthCombo.getItems().addAll(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        monthCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Integer month, boolean empty) {
                super.updateItem(month, empty);
                if (empty || month == null) {
                    setText(null);
                } else {
                    setText(LocalDate.of(2000, month, 1).format(DateTimeFormatter.ofPattern("MMMM")));
                }
            }
        });
        monthCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Integer month, boolean empty) {
                super.updateItem(month, empty);
                if (empty || month == null) {
                    setText(null);
                } else {
                    setText(LocalDate.of(2000, month, 1).format(DateTimeFormatter.ofPattern("MMMM")));
                }
            }
        });
        monthCombo.setValue(LocalDate.now().getMonthValue());
        monthCombo.setPrefWidth(150);

        // ComboBox para año
        ComboBox<Integer> yearCombo = new ComboBox<>();
        int currentYear = LocalDate.now().getYear();
        for (int year = currentYear - 5; year <= currentYear + 1; year++) {
            yearCombo.getItems().add(year);
        }
        yearCombo.setValue(currentYear);
        yearCombo.setPrefWidth(100);

        Label lblMonth = new Label("Mes:");
        lblMonth.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        Label lblYear = new Label("Año:");
        lblYear.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        grid.add(lblMonth, 0, 0);
        grid.add(monthCombo, 1, 0);
        grid.add(lblYear, 0, 1);
        grid.add(yearCombo, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == selectButtonType) {
                Integer month = monthCombo.getValue();
                Integer year = yearCombo.getValue();

                if (month != null && year != null) {
                    String yearMonth = String.format("%04d-%02d", year, month);
                    return new MonthYearSelection(yearMonth);
                }
            }
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }
}

