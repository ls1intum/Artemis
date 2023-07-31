package de.tum.in.www1.artemis;

import org.junit.jupiter.api.Test;

public class FailingTest {

    @Test
    void failingTest() {
        throw new RuntimeException("This test should fail!");
    }
}
