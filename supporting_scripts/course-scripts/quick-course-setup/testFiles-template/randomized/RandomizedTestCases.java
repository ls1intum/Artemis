package ${packageName};

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.text.*;
import java.util.*;

import static de.tum.in.test.api.util.ReflectionTestUtils.*;

import de.tum.in.test.api.BlacklistPath;
import de.tum.in.test.api.PathType;
import de.tum.in.test.api.StrictTimeout;
import de.tum.in.test.api.WhitelistPath;
import de.tum.in.test.api.jupiter.Public;
@Public
@WhitelistPath("target") // mainly for Artemis
@BlacklistPath("target/test-classes") // prevent access to test-related classes and resources
class RandomizedTestCases {

    @Test
    @StrictTimeout(1)
    void testCase1() {
        Random random = new Random();
        boolean shouldFail = random.nextBoolean();

        if (shouldFail) {
            fail(String.format("Different error: %s, %s", System.currentTimeMillis(), random.nextInt(Integer.MAX_VALUE)));
        }
    }

    @Test
    @StrictTimeout(1)
    void testCase2() {
        Random random = new Random();
        boolean shouldFail = random.nextBoolean();

        if (shouldFail) {
            fail(String.format("Different error: %s, %s", System.currentTimeMillis(), random.nextInt(Integer.MAX_VALUE)));
        }
    }

    @Test
    @StrictTimeout(1)
    void testCase3() {
        Random random = new Random();
        boolean shouldFail = random.nextBoolean();

        if (shouldFail) {
            fail(String.format("Different error: %s, %s", System.currentTimeMillis(), random.nextInt(Integer.MAX_VALUE)));
        }
    }

    @Test
    @StrictTimeout(1)
    void testCase4() {
        Random random = new Random();
        boolean shouldFail = random.nextBoolean();

        if (shouldFail) {
            fail(String.format("Different error: %s, %s", System.currentTimeMillis(), random.nextInt(Integer.MAX_VALUE)));
        }
    }

    @Test
    @StrictTimeout(1)
    void testCase5() {
        Random random = new Random();
        boolean shouldFail = random.nextBoolean();

        if (shouldFail) {
            fail(String.format("Different error: %s, %s", System.currentTimeMillis(), random.nextInt(Integer.MAX_VALUE)));
        }
    }

    @Test
    @StrictTimeout(1)
    void testCase6() {
        Random random = new Random();
        boolean shouldFail = random.nextBoolean();

        if (shouldFail) {
            fail(String.format("Different error: %s, %s", System.currentTimeMillis(), random.nextInt(Integer.MAX_VALUE)));
        }
    }

    @Test
    @StrictTimeout(1)
    void testCase7() {
        Random random = new Random();
        boolean shouldFail = random.nextBoolean();

        if (shouldFail) {
            fail(String.format("Different error: %s, %s", System.currentTimeMillis(), random.nextInt(Integer.MAX_VALUE)));
        }
    }

    @Test
    @StrictTimeout(1)
    void testCase8() {
        Random random = new Random();
        boolean shouldFail = random.nextBoolean();

        if (shouldFail) {
            fail(String.format("Different error: %s, %s", System.currentTimeMillis(), random.nextInt(Integer.MAX_VALUE)));
        }
    }

    @Test
    @StrictTimeout(1)
    void testCase9() {
        Random random = new Random();
        boolean shouldFail = random.nextBoolean();

        if (shouldFail) {
            fail(String.format("Different error: %s, %s", System.currentTimeMillis(), random.nextInt(Integer.MAX_VALUE)));
        }
    }

    @Test
    @StrictTimeout(1)
    void testCase10() {
        Random random = new Random();
        boolean shouldFail = random.nextBoolean();

        if (shouldFail) {
            fail(String.format("Different error: %s, %s", System.currentTimeMillis(), random.nextInt(Integer.MAX_VALUE)));
        }
    }
}
