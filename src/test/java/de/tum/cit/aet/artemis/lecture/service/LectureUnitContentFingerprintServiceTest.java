package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
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

    private AttachmentVideoUnit unitWithPdf(Path pdfPath) {
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        Attachment attachment = new Attachment();
        attachment.setLink("/api/core/files/attachments/" + pdfPath.getFileName() + ".pdf");
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
        Path pdfPath = tempDir.resolve("slides.pdf");
        Files.writeString(pdfPath, "original content");
        AttachmentVideoUnit unit = unitWithPdf(pdfPath);

        try (MockedStatic<FilePathConverter> filePathConverter = mockStatic(FilePathConverter.class)) {
            filePathConverter.when(() -> FilePathConverter.fileSystemPathForExternalUri(any(), any())).thenReturn(pdfPath);

            String original = fingerprintService.computeFingerprint(unit);
            String unchanged = fingerprintService.computeFingerprint(unit);
            Files.writeString(pdfPath, "changed content");
            String changed = fingerprintService.computeFingerprint(unit);

            assertThat(original).isEqualTo(unchanged).isNotEqualTo(changed);
        }
    }

    @Test
    void unreadableAttachmentFileFailsInsteadOfFingerprintingWithoutIt() {
        AttachmentVideoUnit unit = unitWithPdf(tempDir.resolve("missing.pdf"));

        try (MockedStatic<FilePathConverter> filePathConverter = mockStatic(FilePathConverter.class)) {
            filePathConverter.when(() -> FilePathConverter.fileSystemPathForExternalUri(any(), any())).thenReturn(tempDir.resolve("missing.pdf"));

            assertThatThrownBy(() -> fingerprintService.computeFingerprint(unit)).isInstanceOf(IllegalStateException.class);
        }
    }
}
