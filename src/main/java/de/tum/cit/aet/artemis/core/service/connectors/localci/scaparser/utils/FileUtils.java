package de.tum.cit.aet.artemis.core.service.connectors.localci.scaparser.utils;

import java.io.File;
import java.util.Optional;

/**
 * Utility class providing shared functionality for files
 */
public final class FileUtils {

    private FileUtils() {
    }

    /**
     * Returns true if the specified file's size is greater than the specified threshold.
     *
     * @param file            the file to check
     * @param sizeInMegabytes the threshold in mega bytes
     * @return true if the size of the file is larger than the threshold
     */
    public static boolean isFilesizeGreaterThan(File file, long sizeInMegabytes) {
        long sizeInBytes = file.length();
        long sizeInMb = sizeInBytes / (1024 * 1024);
        return sizeInMb > sizeInMegabytes;
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
