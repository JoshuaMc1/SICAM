package com.joshuamc1.sicam.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransactionQuantityTest {
    @Test void acceptsWholeNumbersAndUpToTwoDecimalPlaces() {
        for (String input : new String[]{"1", "1.2", "1.25", "0.01", ".5", "1.", "99999999.99"}) {
            assertTrue(TransactionQuantity.parse(input).signum() > 0, input);
        }
    }
    @Test void rejectsThirdDecimalInsteadOfSilentlyRounding() {
        for (String input : new String[]{"1.001", "1.009", "0.001", "1.250", "99999999.991"}) {
            var error = assertThrows(IllegalArgumentException.class, () -> TransactionQuantity.parse(input), input);
            assertTrue(error.getMessage().contains("decimales"));
        }
    }
    @Test void rejectsEmptyNonNumericNegativeZeroAndOverflow() {
        for (String input : new String[]{"", ".", "abc", "NaN", "Infinity", "1e3", "-1", "0", "0.00", "100000000"})
            assertThrows(IllegalArgumentException.class, () -> TransactionQuantity.parse(input), input);
        assertThrows(IllegalArgumentException.class, () -> TransactionQuantity.parse(null));
    }
    @Test void serviceAndFormUseTheSameNumericLimits() {
        for (double value : new double[]{0.01, 1, 1.25, 99999999.99})
            assertEquals(0, TransactionQuantity.parse(java.math.BigDecimal.valueOf(value).toPlainString()).compareTo(TransactionQuantity.requirePositive(value)));
        for (double value : new double[]{-1, 0, Double.NaN, Double.POSITIVE_INFINITY, 1.001, 100000000})
            assertThrows(IllegalArgumentException.class, () -> TransactionQuantity.requirePositive(value));
        assertThrows(IllegalArgumentException.class, () -> TransactionQuantity.requirePositive(null));
    }
}
