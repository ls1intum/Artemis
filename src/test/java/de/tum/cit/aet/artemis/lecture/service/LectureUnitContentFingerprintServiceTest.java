package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;

class LectureUnitContentFingerprintServiceTest {

    private LectureUnitContentFingerprintService fingerprintService;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        fingerprintService = new LectureUnitContentFingerprintService();
    }

    private AttachmentVideoUnit unitWithVideo(String videoSource) {
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setVideoSource(videoSource);
        return unit;
    }

    private AttachmentVideoUnit unitWithPdf(String filename) {
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setId(42L);
        Attachment attachment = new Attachment();
        attachment.setAttachmentType(AttachmentType.FILE);
        attachment.setLink(filename);
        attachment.setAttachmentVideoUnit(unit);
        unit.setAttachment(attachment);
        return unit;
    }

    @Test
    void fingerprintIsDeterministicAndVersioned() {
        AttachmentVideoUnit unit = unitWithVideo("https://video.example/stream");

        String first = fingerprintService.computeFingerprint(unit);
        String second = fingerprintService.computeFingerprint(unit);

        assertThat(first).startsWith("v1:").isEqualTo(second);
    }

    @Test
    void fingerprintChangesWithTheVideoSource() {
        String first = fingerprintService.computeFingerprint(unitWithVideo("https://video.example/a"));
        String second = fingerprintService.computeFingerprint(unitWithVideo("https://video.example/b"));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void fingerprintCoversThePdfBytes() throws Exception {
        AttachmentVideoUnit unit = unitWithPdf("slides.pdf");
        Path pdfPath = tempDir.resolve("42").resolve("slides.pdf");
        FileUtils.writeStringToFile(pdfPath.toFile(), "original content", StandardCharsets.UTF_8);

        try (MockedStatic<FilePathConverter> filePathConverter = mockStatic(FilePathConverter.class)) {
            filePathConverter.when(FilePathConverter::getAttachmentVideoUnitFileSystemPath).thenReturn(tempDir);

            String original = fingerprintService.computeFingerprint(unit);
            String unchanged = fingerprintService.computeFingerprint(unit);
            FileUtils.writeStringToFile(pdfPath.toFile(), "changed content", StandardCharsets.UTF_8);
            String changed = fingerprintService.computeFingerprint(unit);

            assertThat(original).isEqualTo(unchanged).isNotEqualTo(changed);
        }
    }

    @Test
    void unreadableAttachmentFileFailsInsteadOfFingerprintingWithoutIt() {
        AttachmentVideoUnit unit = unitWithPdf("missing.pdf");

        try (MockedStatic<FilePathConverter> filePathConverter = mockStatic(FilePathConverter.class)) {
            filePathConverter.when(FilePathConverter::getAttachmentVideoUnitFileSystemPath).thenReturn(tempDir);

            assertThatThrownBy(() -> fingerprintService.computeFingerprint(unit)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void externalPdfLinkIsFingerprintedLikeAVideoOnlyUnit() {
        AttachmentVideoUnit unit = unitWithVideo("https://video.example/stream");
        unit.setId(42L);
        Attachment attachment = new Attachment();
        attachment.setAttachmentType(AttachmentType.URL);
        attachment.setLink("https://example.org/lecture-notes.pdf");
        attachment.setAttachmentVideoUnit(unit);
        unit.setAttachment(attachment);

        // No stored file to read, so this must neither throw nor look for a local file sharing the link's name.
        String fingerprint = fingerprintService.computeFingerprint(unit);

        assertThat(fingerprint).isEqualTo(fingerprintService.computeFingerprint(unitWithVideo("https://video.example/stream")));
    }

    @Test
    void storedPdfWithAnUppercaseExtensionIsHashed() throws Exception {
        AttachmentVideoUnit unit = unitWithPdf("slides.PDF");
        Path pdfPath = tempDir.resolve("42").resolve("slides.PDF");
        FileUtils.writeStringToFile(pdfPath.toFile(), "original content", StandardCharsets.UTF_8);

        try (MockedStatic<FilePathConverter> filePathConverter = mockStatic(FilePathConverter.class)) {
            filePathConverter.when(FilePathConverter::getAttachmentVideoUnitFileSystemPath).thenReturn(tempDir);

            String original = fingerprintService.computeFingerprint(unit);
            FileUtils.writeStringToFile(pdfPath.toFile(), "changed content", StandardCharsets.UTF_8);

            assertThat(fingerprintService.computeFingerprint(unit)).isNotEqualTo(original);
        }
    }
}
