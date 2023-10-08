package de.tum.in.www1.artemis.util;

import static org.assertj.core.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

/**
 * Zip file service containing utility methods
 * used only for testing purposes.
 */
@Service
public class ZipFileTestUtilService {

    /**
     * Extracts a zip file recursively.
     *
     * @param zipFile The path to the zip file
     * @throws IOException if something goes wrong
     */
    public void extractZipFileRecursively(String zipFile) throws IOException {
        File file = new File(zipFile);

        try (ZipFile zip = new ZipFile(file)) {
            String newPath = zipFile.substring(0, zipFile.length() - 4);

            new File(newPath).mkdir();
            Enumeration<? extends ZipEntry> zipFileEntries = zip.entries();

            // Process each entry
            while (zipFileEntries.hasMoreElements()) {
                // grab a zip file entry
                ZipEntry entry = zipFileEntries.nextElement();
                String currentEntry = entry.getName();
                File destFile = new File(newPath, currentEntry);

                if (!destFile.getCanonicalPath().startsWith(newPath)) {
                    fail("Bad zip entry");
                }

                if (!entry.isDirectory()) {
                    FileUtils.copyInputStreamToFile(zip.getInputStream(entry), destFile);
                }

                if (currentEntry.endsWith(".zip")) {
                    // found a zip file, try to open
                    extractZipFileRecursively(destFile.getAbsolutePath());
                }
            }
        }
    }
}
