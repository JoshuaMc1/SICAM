package com.joshuamc1.sicam.core.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class StockLevelTest {
    @Test
    public void classifiesThresholdBoundaries() {
        assertEquals(StockLevel.OUT_OF_STOCK, StockLevel.classify(0.0, 10.0));
        assertEquals(StockLevel.OUT_OF_STOCK, StockLevel.classify(-1.0, 10.0));
        assertEquals(StockLevel.BELOW_MINIMUM, StockLevel.classify(9.99, 10.0));
        assertEquals(StockLevel.NEAR_MINIMUM, StockLevel.classify(10.0, 10.0));
        assertEquals(StockLevel.NEAR_MINIMUM, StockLevel.classify(12.0, 10.0));
        assertEquals(StockLevel.NORMAL, StockLevel.classify(12.01, 10.0));
    }

    @Test
    public void handlesMissingAndZeroMinimum() {
        assertEquals(StockLevel.NORMAL, StockLevel.classify(1.0, 0.0));
        assertEquals(StockLevel.OUT_OF_STOCK, StockLevel.classify(0.0, 0.0));
        assertEquals(StockLevel.OUT_OF_STOCK, StockLevel.classify(null, 10.0));
        assertEquals(StockLevel.NORMAL, StockLevel.classify(1.0, null));
    }

    @Test
    public void supportsFractionalQuantities() {
        assertEquals(StockLevel.BELOW_MINIMUM, StockLevel.classify(0.49, 0.5));
        assertEquals(StockLevel.NEAR_MINIMUM, StockLevel.classify(0.6, 0.5));
        assertEquals(StockLevel.NORMAL, StockLevel.classify(0.61, 0.5));
    }
}
