package com.joshuamc1.sicam.core;

import java.math.BigDecimal;

/** Regla común para cantidades de los formularios y del servicio de transacciones. */
public final class TransactionQuantity {
    private static final BigDecimal MAXIMUM = new BigDecimal("99999999.99");
    private TransactionQuantity() { }

    public static BigDecimal parse(String text) {
        if (text == null || !text.trim().matches("(?:[0-9]+(?:\\.[0-9]{0,2})?|\\.[0-9]{1,2})")) {
            throw new IllegalArgumentException("La cantidad debe ser un número con un máximo de dos decimales; por ejemplo, 1.25");
        }
        // Validar el texto exacto antes de convertir a double: nunca redondear ni truncar.
        return validate(new BigDecimal(text.trim()));
    }

    public static BigDecimal requirePositive(Double quantity) {
        if (quantity == null || !Double.isFinite(quantity)) {
            throw new IllegalArgumentException("La cantidad debe ser un número mayor que cero");
        }
        return validate(BigDecimal.valueOf(quantity));
    }

    private static BigDecimal validate(BigDecimal value) {
        if (value.signum() <= 0) throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() > 2 || normalized.compareTo(MAXIMUM) > 0) {
            throw new IllegalArgumentException("La cantidad debe tener hasta dos decimales y no superar 99999999.99");
        }
        return normalized;
    }
}
