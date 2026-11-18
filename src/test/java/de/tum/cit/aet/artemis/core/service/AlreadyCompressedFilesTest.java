package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link AlreadyCompressedFiles}.
 */
class AlreadyCompressedFilesTest {

    /**
     * The archives Artemis hands out nest compressed files by design, and a course archive carries lecture attachments
     * that are ZIP containers themselves. Deflating those again spends CPU on the whole payload for almost nothing.
     */
    @ParameterizedTest
    @ValueSource(strings = { "repository.zip", "library.jar", "sources.tar.gz", "slides.pdf", "diagram.png", "recording.mp4", "handout.docx", "grades.xlsx", "slides.pptx",
            "notes.odt", "SHOUTING.ZIP", "Slides.PDF" })
    void shouldStoreContentThatCarriesItsOwnCompression(String filename) {
        assertThat(AlreadyCompressedFiles.matches(Path.of(filename))).as("%s must be stored rather than deflated again", filename).isTrue();
    }

    /**
     * Everything else keeps being deflated, which is where the compression of an archive actually comes from.
     */
    @ParameterizedTest
    @ValueSource(strings = { "Main.java", "report.csv", "build.gradle", "README.md", "gradlew", "archive.zip.part", "notzip" })
    void shouldDeflateEverythingElse(String filename) {
        assertThat(AlreadyCompressedFiles.matches(Path.of(filename))).as("%s must still be deflated", filename).isFalse();
    }
}
