package de.tum.cit.aet.artemis.programming.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.springframework.core.io.InputStreamResource;

public final class ZipTestUtil {

    private ZipTestUtil() {
    }

    public static byte[] createTestZipFile(Map<String, String> files) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zos.putNextEntry(zipEntry);
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    public static InputStreamResource createMockZipResource(byte[] data, String filename) {
        return new InputStreamResource(new ByteArrayInputStream(data)) {

            @Override
            public String getFilename() {
                return filename;
            }

            @Override
            public long contentLength() {
                return data.length;
            }
        };
    }

    /**
     * Extracts every entry of the given zip into the target directory, preserving the directory structure.
     *
     * @param zipContent the zip file content
     * @param targetDir  the directory to extract into; must already exist
     */
    public static void extractZip(byte[] zipContent, Path targetDir) throws IOException {
        Path normalizedTarget = targetDir.toAbsolutePath().normalize();
        try (var zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipContent))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path resolved = normalizedTarget.resolve(entry.getName()).normalize();
                assertThat(resolved).as("zip entry must stay inside the target directory").startsWithRaw(normalizedTarget);
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                }
                else {
                    Files.createDirectories(resolved.getParent());
                    FileUtils.copyInputStreamToFile(CloseShieldInputStream.wrap(zipInputStream), resolved.toFile());
                }
            }
        }
    }

    /**
     * Lists the names of all entries of a zip file, so that a test can assert on what an archive does and does not contain.
     *
     * @param zipContent the zip file content
     * @return the names of all entries, in the order they appear in the archive
     */
    public static List<String> listEntryNames(byte[] zipContent) throws IOException {
        List<String> entryNames = new ArrayList<>();
        try (var zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipContent))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }
        }
        return entryNames;
    }

    /**
     * Reads the first entry whose name ends with the given suffix as a UTF-8 string.
     *
     * @param zipContent  the zip file content
     * @param entrySuffix the suffix identifying the entry, e.g. {@code .git/config}
     * @return the entry content, or null if no entry matches
     */
    public static String readEntryAsString(byte[] zipContent, String entrySuffix) throws IOException {
        try (var zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipContent))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().endsWith(entrySuffix)) {
                    return new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    public static void verifyZipContainsGitDirectory(byte[] zipContent) throws Exception {
        GitVerificationResult result = processZipEntries(zipContent);

        assertThat(result.foundGitDirectory).isTrue();
        assertThat(result.foundGitConfig).isTrue();
        assertThat(result.foundGitHead).isTrue();
        assertThat(result.foundGitRefs).isTrue();
        assertThat(result.foundGitObjects).isTrue();
        assertThat(result.foundOtherFiles).isTrue();
        assertThat(result.repositoryFiles).isNotEmpty();
    }

    private static GitVerificationResult processZipEntries(byte[] zipContent) throws Exception {
        GitVerificationResult result = new GitVerificationResult();

        try (var zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipContent))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                processZipEntry(entry, zipInputStream, result);
            }
        }

        return result;
    }

    public static void verifyZipDoesNotContainGitDirectory(byte[] zipContent) throws Exception {
        GitVerificationResult result = processZipEntries(zipContent);

        assertThat(result.foundGitDirectory).isFalse();
        // Still expect regular files to be present
        assertThat(result.foundOtherFiles).isTrue();
        assertThat(result.repositoryFiles).isNotEmpty();
    }

    private static void processZipEntry(ZipEntry entry, ZipInputStream zipInputStream, GitVerificationResult result) throws Exception {
        String entryName = entry.getName();
        if (entryName.contains(".git/")) {
            result.foundGitDirectory = true;
            processGitEntry(entryName, zipInputStream, result);
        }
        else if (!entryName.endsWith("/")) {
            result.foundOtherFiles = true;
            result.repositoryFiles.add(entryName);
        }
    }

    private static void processGitEntry(String entryName, ZipInputStream zipInputStream, GitVerificationResult result) throws Exception {
        switch (entryName) {
            case String s when s.endsWith(".git/config") -> {
                result.foundGitConfig = true;
                String configContent = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                assertThat(configContent).containsAnyOf("[core]", "[remote", "repositoryformatversion");
            }
            case String s when s.endsWith(".git/HEAD") -> {
                result.foundGitHead = true;
                String headContent = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                assertThat(headContent).containsAnyOf("ref: refs/heads/", "refs/heads/main", "refs/heads/master");
            }
            case String s when s.contains(".git/refs/") -> result.foundGitRefs = true;
            case String s when s.contains(".git/objects/") -> result.foundGitObjects = true;
            default -> {
                /* No specific git entry type found */ }
        }
    }

    /**
     * Extracts the content of the first JSON file found within a given ZIP archive.
     * <p>
     * This method is primarily used in integration tests to verify exported exercise data.
     * For example, it reads the "Exercise-Details-XXX.json" file from an instructor export of
     * a programming exercise and returns its contents as a UTF-8 string for further validation.
     *
     * @param zipBytes the ZIP file content as a byte array, typically received from an export REST endpoint
     * @return the content of the first JSON file found within the ZIP as a UTF-8 string
     * @throws IOException if no JSON file is found inside the ZIP or if an I/O error occurs while reading
     */
    public static String extractExerciseJsonFromZip(byte[] zipBytes) throws IOException {
        try (var zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".json")) {
                    return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException("No JSON file found inside exported ZIP");
    }

    private static class GitVerificationResult {

        boolean foundGitDirectory = false;

        boolean foundOtherFiles = false;

        boolean foundGitConfig = false;

        boolean foundGitHead = false;

        boolean foundGitRefs = false;

        boolean foundGitObjects = false;

        final Set<String> repositoryFiles = new java.util.HashSet<>();
    }

    public static void verifyZipStructureAndContent(byte[] zipContent) throws Exception {
        boolean foundFiles = false;
        int fileCount = 0;
        Set<String> repositoryFiles = new java.util.HashSet<>();

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipContent))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (!entry.isDirectory()) {
                    foundFiles = true;
                    fileCount++;
                    repositoryFiles.add(entryName);
                    byte[] fileContent = zipInputStream.readAllBytes();
                    assertThat(fileContent).isNotNull();
                    if (entryName.endsWith(".java") || entryName.endsWith(".md") || entryName.endsWith(".xml")) {
                        String textContent = new String(fileContent, StandardCharsets.UTF_8);
                        assertThat(textContent).isNotBlank();
                    }
                }
            }
        }

        assertThat(foundFiles).isTrue();
        assertThat(fileCount).isGreaterThan(0);
        assertThat(repositoryFiles).isNotEmpty();
        assertThat(zipContent.length).isGreaterThan(100);
        for (String filename : repositoryFiles) {
            assertThat(filename).isNotBlank();
            assertThat(filename).doesNotContain("\0");
        }
    }
}
