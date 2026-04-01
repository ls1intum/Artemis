package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class RangeRequestServiceTest {

    private RangeRequestService rangeRequestService;

    @TempDir
    Path tempDir;

    private Path testFile;

    @BeforeEach
    void setUp() throws IOException {
        rangeRequestService = new RangeRequestService();

        // Create test file with known content (1KB)
        testFile = tempDir.resolve("test.pdf");
        byte[] testData = new byte[1024];
        for (int i = 0; i < testData.length; i++) {
            testData[i] = (byte) (i % 256);
        }
        Files.write(testFile, testData);
    }

    @Test
    void testFullFileWithoutRangeHeader() throws IOException {
        ResponseEntity<byte[]> response = rangeRequestService.handleRangeRequest(testFile, null, MediaType.APPLICATION_PDF);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        assertThat(response.getBody()).hasSize(1024);
    }

    @Test
    void testFullFileWithEmptyRangeHeader() throws IOException {
        ResponseEntity<byte[]> response = rangeRequestService.handleRangeRequest(testFile, "", MediaType.APPLICATION_PDF);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        assertThat(response.getBody()).hasSize(1024);
    }

    @Test
    void testRangeRequestFirstBytes() throws IOException {
        ResponseEntity<byte[]> response = rangeRequestService.handleRangeRequest(testFile, "bytes=0-99", MediaType.APPLICATION_PDF);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 0-99/1024");
        assertThat(response.getBody()).hasSize(100);

        // Verify content
        for (int i = 0; i < 100; i++) {
            assertThat(response.getBody()[i]).isEqualTo((byte) (i % 256));
        }
    }

    @Test
    void testRangeRequestMiddleBytes() throws IOException {
        ResponseEntity<byte[]> response = rangeRequestService.handleRangeRequest(testFile, "bytes=512-611", MediaType.APPLICATION_PDF);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getBody()).hasSize(100);

        // Verify content starts at offset 512
        for (int i = 0; i < 100; i++) {
            assertThat(response.getBody()[i]).isEqualTo((byte) ((512 + i) % 256));
        }
    }

    @Test
    void testRangeRequestSuffix() throws IOException {
        // Request last 100 bytes
        ResponseEntity<byte[]> response = rangeRequestService.handleRangeRequest(testFile, "bytes=-100", MediaType.APPLICATION_PDF);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 924-1023/1024");
        assertThat(response.getBody()).hasSize(100);
    }

    @Test
    void testInvalidRange() throws IOException {
        ResponseEntity<byte[]> response = rangeRequestService.handleRangeRequest(testFile, "bytes=2000-3000", MediaType.APPLICATION_PDF);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes */1024");
    }

    @Test
    void testMultipleRangesNotSupported() throws IOException {
        ResponseEntity<byte[]> response = rangeRequestService.handleRangeRequest(testFile, "bytes=0-99,200-299", MediaType.APPLICATION_PDF);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
    }

    @Test
    void testNonExistentFile() throws IOException {
        Path nonExistent = tempDir.resolve("does-not-exist.pdf");
        ResponseEntity<byte[]> response = rangeRequestService.handleRangeRequest(nonExistent, null, MediaType.APPLICATION_PDF);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testIsRangeRequest() {
        assertThat(rangeRequestService.isRangeRequest("bytes=0-1023")).isTrue();
        assertThat(rangeRequestService.isRangeRequest(null)).isFalse();
        assertThat(rangeRequestService.isRangeRequest("")).isFalse();
    }
}
