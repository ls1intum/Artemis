package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

@ResourceLock("FilePathConverter")
class FilePathConverterTest {

    private static final Logger log = LoggerFactory.getLogger(FilePathConverterTest.class);

    private static Path rootPath;

    @Nullable
    private static Path originalFileUploadPath;

    @BeforeAll
    static void setup() {
        originalFileUploadPath = FilePathConverter.getFileUploadPath();
        // Read the file upload path from the test configuration file to avoid hardcoding
        rootPath = readFileUploadPathFromConfig();
        log.info("Using file upload root path for tests: {}", rootPath);
        FilePathConverter.setFileUploadPath(rootPath);
    }

    @AfterAll
    static void restoreFileUploadPath() {
        // The path is process-wide and the integration test base sets it once per JVM: put back what was there. A null means nothing had published it yet, and the base does that
        // on its first use.
        if (originalFileUploadPath != null) {
            FilePathConverter.setFileUploadPath(originalFileUploadPath);
        }
    }

    @SuppressWarnings("unchecked")
    private static Path readFileUploadPathFromConfig() {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = FilePathConverterTest.class.getClassLoader().getResourceAsStream("config/application-artemis.yml")) {
            Map<String, Object> config = yaml.load(inputStream);
            Map<String, Object> artemis = (Map<String, Object>) config.get("artemis");
            String fileUploadPath = (String) artemis.get("file-upload-path");
            return Path.of(fileUploadPath);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to read file-upload-path from application-local.yml", e);
        }
    }

    @Test
    void testGetMarkdownFilePath() {
        assertThat(FilePathConverter.getMarkdownFilePath()).isEqualTo(rootPath.resolve("markdown"));
    }

    @Test
    void testGetMarkdownFilePathForConversation() {
        long courseId = 42L;
        long conversationId = 99L;
        assertThat(FilePathConverter.getMarkdownFilePathForConversation(courseId, conversationId))
                .isEqualTo(rootPath.resolve("markdown").resolve("communication").resolve("42").resolve("99"));
    }
}
