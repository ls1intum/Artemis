package de.tum.cit.aet.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import de.tum.in.test.api.BlacklistPath;
import de.tum.in.test.api.StrictTimeout;
import de.tum.in.test.api.WhitelistPath;
import de.tum.in.test.api.jupiter.Public;

@Public
@WhitelistPath("target")
@BlacklistPath("target/test-classes")
class ScoreCalculatorStructureTest {

    @Test
    @StrictTimeout(1)
    void testPublicApi() throws NoSuchMethodException {
        Method method = ScoreCalculator.class.getDeclaredMethod("countPassing", int[].class);
        assertEquals(int.class, method.getReturnType());
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
    }
}
