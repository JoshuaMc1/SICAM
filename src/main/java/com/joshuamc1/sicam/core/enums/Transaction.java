package com.joshuamc1.sicam.core.enums;

public enum Transaction {
    INGRESOS("in", "Ingreso"),
    EGRESOS("out", "Egreso"),
    PRESTAMOS("loan", "Préstamo");

    private final String dbValue;
    private final String displayName;

    Transaction(String dbValue, String displayName) {
        this.dbValue = dbValue;
        this.displayName = displayName;
    }

    public String getDbValue() {
        return dbValue;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static Transaction fromDbValue(String v) {
        if (v == null) return INGRESOS;
        for (Transaction t : values()) {
            if (t.dbValue.equalsIgnoreCase(v) || t.displayName.equalsIgnoreCase(v)) {
                return t;
            }
        }
        return INGRESOS;
    }
}
