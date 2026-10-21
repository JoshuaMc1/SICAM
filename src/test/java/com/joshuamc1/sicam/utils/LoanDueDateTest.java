package com.joshuamc1.sicam.utils;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

public class LoanDueDateTest {
    @Test
    public void expiresOnlyAfterTheDueDate() {
        LocalDate today = LocalDate.of(2026, 10, 20);
        assertTrue(LoanDueDate.isOverdue("pending", today.minusDays(1), today));
        assertFalse(LoanDueDate.isOverdue("pending", today, today));
        assertFalse(LoanDueDate.isOverdue("pending", today.plusDays(1), today));
        assertFalse(LoanDueDate.isOverdue("pending", null, today));
    }

    @Test
    public void respectsClosedAndExplicitOverdueStates() {
        LocalDate today = LocalDate.of(2026, 10, 20);
        for (String status : new String[]{"returned", "paid", "replaced"}) {
            assertFalse(LoanDueDate.isOverdue(status, today.minusDays(5), today));
        }
        assertTrue(LoanDueDate.isOverdue("overdue", null, today));
    }
}
