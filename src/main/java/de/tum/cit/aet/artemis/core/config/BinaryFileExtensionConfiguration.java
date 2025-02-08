package de.tum.cit.aet.artemis.core.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;

@Configuration
public class BinaryFileExtensionConfiguration {

    private static final List<String> binaryFileExtensions = List.of(".exe", ".jar", ".dll", ".so", ".class", ".bin", ".msi", ".pyc", ".iso", ".o", ".app");

    public static List<String> getBinaryFileExtensions() {
        return binaryFileExtensions;
    }
}
