package de.tum.cit.aet.artemis.programming.service.localci.scaparser.utils;

import java.io.File;
import java.util.Optional;

/**
 * Utility class providing shared functionality for files
 */
public final class FileUtils {

    private FileUtils() {
    }

    /**
     * Returns the extension of the specified file.
     *
     * @param file the file
     * @return the file extension.
     */
    public static String getExtension(File file) {
        String filename = file.getName();
        return Optional.of(filename).filter(f -> f.contains(".")).map(f -> f.substring(filename.lastIndexOf(".") + 1)).orElse("");
    }
}
