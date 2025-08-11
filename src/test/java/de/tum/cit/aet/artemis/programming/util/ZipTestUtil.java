package de.tum.cit.aet.artemis.programming.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

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

    public static void verifyZipContainsGitDirectory(byte[] zipContent) throws Exception {
        boolean foundGitDirectory = false;
        boolean foundOtherFiles = false;
        boolean foundGitConfig = false;
        boolean foundGitHead = false;
        boolean foundGitRefs = false;
        boolean foundGitObjects = false;

        Set<String> repositoryFiles = new java.util.HashSet<>();

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipContent))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.contains(".git/")) {
                    foundGitDirectory = true;
                    if (entryName.endsWith(".git/config")) {
                        foundGitConfig = true;
                        String configContent = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                        assertThat(configContent).containsAnyOf("[core]", "[remote", "repositoryformatversion");
                    }
                    else if (entryName.endsWith(".git/HEAD")) {
                        foundGitHead = true;
                        String headContent = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                        assertThat(headContent).containsAnyOf("ref: refs/heads/", "refs/heads/main", "refs/heads/master");
                    }
                    else if (entryName.contains(".git/refs/")) {
                        foundGitRefs = true;
                    }
                    else if (entryName.contains(".git/objects/")) {
                        foundGitObjects = true;
                    }
                }
                else if (!entryName.endsWith("/")) {
                    foundOtherFiles = true;
                    repositoryFiles.add(entryName);
                }
            }
        }

        assertThat(foundGitDirectory).isTrue();
        assertThat(foundGitConfig).isTrue();
        assertThat(foundGitHead).isTrue();
        assertThat(foundGitRefs).isTrue();
        assertThat(foundGitObjects).isTrue();
        assertThat(foundOtherFiles).isTrue();
        assertThat(repositoryFiles).isNotEmpty();
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
