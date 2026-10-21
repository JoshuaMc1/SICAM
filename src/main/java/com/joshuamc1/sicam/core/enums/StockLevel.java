package com.joshuamc1.sicam.core.enums;

/** Rangos excluyentes para los indicadores y filtros del inventario. */
public enum StockLevel {
    OUT_OF_STOCK("Sin stock", "#616161"),
    BELOW_MINIMUM("Bajo el mínimo", "#D32F2F"),
    NEAR_MINIMUM("Cerca del mínimo", "#F9A825"),
    NORMAL("Stock suficiente", "#388E3C");

    private final String label;
    private final String color;

    StockLevel(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String getLabel() { return label; }
    public String getColor() { return color; }

    public static StockLevel classify(Double currentStock, Double minimumStock) {
        double current = currentStock == null ? 0 : currentStock;
        double minimum = minimumStock == null ? 0 : Math.max(0, minimumStock);
        if (current <= 0) return OUT_OF_STOCK;
        if (current < minimum) return BELOW_MINIMUM;
        if (minimum > 0 && current <= minimum * 1.2) return NEAR_MINIMUM;
        return NORMAL;
    }
}
