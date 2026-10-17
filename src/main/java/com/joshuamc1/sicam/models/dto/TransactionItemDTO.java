package com.joshuamc1.sicam.models.dto;

/**
 * DTO para transferir datos de un item de transacción
 * Usado para crear transacciones multi-producto
 */
public class TransactionItemDTO {
    private Integer productId;
    private Double quantity;
    private String notes;
    private String productName; // Para mostrar en UI

    public TransactionItemDTO() {
    }

    public TransactionItemDTO(Integer productId, Double quantity, String notes) {
        this.productId = productId;
        this.quantity = quantity;
        this.notes = notes;
    }

    public TransactionItemDTO(Integer productId, Double quantity, String notes, String productName) {
        this.productId = productId;
        this.quantity = quantity;
        this.notes = notes;
        this.productName = productName;
    }

    // Getters y setters
    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public Double getQuantity() {
        return quantity;
    }

    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    @Override
    public String toString() {
        return "TransactionItemDTO{" +
                "productId=" + productId +
                ", productName='" + productName + '\'' +
                ", quantity=" + quantity +
                ", notes='" + notes + '\'' +
                '}';
    }
}

