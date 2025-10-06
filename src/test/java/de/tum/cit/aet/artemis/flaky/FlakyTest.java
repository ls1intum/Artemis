package de.tum.cit.aet.artemis.flaky;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FlakyTest {

    private void assertRandomly() {
        assertTrue(Math.random() >= 0.2, String.format("Test failed randomly"));
    }

    @Test
    void flakyTest1() {
        assertRandomly();
    }

    @Test
    void flakyTest2() {
        assertRandomly();
    }

    @Test
    void flakyTest3() {
        assertRandomly();
    }

    @Test
    void flakyTest4() {
        assertRandomly();
    }

    @Test
    void flakyTest5() {
        assertRandomly();
    }

    @Test
    void flakyTest6() {
        assertRandomly();
    }

    @Test
    void flakyTest7() {
        assertRandomly();
    }

    @Test
    void flakyTest8() {
        assertRandomly();
    }

    @Test
    void flakyTest9() {
        assertRandomly();
    }

    @Test
    void flakyTest10() {
        assertRandomly();
    }
}
