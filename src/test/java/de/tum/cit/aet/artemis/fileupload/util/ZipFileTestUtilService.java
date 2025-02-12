package de.tum.cit.aet.artemis.fileupload.util;

import static org.assertj.core.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
        File file = Path.of(zipFile).toFile();

        try (ZipFile zip = new ZipFile(file)) {
            String newPath = zipFile.substring(0, zipFile.length() - 4);

            Path parentFolder = Path.of(newPath);
            Files.createDirectories(parentFolder);

            Enumeration<? extends ZipEntry> zipFileEntries = zip.entries();
            // Process each entry
            while (zipFileEntries.hasMoreElements()) {
                // grab a zip file entry
                ZipEntry entry = zipFileEntries.nextElement();
                String currentEntry = entry.getName();
                Path destFile = parentFolder.resolve(currentEntry);

                if (!destFile.toAbsolutePath().toString().startsWith(parentFolder.toAbsolutePath().toString())) {
                    fail("Bad zip entry");
                }

                Path destinationParent = destFile.getParent();
                // create the parent directory structure if needed
                Files.createDirectories(destinationParent);

                if (!entry.isDirectory()) {
                    FileUtils.copyInputStreamToFile(zip.getInputStream(entry), destFile.toFile());
                }

                if (currentEntry.endsWith(".zip")) {
                    // found a zip file, try to open
                    extractZipFileRecursively(destFile.toAbsolutePath().toString());
                }
            }
            return parentFolder;
        }
    }
}
