package com.joshuamc1.sicam.utils;

import com.joshuamc1.sicam.models.Transaction;
import javafx.scene.control.Label;

/** Presentación común del estado del registro de una transacción. */
public final class TransactionStatusBadge {
    private TransactionStatusBadge() {
    }

    public static Label create(Transaction transaction) {
        String text;
        String color;
        if (transaction.isInvalidated()) {
            text = "Invalidado";
            color = "#F44336";
        } else if (transaction.isActive()) {
            text = "Activo";
            color = "#4CAF50";
        } else {
            text = "Inactivo";
            color = "#9E9E9E";
        }
        Label badge = new Label(text);
        badge.setStyle("-fx-background-color: " + color
            + "; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 5;");
        return badge;
    }
}
