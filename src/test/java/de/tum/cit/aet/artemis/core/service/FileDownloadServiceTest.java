package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.service.file.FileDownloadService;

@ExtendWith(MockitoExtension.class)
class FileDownloadServiceTest {

    @Mock
    private FileService fileService;

    @TempDir
    Path tempDir;

    private FileDownloadService fileDownloadService;

    @BeforeEach
    void setUp() {
        fileDownloadService = new FileDownloadService(fileService);
    }

    @Test
    void shouldPrepareFullDownloadForUnknownExtensionWithOctetStream() throws IOException {
        byte[] expectedContent = "dummy unknown content".getBytes();
        Path filePath = tempDir.resolve("dummy.unknownext");
        FileUtils.writeByteArrayToFile(filePath.toFile(), expectedContent);
        when(fileService.getFileForPath(filePath)).thenReturn(expectedContent);

        var payload = fileDownloadService.prepareAttachmentDownload(tempDir, filePath.getFileName().toString(), Optional.empty(), List.of(), 200_000);

        assertThat(payload.status()).isEqualTo(HttpStatus.OK);
        assertThat(payload.content()).isEqualTo(expectedContent);
        assertThat(payload.mediaType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(payload.contentRange()).isEmpty();
        assertThat(payload.headers().getFirst(HttpHeaders.ACCEPT_RANGES)).isNull();
        verify(fileService).getFileForPath(filePath);
    }

    @Test
    void shouldPrepareHtmlDownloadAsAttachment() throws IOException {
        byte[] expectedContent = "<html><body>dummy</body></html>".getBytes();
        Path filePath = tempDir.resolve("dummy.html");
        FileUtils.writeByteArrayToFile(filePath.toFile(), expectedContent);
        when(fileService.getFileForPath(filePath)).thenReturn(expectedContent);

        var payload = fileDownloadService.prepareAttachmentDownload(tempDir, filePath.getFileName().toString(), Optional.empty(), List.of(), 200_000);

        assertThat(payload.status()).isEqualTo(HttpStatus.OK);
        assertThat(payload.content()).isEqualTo(expectedContent);
        assertThat(payload.headers().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment;");
        verify(fileService).getFileForPath(filePath);
    }

    @Test
    void shouldSanitizeReplacementFilenameInHeaders() {
        var headers = fileDownloadService.createFileHeaders("ignored.pdf", Optional.of("test–file.pdf"));

        assertThat(headers.getFirst("Filename")).isEqualTo("test_file.pdf");
    }

    @Test
    void shouldPreparePartialContentForValidPdfRange() throws IOException {
        byte[] content = "0123456789".getBytes();
        Path filePath = tempDir.resolve("dummy.pdf");
        FileUtils.writeByteArrayToFile(filePath.toFile(), content);

        var payload = fileDownloadService.prepareAttachmentDownload(tempDir, filePath.getFileName().toString(), Optional.empty(), HttpRange.parseRanges("bytes=2-5"), 100);

        assertThat(payload.status()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(payload.content()).isEqualTo("2345".getBytes());
        assertThat(payload.contentRange()).contains("bytes 2-5/10");
        assertThat(payload.headers().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        verifyNoInteractions(fileService);
    }

    @Test
    void shouldReturnRangeNotSatisfiableForOutOfBoundsPdfRange() throws IOException {
        byte[] content = "0123456789".getBytes();
        Path filePath = tempDir.resolve("dummy.pdf");
        FileUtils.writeByteArrayToFile(filePath.toFile(), content);

        var payload = fileDownloadService.prepareAttachmentDownload(tempDir, filePath.getFileName().toString(), Optional.empty(), HttpRange.parseRanges("bytes=25-30"), 100);

        assertThat(payload.status()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        assertThat(payload.content()).isEmpty();
        assertThat(payload.contentRange()).contains("bytes */10");
        verifyNoInteractions(fileService);
    }

    @Test
    void shouldReturnRangeNotSatisfiableWhenPdfRangeExceedsConfiguredLimit() throws IOException {
        byte[] content = "0123456789".getBytes();
        Path filePath = tempDir.resolve("dummy.pdf");
        FileUtils.writeByteArrayToFile(filePath.toFile(), content);

        var payload = fileDownloadService.prepareAttachmentDownload(tempDir, filePath.getFileName().toString(), Optional.empty(), HttpRange.parseRanges("bytes=0-9"), 5);

        assertThat(payload.status()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        assertThat(payload.content()).isEmpty();
        assertThat(payload.contentRange()).contains("bytes */10");
        verifyNoInteractions(fileService);
    }

    @Test
    void shouldPrepareFullPdfDownloadWhenNoRangeHeaderIsPresent() throws IOException {
        byte[] expectedContent = "dummy pdf".getBytes();
        Path filePath = tempDir.resolve("dummy.pdf");
        FileUtils.writeByteArrayToFile(filePath.toFile(), expectedContent);
        when(fileService.getFileForPath(filePath)).thenReturn(expectedContent);

        var payload = fileDownloadService.prepareAttachmentDownload(tempDir, filePath.getFileName().toString(), Optional.empty(), List.of(), 200_000);

        assertThat(payload.status()).isEqualTo(HttpStatus.OK);
        assertThat(payload.content()).isEqualTo(expectedContent);
        assertThat(payload.contentRange()).isEmpty();
        assertThat(payload.headers().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        verify(fileService).getFileForPath(filePath);
    }

    @Test
    void shouldThrowEntityNotFoundForMissingPdfFile() {
        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> fileDownloadService.prepareAttachmentDownload(tempDir, "missing.pdf", Optional.empty(), List.of(), 200_000));
    }

    @Test
    void shouldThrowEntityNotFoundWhenNonPdfFileCannotBeLoaded() throws IOException {
        Path filePath = tempDir.resolve("missing.unknownext");
        when(fileService.getFileForPath(filePath)).thenReturn(null);

        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> fileDownloadService.prepareAttachmentDownload(tempDir, filePath.getFileName().toString(), Optional.empty(), List.of(), 200_000));
    }

    @Test
    void shouldThrowInternalServerErrorWhenReadingFullDownloadFails() throws IOException {
        Path filePath = tempDir.resolve("dummy.unknownext");
        when(fileService.getFileForPath(filePath)).thenThrow(new IOException("cannot read"));

        assertThatExceptionOfType(InternalServerErrorException.class)
                .isThrownBy(() -> fileDownloadService.prepareAttachmentDownload(tempDir, filePath.getFileName().toString(), Optional.empty(), List.of(), 200_000));
    }
}
