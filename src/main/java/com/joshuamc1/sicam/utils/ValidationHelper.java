package com.joshuamc1.sicam.utils;

import javafx.beans.binding.BooleanBinding;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.function.Function;

/**
 * Helper centralizado para validaciones de formularios
 */
public class ValidationHelper {

    private static final String ERROR_STYLE = "-fx-text-fill: #d60000; -fx-font-size: 12px;";
    private static final String SUCCESS_STYLE = "-fx-text-fill: #2d8659; -fx-font-size: 12px;";

    /**
     * Agrega un label de helper text "Obligatorio" debajo de un campo
     */
    public static Label addRequiredFieldHelper(VBox container) {
        Label helper = new Label("Obligatorio");
        helper.setStyle(ERROR_STYLE);
        helper.setVisible(true);
        container.getChildren().add(helper);
        return helper;
    }

    /**
     * Agrega un label de helper text con mensaje personalizado
     */
    public static Label addFieldHelper(VBox container, String message, boolean isError) {
        Label helper = new Label(message);
        helper.setStyle(isError ? ERROR_STYLE : SUCCESS_STYLE);
        helper.setVisible(true);
        container.getChildren().add(helper);
        return helper;
    }

    /**
     * Crea un TextFormatter que solo permite números enteros positivos
     */
    public static TextFormatter<String> getNumericFormatter() {
        return new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.isEmpty() || newText.matches("\\d+")) {
                return change;
            }
            return null;
        });
    }

    /**
     * Crea un TextFormatter que solo permite números decimales positivos
     */
    public static TextFormatter<String> getDecimalFormatter() {
        return new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.isEmpty() || newText.matches("\\d*\\.?\\d*")) {
                return change;
            }
            return null;
        });
    }

    /**
     * Valida que un campo sea único en la base de datos
     * Muestra mensaje de error sin cerrar el diálogo
     */
    public static void validateUniqueField(TextField field,
                                          Function<String, Boolean> existsCheck,
                                          Label helperLabel,
                                          String errorMessage) {
        field.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused && !field.getText().trim().isEmpty()) {
                String value = field.getText().trim();
                if (existsCheck.apply(value)) {
                    helperLabel.setText(errorMessage);
                    helperLabel.setStyle(ERROR_STYLE);
                    helperLabel.setVisible(true);
                } else {
                    helperLabel.setText("");
                    helperLabel.setVisible(false);
                }
            }
        });
    }

    /**
     * Valida que un campo sea único en la base de datos (versión con ID para edición)
     * Excluye el registro actual de la validación
     */
    public static void validateUniqueFieldWithId(TextField field,
                                                 Integer currentId,
                                                 Function<String, Integer> getIdByValue,
                                                 Label helperLabel,
                                                 String errorMessage) {
        field.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused && !field.getText().trim().isEmpty()) {
                String value = field.getText().trim();
                Integer foundId = getIdByValue.apply(value);

                // Si existe y no es el registro actual, es duplicado
                if (foundId != null && !foundId.equals(currentId)) {
                    helperLabel.setText(errorMessage);
                    helperLabel.setStyle(ERROR_STYLE);
                    helperLabel.setVisible(true);
                } else {
                    helperLabel.setText("");
                    helperLabel.setVisible(false);
                }
            }
        });
    }

    /**
     * Crea un BooleanBinding que indica si un TextField está vacío
     */
    public static BooleanBinding isTextFieldEmpty(TextField field) {
        return field.textProperty().isEmpty()
                .or(field.textProperty().isEqualTo(""))
                .or(field.textProperty().isEqualTo("  ").or(field.textProperty().isEqualTo("   ")));
    }

    /**
     * Crea un BooleanBinding que indica si un ComboBox no tiene selección
     */
    public static BooleanBinding isComboBoxEmpty(ComboBox<?> combo) {
        return combo.valueProperty().isNull();
    }

    /**
     * Crea un BooleanBinding que indica si un ChoiceBox no tiene selección
     */
    public static BooleanBinding isChoiceBoxEmpty(ChoiceBox<?> choice) {
        return choice.valueProperty().isNull();
    }

    /**
     * Muestra un mensaje de error temporal en un label
     */
    public static void showError(Label label, String message) {
        label.setText(message);
        label.setStyle(ERROR_STYLE);
        label.setVisible(true);
    }

    /**
     * Muestra un mensaje de éxito temporal en un label
     */
    public static void showSuccess(Label label, String message) {
        label.setText(message);
        label.setStyle(SUCCESS_STYLE);
        label.setVisible(true);
    }

    /**
     * Oculta un label de mensaje
     */
    public static void hideMessage(Label label) {
        label.setText("");
        label.setVisible(false);
    }

    /**
     * Valida que un campo numérico no esté vacío y sea mayor que cero
     */
    public static BooleanBinding isNumericFieldInvalid(TextField field) {
        return field.textProperty().isEmpty()
                .or(field.textProperty().isEqualTo("0"))
                .or(field.textProperty().isEqualTo(""));
    }

    /**
     * Crea un TextFormatter para teléfono con formato 0000-0000
     */
    public static TextFormatter<String> getPhoneFormatter() {
        return new TextFormatter<>(change -> {
            String newText = change.getControlNewText();

            // Permitir solo dígitos y guiones
            if (!newText.matches("[\\d-]*")) {
                return null;
            }

            // Remover guiones para contar dígitos
            String digitsOnly = newText.replaceAll("-", "");
            if (digitsOnly.length() > 8) {
                return null;
            }

            // Auto-agregar guión después de 4 dígitos
            if (digitsOnly.length() == 4 && !newText.contains("-") && change.isAdded()) {
                change.setText(change.getText() + "-");
                change.setCaretPosition(change.getCaretPosition() + 1);
                change.setAnchor(change.getAnchor() + 1);
            }

            return change;
        });
    }

    /**
     * Formatea un número de teléfono a 0000-0000
     */
    public static String formatPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return "";
        }
        String digitsOnly = phone.replaceAll("[^\\d]", "");
        if (digitsOnly.length() == 8) {
            return digitsOnly.substring(0, 4) + "-" + digitsOnly.substring(4);
        }
        return phone;
    }

    /**
     * Valida formato de DNI (solo números)
     */
    public static TextFormatter<String> getDniFormatter() {
        return new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.isEmpty() || newText.matches("\\d{0,13}")) {
                return change;
            }
            return null;
        });
    }
}

