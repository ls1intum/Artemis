package de.tum.cit.aet.artemis.core.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Service for handling HTTP Range requests (RFC 7233) for files.
 * Enables efficient streaming of large files by sending only requested byte ranges.
 */
@Service
public class RangeRequestService {

    /**
     * Handles HTTP Range requests for files.
     *
     * @param filePath    Path to the file
     * @param rangeHeader Range header value (e.g., "bytes=0-1023")
     * @param mediaType   Media type of the file
     * @return ResponseEntity with partial content (206) or full content (200)
     * @throws IOException if file reading fails
     */
    public ResponseEntity<byte[]> handleRangeRequest(Path filePath, String rangeHeader, MediaType mediaType) throws IOException {

        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        long fileSize = Files.size(filePath);

        // No Range header -> send full file with range support indication
        if (rangeHeader == null || rangeHeader.isEmpty()) {
            byte[] fullContent = Files.readAllBytes(filePath);
            return ResponseEntity.ok().header(HttpHeaders.ACCEPT_RANGES, "bytes").header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize)).contentType(mediaType)
                    .body(fullContent);
        }

        // Parse Range header
        List<HttpRange> ranges;
        try {
            ranges = HttpRange.parseRanges(rangeHeader);
        }
        catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize).build();
        }

        // Only support single range requests (multi-part ranges are complex)
        if (ranges.size() != 1) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize).build();
        }

        HttpRange range = ranges.getFirst();
        long start = range.getRangeStart(fileSize);
        long end = range.getRangeEnd(fileSize);
        long rangeLength = end - start + 1;

        // Validate range
        if (start > end || end >= fileSize) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize).build();
        }

        // Read the requested byte range using InputStream for memory efficiency
        byte[] rangeData = readFileRange(filePath, start, rangeLength);

        // Build 206 Partial Content response
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start, end, fileSize)).header(HttpHeaders.CONTENT_LENGTH, String.valueOf(rangeLength))
                .contentType(mediaType).body(rangeData);
    }

    /**
     * Reads a specific byte range from a file using InputStream (memory efficient).
     *
     * @param filePath Path to the file
     * @param start    Starting byte position
     * @param length   Number of bytes to read
     * @return Byte array containing the requested range
     * @throws IOException if file reading fails
     */
    private byte[] readFileRange(Path filePath, long start, long length) throws IOException {
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            // Skip to start position
            long skipped = inputStream.skip(start);
            if (skipped != start) {
                throw new IOException("Could not skip to start position: " + start);
            }

            // Read the requested length
            byte[] buffer = new byte[(int) length];
            int totalRead = 0;
            int bytesRead;

            while (totalRead < length && (bytesRead = inputStream.read(buffer, totalRead, (int) (length - totalRead))) != -1) {
                totalRead += bytesRead;
            }

            if (totalRead != length) {
                throw new IOException("Could not read complete range");
            }

            return buffer;
        }
    }

    /**
     * Checks if a request is a Range request.
     *
     * @param rangeHeader Range header value
     * @return true if this is a range request
     */
    public boolean isRangeRequest(String rangeHeader) {
        return rangeHeader != null && !rangeHeader.isEmpty();
    }
}
