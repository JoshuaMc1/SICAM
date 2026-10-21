package com.joshuamc1.sicam.utils;

import java.time.LocalDate;

/** Compara fechas completas: un préstamo que vence hoy todavía está en plazo. */
public final class LoanDueDate {
    private LoanDueDate() { }

    public static boolean isOverdue(String loanStatus, LocalDate expectedReturn, LocalDate today) {
        return "overdue".equals(loanStatus)
            || ("pending".equals(loanStatus) && expectedReturn != null && expectedReturn.isBefore(today));
    }
}
