package com.example.hello;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NumberMatchingTest {
    @Test
    public void shortNumbersAreMatchedExactly() {
        // "121" must not match every number that merely ends in 121
        assertEquals("(number=? OR number=?)", Favs.logSel("121"));
        assertArrayEquals(new String[]{"121", "121"}, Favs.logArgs("121"));
        assertTrue(Favs.isShort("1800-123"));
        assertArrayEquals(new String[]{"1800-123", "1800123"}, Favs.logArgs("1800-123"));
    }

    @Test
    public void fullNumbersMatchOnLastTenDigits() {
        assertEquals("number LIKE ?", Favs.logSel("+91 98765 43210"));
        assertArrayEquals(new String[]{"%9876543210"}, Favs.logArgs("+91 98765 43210"));
        assertArrayEquals(new String[]{"%9876543210"}, Favs.logArgs("098765 43210"));
    }

    @Test
    public void emptyOrNullNumberDoesNotCrash() {
        assertArrayEquals(new String[]{"", ""}, Favs.logArgs(null));
        assertArrayEquals(new String[]{"", ""}, Favs.logArgs(""));
    }

    @Test
    public void keysUseTheLastTenDigits() {
        assertEquals("9876543210", Favs.key("+91 98765-43210"));
        assertEquals("9876543210", SpamGuard.key("+91 98765-43210"));
        assertEquals("121", Favs.key("121"));
        assertEquals("", Favs.key(null));
    }

    @Test
    public void digitsStripsEverythingElse() {
        assertEquals("12", SpamGuard.digits("a1b2"));
        assertEquals("", SpamGuard.digits(null));
    }
}
