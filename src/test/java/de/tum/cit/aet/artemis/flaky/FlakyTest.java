package de.tum.cit.aet.artemis.flaky;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class FlakyTest {

    private void assertRandomly() {
        assertThat(Math.random()).as("Test failed randomly").isGreaterThanOrEqualTo(0.2);
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

    @Test
    void failyTest() {
        fail("This test always fails");
    }
}
