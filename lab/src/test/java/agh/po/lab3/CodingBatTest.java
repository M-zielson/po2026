package agh.po.lab3;

import static org.junit.Assert.*;

public class CodingBatTest {
    CodingBat cb = new CodingBat();

    @org.junit.Test
    public void sleepIn() {
        assertTrue(cb.sleepIn(false, false));
        assertFalse(cb.sleepIn(true, false));
        assertTrue(cb.sleepIn(false, true));
    }

    @org.junit.Test
    public void monkeyTrouble() {
        assertTrue(cb.monkeyTrouble(true, true));
        assertTrue(cb.monkeyTrouble(false, false));
        assertFalse(cb.monkeyTrouble(true, false));
    }

    @org.junit.Test
    public void countEvens() {
        assertEquals(3, cb.countEvens(new int[]{2, 1, 2, 3, 4}));
        assertEquals(5, cb.countEvens(new int[]{2, 2, 0, 4, 6}));
        assertEquals(0, cb.countEvens(new int[]{1, 3, 5}));
    }

    @org.junit.Test
    public void makeOutWord() {
        assertEquals("<<Yay>>", cb.makeOutWord("<<>>", "Yay"));
        assertEquals("<<WooHoo>>", cb.makeOutWord("<<>>", "WooHoo"));
        assertEquals("[[wneka]]", cb.makeOutWord("[[]]", "wneka"));
    }
}