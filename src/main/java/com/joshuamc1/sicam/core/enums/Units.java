package com.joshuamc1.sicam.core.enums;

import java.util.Arrays;

public enum Units {
    UNIDAD("unidad", "Unidad"),
    KILOGRAMO("kg", "Kilogramo"),
    METRO("m", "Metro"),
    LITRO("l", "Litro"),
    PAQUETE("paquete", "Paquete"),
    SACO("saco", "Saco"),
    CAJA("caja", "Caja"),
    ROLLO("rollo", "Rollo"),
    OTROS("otros", "Otros");

    private final String dbValue;
    private final String displayName;

    Units(String dbValue, String displayName) {
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

    public static Units fromDbValue(String v) {
        if (v == null) return OTROS;
        return Arrays.stream(values())
                .filter(u -> u.dbValue.equalsIgnoreCase(v) || u.displayName.equalsIgnoreCase(v))
                .findFirst()
                .orElse(OTROS);
    }
}

