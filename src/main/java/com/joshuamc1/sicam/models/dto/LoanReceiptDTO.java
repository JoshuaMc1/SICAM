package com.joshuamc1.sicam.models.dto;

import java.util.Date;
import java.util.List;

/**
 * DTO para transferir datos del recibo de préstamo
 * Usado para evitar problemas de conexión de base de datos en hilos separados
 */
public class LoanReceiptDTO {
    private String loanCode;
    private Date createdAt;
    private Date expectedReturnDate;
    private String notes;

    // Usuario
    private String userName;
    private String userUsername;

    // Persona
    private String personFirstName;
    private String personLastName;
    private String personDni;
    private String personPhone;

    // Items
    private List<LoanReceiptItemDTO> items;

    public LoanReceiptDTO() {
    }

    // Getters y Setters
    public String getLoanCode() {
        return loanCode;
    }

    public void setLoanCode(String loanCode) {
        this.loanCode = loanCode;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getExpectedReturnDate() {
        return expectedReturnDate;
    }

    public void setExpectedReturnDate(Date expectedReturnDate) {
        this.expectedReturnDate = expectedReturnDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserUsername() {
        return userUsername;
    }

    public void setUserUsername(String userUsername) {
        this.userUsername = userUsername;
    }

    public String getPersonFirstName() {
        return personFirstName;
    }

    public void setPersonFirstName(String personFirstName) {
        this.personFirstName = personFirstName;
    }

    public String getPersonLastName() {
        return personLastName;
    }

    public void setPersonLastName(String personLastName) {
        this.personLastName = personLastName;
    }

    public String getPersonDni() {
        return personDni;
    }

    public void setPersonDni(String personDni) {
        this.personDni = personDni;
    }

    public String getPersonPhone() {
        return personPhone;
    }

    public void setPersonPhone(String personPhone) {
        this.personPhone = personPhone;
    }

    public List<LoanReceiptItemDTO> getItems() {
        return items;
    }

    public void setItems(List<LoanReceiptItemDTO> items) {
        this.items = items;
    }

    /**
     * DTO para un item del recibo
     */
    public static class LoanReceiptItemDTO {
        private String productName;
        private String productCode;
        private Double quantity;
        private String unit;
        private String itemNotes;

        public LoanReceiptItemDTO() {
        }

        public LoanReceiptItemDTO(String productName, String productCode, Double quantity, String unit, String itemNotes) {
            this.productName = productName;
            this.productCode = productCode;
            this.quantity = quantity;
            this.unit = unit;
            this.itemNotes = itemNotes;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public String getProductCode() {
            return productCode;
        }

        public void setProductCode(String productCode) {
            this.productCode = productCode;
        }

        public Double getQuantity() {
            return quantity;
        }

        public void setQuantity(Double quantity) {
            this.quantity = quantity;
        }

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public String getItemNotes() {
            return itemNotes;
        }

        public void setItemNotes(String itemNotes) {
            this.itemNotes = itemNotes;
        }
    }
}

