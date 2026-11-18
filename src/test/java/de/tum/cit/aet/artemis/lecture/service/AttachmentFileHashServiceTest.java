package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
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
        FileUtils.writeStringToFile(file.toFile(), "abc", StandardCharsets.UTF_8);

        AttachmentFileHashService.FileHash fileHash = service.sha256(file);

        assertThat(fileHash.algorithm()).isEqualTo("SHA-256");
        assertThat(fileHash.value()).isEqualTo(SHA_256_ABC);
    }

    @Test
    void wrapsMultipartFileInputStreamOpenIOException() throws IOException {
        MultipartFile file = new OpeningFailingMultipartFile();

        assertThatThrownBy(() -> service.sha256(file)).isInstanceOf(AttachmentFileHashException.class).hasMessageContaining("Could not hash uploaded attachment file")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void wrapsMultipartFileInputStreamReadIOException() throws IOException {
        IOException readException = new IOException("Cannot read stream");
        MultipartFile file = new ReadFailingMultipartFile(readException);

        assertThatThrownBy(() -> service.sha256(file)).isInstanceOf(AttachmentFileHashException.class).hasMessageContaining("Could not hash attachment file stream")
                .hasCause(readException);
    }

    private static final class OpeningFailingMultipartFile extends MockMultipartFile {

        private OpeningFailingMultipartFile() {
            super("file", new byte[0]);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            throw new IOException("Cannot read file");
        }
    }

    private static final class ReadFailingMultipartFile extends MockMultipartFile {

        private final IOException readException;

        private ReadFailingMultipartFile(IOException readException) {
            super("file", new byte[0]);
            this.readException = readException;
        }

        @Override
        public InputStream getInputStream() {
            return new InputStream() {

                @Override
                public int read() throws IOException {
                    throw readException;
                }

                @Override
                public int read(byte[] bytes, int offset, int length) throws IOException {
                    throw readException;
                }
            };
        }
    }
}
