package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class AttachmentFileHashServiceTest {

    private static final String SHA_256_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    private final AttachmentFileHashService service = new AttachmentFileHashService();

    @TempDir
    private Path tempDir;

    @Test
    void hashesMultipartFileWithSha256() {
        MultipartFile file = new MockMultipartFile("file", "abc.txt", "text/plain", "abc".getBytes(StandardCharsets.UTF_8));

        AttachmentFileHashService.FileHash fileHash = service.sha256(file);

        assertThat(fileHash.algorithm()).isEqualTo("SHA-256");
        assertThat(fileHash.value()).isEqualTo(SHA_256_ABC);
    }

    @Test
    void hashesPathWithSha256() throws IOException {
        Path file = tempDir.resolve("abc.txt");
        Files.writeString(file, "abc", StandardCharsets.UTF_8);

        AttachmentFileHashService.FileHash fileHash = service.sha256(file);

        assertThat(fileHash.algorithm()).isEqualTo("SHA-256");
        assertThat(fileHash.value()).isEqualTo(SHA_256_ABC);
    }

    @Test
    void wrapsMultipartFileInputStreamIOException() throws IOException {
        MultipartFile file = new ThrowingMultipartFile();

        assertThatThrownBy(() -> service.sha256(file)).isInstanceOf(AttachmentFileHashException.class).hasMessageContaining("Could not hash uploaded attachment file")
                .hasCauseInstanceOf(IOException.class);
    }

    private static final class ThrowingMultipartFile extends MockMultipartFile {

        private ThrowingMultipartFile() {
            super("file", new byte[0]);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            throw new IOException("Cannot read file");
        }
    }
}
