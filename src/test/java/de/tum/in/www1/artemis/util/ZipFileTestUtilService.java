package de.tum.in.www1.artemis.util;

import static org.assertj.core.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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
     * @return path to the directory containing the exported files
     * @throws IOException if something goes wrong
     */
    public Path extractZipFileRecursively(String zipFile) throws IOException {
        File file = new File(zipFile);

        try (ZipFile zip = new ZipFile(file)) {
            String newPath = zipFile.substring(0, zipFile.length() - 4);

            File parentFolder = new File(newPath);
            parentFolder.mkdir();

            Enumeration<? extends ZipEntry> zipFileEntries = zip.entries();
            // Process each entry
            while (zipFileEntries.hasMoreElements()) {
                // grab a zip file entry
                ZipEntry entry = zipFileEntries.nextElement();
                String currentEntry = entry.getName();
                File destFile = new File(parentFolder, currentEntry);

                if (!destFile.getCanonicalPath().startsWith(parentFolder.getCanonicalPath())) {
                    fail("Bad zip entry");
                }

                File destinationParent = destFile.getParentFile();
                // create the parent directory structure if needed
                destinationParent.mkdirs();

                if (!entry.isDirectory()) {
                    FileUtils.copyInputStreamToFile(zip.getInputStream(entry), destFile);
                }

                if (currentEntry.endsWith(".zip")) {
                    // found a zip file, try to open
                    extractZipFileRecursively(destFile.getAbsolutePath());
                }
            }
            return parentFolder.toPath();
        }
    }
}
