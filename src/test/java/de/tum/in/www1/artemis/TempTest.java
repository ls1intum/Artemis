package de.tum.in.www1.artemis;

import org.junit.jupiter.api.Test;

public class TempTest {

    @Test
    void failingTest() {
        throw new RuntimeException("This test should fail");
    }

    @Test
    void passingTest() {
        // This test should pass
    }
}
