package de.tum.in.www1.artemis.util;

import org.junit.jupiter.api.function.Executable;
import org.springframework.test.util.ReflectionTestUtils;

public class ConfigUtil {

    /**
     * Runs a test but changes the specified property beforehand and resets it to the previous property afterwards
     *
     * @param resource    the resource with the config attributes that should be changed temporarily
     * @param configName  the name of the attribute
     * @param configValue the value it should be changed to
     * @param test        the test to execute
     * @throws Throwable if the test throws anything
     */
    public static void testWithChangedConfig(Object resource, String configName, Object configValue, Executable test) throws Throwable {
        var oldValue = ReflectionTestUtils.getField(resource, configName);
        ReflectionTestUtils.setField(resource, configName, configValue);

        try {
            test.execute();
        }
        finally {
            ReflectionTestUtils.setField(resource, configName, oldValue);
        }
    }
}
