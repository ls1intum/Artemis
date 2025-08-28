package de.tum.cit.aet.artemis.core.util;

public final class NativeImageUtil {

    private NativeImageUtil() {
    }

    /**
     * Detects if running in GraalVM native image mode.
     */
    public static boolean isNativeImage() {
        return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    }
}
