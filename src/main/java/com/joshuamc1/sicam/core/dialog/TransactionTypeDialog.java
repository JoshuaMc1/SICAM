package com.joshuamc1.sicam.core.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

public class TransactionTypeDialog {

    public enum TransactionType {
        IN("in", "Ingresos"),
        OUT("out", "Egresos"),
        LOAN("loan", "Préstamos");

        private final String code;
        private final String displayName;

        TransactionType(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String getCode() {
            return code;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public static TransactionType showDialog(String title) {
        Dialog<TransactionType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("Seleccione el tipo de transacción");

        ButtonType selectButtonType = new ButtonType("Seleccionar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(selectButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        ComboBox<TransactionType> typeCombo = new ComboBox<>();
        for(TransactionType type: TransactionType.values()) {
            String module=switch(type) { case IN -> "INGRESOS"; case OUT -> "EGRESOS"; case LOAN -> "PRESTAMOS"; };
            if(com.joshuamc1.sicam.core.PermissionHelper.canAccess(module,"READ")) typeCombo.getItems().add(type);
        }
        dialog.getDialogPane().lookupButton(selectButtonType).disableProperty().bind(typeCombo.valueProperty().isNull());
        typeCombo.getSelectionModel().selectFirst();
        typeCombo.setPromptText("Seleccione tipo");
        typeCombo.setPrefWidth(200);

        Label lblType = new Label("Tipo:");
        lblType.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        grid.add(lblType, 0, 0);
        grid.add(typeCombo, 1, 0);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == selectButtonType) {
                return typeCombo.getValue();
            }
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }
}

