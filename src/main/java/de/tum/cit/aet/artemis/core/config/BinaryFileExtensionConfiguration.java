package de.tum.cit.aet.artemis.core.config;

import java.util.List;

/**
 * Provides a list of binary file extensions which can be used to filter returned files in the business logic of
 * the application.
 */
public class BinaryFileExtensionConfiguration {

    private static final List<String> binaryFileExtensions = List.of(".exe", ".jar", ".dll", ".so", ".class", ".bin", ".msi", ".pyc", ".iso", ".o", ".app");

    public static List<String> getBinaryFileExtensions() {
        return binaryFileExtensions;
    }
}
